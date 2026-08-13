package io.github.htearih.scribble.bot.model;

/**
 * Something the bot asks the platform to do in the room it was mentioned in. A discriminated union
 * on {@code type}; {@link AddMessage} is the only member the Bot API accepts today.
 */
public sealed interface Action permits AddMessage {

    /** The wire discriminator. */
    String type();

    /** Post {@code text} into the room as the bot. */
    static AddMessage addMessage(String text) {
        return new AddMessage(text);
    }
}
