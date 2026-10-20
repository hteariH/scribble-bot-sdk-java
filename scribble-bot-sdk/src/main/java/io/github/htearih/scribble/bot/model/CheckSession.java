package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Cancels the request's whole drawing — every {@code sp.*} action in it — unless the room is still on
 * the drawing session the bot read, so a canvas clear in the meantime does not land the drawing in
 * somebody else's picture. The cancellation is silent, and chat actions are not affected.
 *
 * <p>It must come before every other {@code sp.*} action of the request: drawing is applied all at
 * once, so a check anywhere later could only stop the actions after it. {@link
 * io.github.htearih.scribble.bot.Validation} rejects one that does not.
 *
 * @param session the {@link ScratchpadSessionMetaMessage#currentSession()} the bot read, e.g.
 *                {@code state.sessionMeta().currentSession()}
 */
@JsonPropertyOrder({"type", "session"})
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckSession(long session) implements Action {

    /** The wire discriminator. */
    public static final String TYPE = "sp.checkSession";

    @Override
    @JsonProperty("type")
    public String type() {
        return TYPE;
    }
}
