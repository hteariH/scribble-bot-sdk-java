package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Something the bot asks the platform to do in a room. A flat discriminated union on {@code type}:
 * the discriminator sits next to the payload fields rather than wrapping them.
 *
 * <p>{@link AddMessage} is the only member the Bot API accepts today, under its prefixed name
 * {@code chat.addMessage}. The unprefixed {@code addMessage} spelling that upstream deprecated in
 * 0.3.0 was removed in 0.4.0 and is not accepted; this port never emitted it.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({@JsonSubTypes.Type(value = AddMessage.class, name = AddMessage.TYPE)})
public sealed interface Action permits AddMessage {

    /** The wire discriminator. */
    String type();

    /** Post {@code text} into the room as the bot. */
    static AddMessage addMessage(String text) {
        return new AddMessage(text);
    }
}
