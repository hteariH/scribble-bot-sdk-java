package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.htearih.scribble.bot.text.Runes;

/**
 * The message an outbound {@link AddMessage} replies to, and which part of it to quote.
 *
 * <p>Give exactly one of {@link #messageId()} or {@link #localId()} — the platform rejects both
 * together, and so does this SDK before the request goes out.
 *
 * <p>Quote offsets are <strong>rune</strong> (Unicode code point) counts, not Java {@code char}
 * counts. Build them with {@link #quoting(long, String, String)} or {@link Runes#quoteRange} rather
 * than {@link String#indexOf}, which drifts one position right per astral character (most emoji).
 *
 * <p>The platform slices the quoted fragment out of the target message itself, so a bot can never
 * attribute text to somebody who did not write it. Out-of-range offsets are truncated rather than
 * rejected: a quote running past the end is cut short, and a {@code quoteStart} at or beyond the end
 * drops the quote while the message still posts as a plain reply.
 *
 * @param messageId   the room-global ID of the message to reply to, or {@code null} when replying by
 *                    {@code localId}
 * @param localId     the {@link AddMessage#localId()} of one of this bot's own earlier messages, or
 *                    {@code null} when replying by {@code messageId}
 * @param quoteStart  the rune offset in the target where the quoted fragment starts, or {@code null}
 * @param quoteLength the length of the quoted fragment in runes, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutboundReplyTarget(Long messageId, Long localId, Integer quoteStart, Integer quoteLength) {

    /** Replies to a room-global message ID, quoting nothing. */
    public static OutboundReplyTarget to(long messageId) {
        return new OutboundReplyTarget(messageId, null, null, null);
    }

    /** Replies to one of this bot's own messages by its {@link AddMessage#localId()}, quoting nothing. */
    public static OutboundReplyTarget toLocal(long localId) {
        return new OutboundReplyTarget(null, localId, null, null);
    }

    /**
     * Replies to {@code messageId}, quoting the first occurrence of {@code quote} in
     * {@code sourceText} — the text of the message being replied to, as delivered in
     * {@link RepliedMessage#text()} or {@link ChatAddressedTrigger#text()}.
     *
     * <p>Falls back to a plain reply when {@code quote} is empty or does not occur in the source, so
     * a quote that cannot be located never costs the reply itself.
     */
    public static OutboundReplyTarget quoting(long messageId, String sourceText, String quote) {
        var range = Runes.quoteRange(sourceText, quote);
        return range == null
                ? to(messageId)
                : new OutboundReplyTarget(messageId, null, range.quoteStart(), range.quoteLength());
    }

    /** Whether this target quotes a fragment rather than referring to the whole message. */
    public boolean hasQuote() {
        return quoteStart != null || quoteLength != null;
    }
}
