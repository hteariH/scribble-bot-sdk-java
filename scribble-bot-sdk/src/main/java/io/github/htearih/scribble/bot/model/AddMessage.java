package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Posts a message into the room as the bot, optionally as a reply to another message.
 *
 * <p>{@code type} is not a record component, so it is pinned as a property explicitly — Jackson
 * derives record properties from the canonical constructor and would otherwise drop it.
 *
 * @param text    what to post
 * @param localId this bot's own ID for the message, or {@code null} — see {@link #localId()}
 * @param replyTo the message this one replies to, or {@code null} to post a standalone message
 */
@JsonPropertyOrder({"type", "text", "localId", "replyTo"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddMessage(String text, Long localId, OutboundReplyTarget replyTo) implements Action {

    /** The wire discriminator. The unprefixed {@code addMessage} spelling is gone as of upstream 0.4.0. */
    public static final String TYPE = "chat.addMessage";

    /**
     * The largest value a {@link #localId()} may carry: 2^53-1, the platform's cap for JavaScript
     * interoperability. A larger one is rejected rather than silently rounded.
     */
    public static final long MAX_LOCAL_ID = 9007199254740991L;

    /** A plain message with no local ID and no reply target. */
    public AddMessage(String text) {
        this(text, null, null);
    }

    public AddMessage {
        // 0 is the wire's "no local ID", and the platform never deduplicates it. Normalising it to
        // null here keeps `localId()` honest and keeps the zero off the wire entirely.
        localId = localId != null && localId == 0L ? null : localId;
    }

    @Override
    @JsonProperty("type")
    public String type() {
        return TYPE;
    }

    /**
     * This bot's own ID for the message, unique among its messages in this room's chat.
     *
     * <p>The platform hands it back as {@link RepliedMessage#localId()} when somebody replies, which
     * lets the bot recognise replies to itself and look the message up in its own storage without
     * ever fetching room-global IDs.
     *
     * <p>It also serves as an idempotency key: re-sending the same {@code localId} into the same
     * room drops the duplicate instead of posting twice, as long as the original still exists (chat
     * messages expire after about two days).
     *
     * <p>{@code null} means no local ID, and nothing without one is ever deduplicated. A persistent
     * counter is the sound choice; a single-instance, memory-only bot can get away with the current
     * epoch milliseconds.
     */
    @Override
    public Long localId() {
        return localId;
    }

    /** The same message, posted as a reply to {@code messageId}. */
    public AddMessage replyingTo(long messageId) {
        return new AddMessage(text, localId, OutboundReplyTarget.to(messageId));
    }

    /** The same message, carrying {@code localId} so replies to it can be recognised. */
    public AddMessage withLocalId(long localId) {
        return new AddMessage(text, localId, replyTo);
    }

    /** The same message, aimed at {@code target}. */
    public AddMessage withReplyTo(OutboundReplyTarget target) {
        return new AddMessage(text, localId, target);
    }
}
