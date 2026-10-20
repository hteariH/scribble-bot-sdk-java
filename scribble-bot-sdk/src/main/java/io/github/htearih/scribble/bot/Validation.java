package io.github.htearih.scribble.bot;

import io.github.htearih.scribble.bot.model.Action;
import io.github.htearih.scribble.bot.model.AddMessage;
import io.github.htearih.scribble.bot.model.CheckSession;
import io.github.htearih.scribble.bot.model.LayerPosition;
import io.github.htearih.scribble.bot.model.OutboundReplyTarget;
import io.github.htearih.scribble.bot.model.PutLayer;
import io.github.htearih.scribble.bot.model.PutObject;
import io.github.htearih.scribble.bot.model.RemoveLayer;
import io.github.htearih.scribble.bot.model.RemoveObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Every problem with {@code actions}, in the order they were found; empty when it is sendable.
     *
     * <p>Everything but the size in bytes, which needs the JSON — see {@link #payloadSize(int)}.
     */
    public static List<ValidationError> actions(List<Action> actions) {
        var errors = new ArrayList<ValidationError>();
        if (actions == null) {
            errors.add(new ValidationError("actions", "is required"));
            return errors;
        }
        if (actions.size() > Action.MAX_ACTIONS) {
            errors.add(new ValidationError("actions", "must not hold more than " + Action.MAX_ACTIONS + " actions"));
        }
        for (var i = 0; i < actions.size(); i++) {
            var path = "actions." + i;
            var action = actions.get(i);
            if (action == null) {
                errors.add(new ValidationError(path, "is null"));
                continue;
            }
            // A plain instanceof chain rather than a pattern switch: the SDK compiles against Java 17.
            if (action instanceof AddMessage message) {
                addMessage(message, path, errors);
            } else if (action instanceof CheckSession check) {
                if (check.session() <= 0) {
                    errors.add(new ValidationError(path + ".session", "must be positive"));
                }
            } else if (action instanceof PutLayer layer) {
                putLayer(layer, path, errors);
            } else if (action instanceof RemoveLayer layer) {
                drawingLocalId(layer.localId(), path + ".localId", errors);
            } else if (action instanceof PutObject object) {
                putObject(object, path, errors);
            } else if (action instanceof RemoveObject object) {
                drawingLocalId(object.localId(), path + ".localId", errors);
            }
        }
        checkFrameOwners(actions, errors);
        checkSessionFirst(actions, errors);
        return errors;
    }

    /** The problem with a request or hook response of {@code bytes} bytes of JSON, if it is too big. */
    public static List<ValidationError> payloadSize(int bytes) {
        return bytes > Action.MAX_PAYLOAD_BYTES
                ? List.of(new ValidationError(
                        "actions", "take " + bytes + " bytes of JSON, more than " + Action.MAX_PAYLOAD_BYTES))
                : List.of();
    }

    private static void putLayer(PutLayer layer, String path, List<ValidationError> errors) {
        drawingLocalId(layer.localId(), path + ".localId", errors);
        var frames = layer.frameLocalIds();
        if (frames == null || frames.isEmpty()) {
            errors.add(new ValidationError(path + ".frameLocalIds", "must not be empty; use sp.removeLayer to remove a layer"));
        } else {
            if (frames.size() > PutLayer.MAX_FRAMES) {
                errors.add(new ValidationError(path + ".frameLocalIds", "must not hold more than " + PutLayer.MAX_FRAMES + " frames"));
            }
            for (var j = 0; j < frames.size(); j++) {
                var frame = frames.get(j);
                if (frame == null) {
                    errors.add(new ValidationError(path + ".frameLocalIds." + j, "is null"));
                } else {
                    drawingLocalId(frame, path + ".frameLocalIds." + j, errors);
                }
            }
        }
        if (layer.position() != null) {
            position(layer.position(), path + ".position", errors);
        }
    }

    private static void position(LayerPosition position, String path, List<ValidationError> errors) {
        if (position.at() == null) {
            errors.add(new ValidationError(path + ".at", "is required"));
            return;
        }
        var layers = (position.layerId() != null ? 1 : 0) + (position.layerLocalId() != null ? 1 : 0);
        if (!position.at().isRelative()) {
            if (layers > 0) {
                errors.add(new ValidationError(path + ".at", "position at " + position.at().wire() + " takes no layer"));
            }
        } else if (layers != 1) {
            errors.add(new ValidationError(
                    path + ".layerId",
                    "position " + position.at().wire() + " takes exactly one of layerId or layerLocalId"));
        }
        if (position.layerId() != null && position.layerId() < 0) {
            errors.add(new ValidationError(path + ".layerId", "must not be negative"));
        }
        if (position.layerLocalId() != null) {
            drawingLocalId(position.layerLocalId(), path + ".layerLocalId", errors);
        }
    }

    private static void putObject(PutObject object, String path, List<ValidationError> errors) {
        drawingLocalId(object.localId(), path + ".localId", errors);
        if ((object.frameId() == null) == (object.frameLocalId() == null)) {
            errors.add(new ValidationError(path + ".frameId", "give exactly one of frameId or frameLocalId"));
        }
        if (object.frameId() != null && object.frameId() < 0) {
            errors.add(new ValidationError(path + ".frameId", "must not be negative"));
        }
        if (object.frameLocalId() != null) {
            drawingLocalId(object.frameLocalId(), path + ".frameLocalId", errors);
        }
        // Every int is a valid packed colour, so rgba needs no check: it is sent unsigned.
        if (!(object.lineWidth() >= 0 && object.lineWidth() <= PutObject.MAX_LINE_WIDTH)) {
            errors.add(new ValidationError(path + ".lineWidth", "must be between 0 and " + (int) PutObject.MAX_LINE_WIDTH));
        }
        var points = object.points();
        if (points == null) {
            errors.add(new ValidationError(path + ".points", "is required"));
            return;
        }
        if (points.size() < 2 || points.size() > 2 * PutObject.MAX_POINTS) {
            errors.add(new ValidationError(path + ".points", "must hold from 1 to " + PutObject.MAX_POINTS + " [x, y] pairs"));
        } else if (points.size() % 2 != 0) {
            errors.add(new ValidationError(path + ".points", "points must hold [x, y] pairs"));
        }
        for (var j = 0; j < points.size(); j++) {
            var point = points.get(j);
            if (point == null || !Double.isFinite(point)) {
                errors.add(new ValidationError(path + ".points." + j, "must be a finite number"));
            }
        }
    }

    /**
     * A frame local ID belongs to a single layer of the bot. Within a request, giving it to another
     * layer is caught here; against the layers of earlier requests only the platform can tell, and it
     * skips such an action.
     */
    private static void checkFrameOwners(List<Action> actions, List<ValidationError> errors) {
        var owners = new HashMap<Long, Long>();
        for (var i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            if (action instanceof RemoveLayer remove) {
                free(owners, remove.localId());
            }
            if (!(action instanceof PutLayer layer) || layer.frameLocalIds() == null) {
                continue;
            }
            Long clash = null;
            for (var frame : layer.frameLocalIds()) {
                var owner = frame == null ? null : owners.get(frame);
                if (owner != null && owner != layer.localId()) {
                    clash = frame;
                    break;
                }
            }
            if (clash != null) {
                errors.add(new ValidationError(
                        "actions." + i + ".frameLocalIds",
                        "frame " + clash + " belongs to layer " + owners.get(clash)
                                + ": a frame local ID can't be in two layers"));
                continue;
            }
            free(owners, layer.localId());
            for (var frame : layer.frameLocalIds()) {
                if (frame != null) {
                    owners.put(frame, layer.localId());
                }
            }
        }
    }

    private static void free(Map<Long, Long> owners, long layerLocalId) {
        owners.values().removeIf(owner -> owner == layerLocalId);
    }

    /** Drawing is applied all at once, so a check anywhere but first would only let the actions before it through. */
    private static void checkSessionFirst(List<Action> actions, List<ValidationError> errors) {
        var drawing = false;
        for (var i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            if (action == null || !action.isDrawing()) {
                continue;
            }
            if (drawing && action instanceof CheckSession) {
                errors.add(new ValidationError(
                        "actions." + i + ".type", "sp.checkSession must come before the other sp.* actions"));
            }
            drawing = true;
        }
    }

    /** Unlike in chat, a drawing is identified by its local ID, so it is required and can't be 0. */
    private static void drawingLocalId(long localId, String path, List<ValidationError> errors) {
        if (localId < 1) {
            errors.add(new ValidationError(path, "must be at least 1"));
        } else if (localId > Action.MAX_LOCAL_ID) {
            errors.add(new ValidationError(path, "must not exceed " + Action.MAX_LOCAL_ID));
        }
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
