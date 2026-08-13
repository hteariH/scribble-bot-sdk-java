package io.github.htearih.scribble.bot.model;

import java.util.List;

/**
 * The bot's answer to a hook. scribble.pub has no outbound API, so this HTTP response body
 * <em>is</em> the reply — there is no second chance to send one.
 */
public record HookResponse(List<Action> actions) {

    public HookResponse {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /** A response with a single {@code addMessage}, or none at all when the text is blank. */
    public static HookResponse of(String text) {
        return text == null || text.isBlank()
                ? new HookResponse(List.of())
                : new HookResponse(List.of(Action.addMessage(text)));
    }
}
