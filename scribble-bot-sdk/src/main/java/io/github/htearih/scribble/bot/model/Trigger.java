package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The event scribble.pub delivered. Today the platform only ever sends {@link #CHAT_MENTION} —
 * somebody tagged the bot in a room's chat.
 *
 * <p>Read leniently ({@code ignoreUnknown}) so the platform can add fields without every delivery
 * starting to fail.
 *
 * @param trigger   the event name, e.g. {@code chat.mention}
 * @param room      the room the message was posted in
 * @param timestamp platform timestamp, passed through unchanged
 * @param text      the message text, mention included
 * @param username  who wrote it
 * @param directUrl the base API URL of this room's host instance (e.g. {@code https://eu.scribble.pub}),
 *                  used to reach the room without an intermediate redirect
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Trigger(String trigger, String room, long timestamp, String text, String username, String directUrl) {

    /** The only trigger the platform sends today. */
    public static final String CHAT_MENTION = "chat.mention";

    public boolean isChatMention() {
        return CHAT_MENTION.equals(trigger);
    }

    /** Whether this is a mention with everything the SDK needs to answer it. */
    public boolean isComplete() {
        return isChatMention() && room != null && text != null && username != null && directUrl != null;
    }
}
