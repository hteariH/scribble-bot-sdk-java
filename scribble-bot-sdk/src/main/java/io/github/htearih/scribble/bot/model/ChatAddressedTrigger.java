package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A chat message addressed to the bot: it opens with the bot's tag ({@code @HelloBot …}), replies to
 * one of the bot's own messages, or both.
 *
 * <p>Replaces the {@code chat.mention} trigger of upstream 0.3.0 and earlier — one trigger now
 * covers tags and replies alike. The addressing rules are the platform's, not this SDK's:
 *
 * <ul>
 *   <li><strong>Only an opening tag addresses a bot.</strong> Naming one mid-sentence is ordinary text.
 *   <li><strong>Tags win over replies.</strong> A reply addresses a bot only when the message opens
 *       with no bot tag, so replying to one bot while opening with another's tag reaches the tagged
 *       one alone.
 *   <li><strong>Bots never trigger bots.</strong> Messages posted by bots emit no hooks, and a bot
 *       never triggers itself.
 * </ul>
 *
 * <p>The platform does not strip the tag from {@link #text()}; the SDK does that for you in
 * {@link io.github.htearih.scribble.bot.Addressed#text()}.
 *
 * @param type             always {@value #TYPE}
 * @param room             the room the message was posted in
 * @param timestamp        platform timestamp, in seconds since the Unix epoch
 * @param directUrl        the base API URL of this room's host instance
 * @param username         the display name of whoever wrote it; they can change it, so do not key
 *                         storage on it
 * @param userId           the stable ID of whoever wrote it — see {@link #userId()}
 * @param messageId        the room-global ID of this message
 * @param text             what they said, the opening tag included
 * @param replyToMessageId the ID of the message this replies to, or {@code null} when it is not a
 *                         reply. Present whatever became of the target — this is the one part of a
 *                         reply that always survives
 * @param replyTo          the message this replies to, or {@code null} — see {@link #replyTo()}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatAddressedTrigger(
        String type,
        String room,
        long timestamp,
        String directUrl,
        String username,
        String userId,
        long messageId,
        String text,
        Long replyToMessageId,
        RepliedMessage replyTo)
        implements Trigger {

    /** The wire discriminator for this trigger. */
    public static final String TYPE = "chat.addressed";

    public ChatAddressedTrigger {
        type = type == null ? TYPE : type;
    }

    /**
     * The ID of the user who triggered the hook. Unlike {@link #username()}, which its owner can
     * change, this one is stable — always key per-user storage on it.
     *
     * <p>The first letter indicates the author type: {@code u} (registered user), {@code g} (guest)
     * or {@code b} (bot). Treat it as one opaque string, compared whole and case-sensitively, and
     * expect new prefix letters and greater lengths in future. A guest's identity is tied to their
     * browser session, so a cleared cookie jar returns them under a new {@code g…} ID.
     */
    @Override
    public String userId() {
        return userId;
    }

    /**
     * The message this one replies to, delivered only while that message is still live.
     *
     * <p>Present whether the reply targets one of this bot's messages or a third party's — a user
     * replying to somebody else while tagging this bot delivers that third party's message here.
     *
     * <p>Absent when the message is not a reply, and equally when its target has been deleted,
     * hidden or expired, leaving {@link #replyToMessageId()} on its own. Its presence therefore
     * tells you the target is still readable, not merely that this is a reply.
     */
    @Override
    public RepliedMessage replyTo() {
        return replyTo;
    }

    /** Whether this message is a reply, whatever became of the message it replies to. */
    public boolean isReply() {
        return replyToMessageId != null;
    }

    /** Whether the SDK has everything it needs to hand this to a handler. */
    @Override
    public boolean isComplete() {
        return Trigger.super.isComplete() && text != null && username != null && userId != null;
    }
}
