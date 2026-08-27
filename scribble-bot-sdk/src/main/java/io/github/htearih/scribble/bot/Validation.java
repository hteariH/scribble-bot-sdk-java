package io.github.htearih.scribble.bot;

import io.github.htearih.scribble.bot.model.Action;
import io.github.htearih.scribble.bot.model.AddMessage;
import io.github.htearih.scribble.bot.model.OutboundReplyTarget;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks outbound payloads against the wire contract before they leave the process.
 *
 * <p>The platform enforces all of this too, but it answers with a flat 400 and a room that got
 * nothing. Failing here instead costs no round trip and names the field.
 *
 * <p>Inbound payloads are deliberately <em>not</em> validated this strictly — see
 * {@link io.github.htearih.scribble.bot.model.Trigger} for why an unrecognised delivery is
 * acknowledged rather than rejected.
 */
public final class Validation {

    private Validation() {
    }

    /** Every problem with {@code actions}, in the order they were found; empty when it is sendable. */
    public static List<ValidationError> actions(List<Action> actions) {
        var errors = new ArrayList<ValidationError>();
        if (actions == null) {
            errors.add(new ValidationError("actions", "is required"));
            return errors;
        }
        for (var i = 0; i < actions.size(); i++) {
            var path = "actions." + i;
            var action = actions.get(i);
            if (action == null) {
                errors.add(new ValidationError(path, "is null"));
                continue;
            }
            if (action instanceof AddMessage message) {
                addMessage(message, path, errors);
            }
        }
        return errors;
    }

    private static void addMessage(AddMessage message, String path, List<ValidationError> errors) {
        if (message.text() == null) {
            errors.add(new ValidationError(path + ".text", "is required"));
        }
        localId(message.localId(), path + ".localId", errors);
        if (message.replyTo() != null) {
            replyTo(message.replyTo(), path + ".replyTo", errors);
        }
    }

    private static void replyTo(OutboundReplyTarget target, String path, List<ValidationError> errors) {
        var hasMessageId = target.messageId() != null;
        var hasLocalId = target.localId() != null;
        if (hasMessageId && hasLocalId) {
            errors.add(new ValidationError(path + ".messageId", "give either messageId or localId, not both"));
        } else if (!hasMessageId && !hasLocalId) {
            errors.add(new ValidationError(path + ".messageId", "one of messageId or localId is required"));
        }
        localId(target.localId(), path + ".localId", errors);

        // The platform truncates an out-of-range quote rather than failing, but it cannot make sense
        // of half a pair: an offset without a length says where to start and never where to stop.
        if ((target.quoteStart() == null) != (target.quoteLength() == null)) {
            var missing = target.quoteStart() == null ? ".quoteStart" : ".quoteLength";
            errors.add(new ValidationError(path + missing, "quoteStart and quoteLength must be given together"));
        }
        if (target.quoteStart() != null && target.quoteStart() < 0) {
            errors.add(new ValidationError(path + ".quoteStart", "must not be negative"));
        }
        if (target.quoteLength() != null && target.quoteLength() <= 0) {
            errors.add(new ValidationError(path + ".quoteLength", "must be positive"));
        }
    }

    private static void localId(Long localId, String path, List<ValidationError> errors) {
        if (localId == null) {
            return;
        }
        if (localId < 0) {
            errors.add(new ValidationError(path, "must not be negative"));
        } else if (localId > AddMessage.MAX_LOCAL_ID) {
            errors.add(new ValidationError(path, "must not exceed " + AddMessage.MAX_LOCAL_ID));
        }
    }

    /** Every problem with a webhook URL; empty when it is registrable. */
    public static List<ValidationError> webhookUrl(String url) {
        var errors = new ArrayList<ValidationError>();
        if (url == null || url.isBlank()) {
            errors.add(new ValidationError("url", "is required"));
            return errors;
        }
        String scheme;
        try {
            scheme = URI.create(url).getScheme();
        } catch (IllegalArgumentException exception) {
            errors.add(new ValidationError("url", "is not a valid URL"));
            return errors;
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            errors.add(new ValidationError("url", "must be an absolute http or https URL"));
        }
        return errors;
    }

    /** Throws {@link ScribblePubValidationError} when {@code errors} is not empty. */
    public static void require(String what, List<ValidationError> errors) {
        if (!errors.isEmpty()) {
            throw new ScribblePubValidationError("invalid " + what + ": " + errors.get(0), errors);
        }
    }
}
