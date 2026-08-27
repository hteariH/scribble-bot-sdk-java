package io.github.htearih.scribble.bot;

import io.github.htearih.scribble.bot.model.AddMessage;
import io.github.htearih.scribble.bot.model.ChatAddressedTrigger;
import io.github.htearih.scribble.bot.model.OutboundReplyTarget;
import io.github.htearih.scribble.bot.model.RepliedMessage;

/**
 * A message addressed to the bot, ready to answer: the same event as {@link ChatAddressedTrigger}
 * with the {@code @handle} already taken out of the text.
 *
 * <p>Replaces the {@code Mention} of earlier versions, because a bot is now addressed by a reply as
 * well as by a tag — see {@link ChatAddressedTrigger} for the platform's rules on which is which.
 *
 * @param room             the room it was posted in, with the platform's own casing
 * @param username         the author's display name, which they can change
 * @param userId           the author's stable ID — key per-user storage on this, never on the name
 * @param messageId        the room-global ID of this message, and the thing to reply to
 * @param text             what they said, without the opening tag
 * @param rawText          the original text, tag included
 * @param timestamp        the platform timestamp, passed through unchanged
 * @param replyToMessageId the message this replies to, or {@code null} — present whatever became of
 *                         the target
 * @param replyTo          the replied-to message itself, or {@code null} when this is not a reply or
 *                         the target is no longer readable
 */
public record Addressed(
        String room,
        String username,
        String userId,
        long messageId,
        String text,
        String rawText,
        long timestamp,
        Long replyToMessageId,
        RepliedMessage replyTo) {

    /** True when nothing was said beyond the handle itself. */
    public boolean isBare() {
        return text == null || text.isBlank();
    }

    /** Whether this message is a reply, whatever became of the message it replies to. */
    public boolean isReply() {
        return replyToMessageId != null;
    }

    /**
     * Whether this is a reply to one of <em>this bot's</em> own messages — which is to say the
     * replied-to message came back carrying the {@link AddMessage#localId()} the bot posted it under.
     *
     * <p>False for a reply to somebody else, and equally for a reply to a message this bot posted
     * without a local ID, since nothing then identifies it as the bot's.
     */
    public boolean isReplyToSelf() {
        return replyTo != null && replyTo.isOwn();
    }

    /** The fragment quoted from the replied-to message, or {@code null} when nothing was quoted. */
    public String quote() {
        return replyTo == null ? null : replyTo.quote();
    }

    /** Posts {@code text} as a plain message in the room, not attached to this one. */
    public AddMessage say(String text) {
        return new AddMessage(text);
    }

    /** Posts {@code text} as a reply to this message, so it lands in the same thread. */
    public AddMessage replyWith(String text) {
        return new AddMessage(text, null, OutboundReplyTarget.to(messageId));
    }

    /**
     * Posts {@code text} as a reply to this message, quoting the first occurrence of {@code quote} in
     * what was said. Falls back to a plain reply when the fragment cannot be found, so a missing
     * quote never costs the answer.
     */
    public AddMessage replyQuoting(String text, String quote) {
        return new AddMessage(text, null, OutboundReplyTarget.quoting(messageId, rawText, quote));
    }

    /** Builds the ergonomic view of a trigger, given the text with the bot's tag already stripped. */
    static Addressed of(ChatAddressedTrigger trigger, String strippedText) {
        return new Addressed(
                trigger.room(),
                trigger.username(),
                trigger.userId(),
                trigger.messageId(),
                strippedText,
                trigger.text(),
                trigger.timestamp(),
                trigger.replyToMessageId(),
                trigger.replyTo());
    }
}
