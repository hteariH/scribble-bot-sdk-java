package io.github.htearih.scribble.bot;

/**
 * A mention, ready to answer: the same event as {@link io.github.htearih.scribble.bot.model.Trigger},
 * with the {@code @handle} already taken out of the text.
 *
 * @param room      the room it was posted in, with the platform's own casing
 * @param username  who wrote it
 * @param text      what they said, without the mention
 * @param rawText   the original text, mention included
 * @param timestamp the platform timestamp, passed through unchanged
 */
public record Mention(String room, String username, String text, String rawText, long timestamp) {

    /** True when the mention carried nothing but the handle. */
    public boolean isBare() {
        return text == null || text.isBlank();
    }
}
