package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.htearih.scribble.bot.text.Runes;

/**
 * The message a chat message replies to, delivered only while that message is still live.
 *
 * <p>A parent that was deleted, hidden or expired is not described here at all: the reply then
 * arrives with {@link ChatAddressedTrigger#replyToMessageId()} alone. So whenever this object is
 * present, everything it knows about the parent is present with it, and the nullable fields below
 * are nullable for their own reasons rather than because the parent went missing.
 *
 * <p><strong>Quote offsets are rune indices</strong> — Unicode code points, as in Go — not Java's
 * UTF-16 {@code char} indices, which differ for anything above U+FFFF (most emoji). Use
 * {@link #quote()} rather than {@link String#substring}, or {@link Runes} directly.
 *
 * @param messageId the room-global ID of the replied-to message
 * @param localId   the {@link AddMessage#localId()} this bot posted the message under, or
 *                  {@code null} when the message is not this bot's own. The key to look it up in
 *                  your own storage
 * @param username  the display name of the parent's author
 * @param userId    the stable ID of the parent's author
 * @param text      the full text of the replied-to message
 * @param quoteStart the rune offset in {@code text} where the quoted fragment starts, or
 *                   {@code null} when the reply quotes nothing
 * @param quoteText  the quoted fragment as it read when the reply was posted, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RepliedMessage(
        long messageId,
        Long localId,
        String username,
        String userId,
        String text,
        Integer quoteStart,
        String quoteText) {

    /** Whether the reply quoted a fragment rather than referring to the whole message. */
    public boolean hasQuote() {
        return quoteText != null;
    }

    /** Whether this bot posted the message being replied to, i.e. it carries a {@link #localId()}. */
    public boolean isOwn() {
        return localId != null;
    }

    /**
     * The quoted fragment.
     *
     * <p>Prefer {@link #quoteText()}, which is what the platform stored with the reply: because it
     * was captured when the reply was posted, it stays stable even if the parent has since been
     * edited — which also means it may no longer appear at {@link #quoteStart()}, or in
     * {@link #text()} at all. This method falls back to slicing {@link #text()} at the recorded rune
     * offset only when the platform sent an offset without the text.
     *
     * @return the quote, or {@code null} when the reply quoted nothing
     */
    public String quote() {
        if (quoteText != null) {
            return quoteText;
        }
        if (quoteStart == null || text == null) {
            return null;
        }
        return Runes.slice(text, quoteStart);
    }
}
