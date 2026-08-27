package io.github.htearih.scribble.bot;

/**
 * The convenient way to write a bot: take a message addressed to it, return the line to post. The
 * SDK flattens the answer to plain text, truncates it and wraps it in a {@code chat.addMessage}
 * action; returning {@code null} or blank posts nothing at all.
 *
 * <p>Whether the answer lands in the thread or as a standalone message is a bot-wide setting —
 * {@code ScribblePubBot.Builder.replyInThread} or {@code scribble.reply-in-thread}. Reach for
 * {@link ChatAddressedHandler} when a single line is not enough.
 *
 * <p>Called on the caller's thread, synchronously — see {@link ScribblePubBot#handleHook} for why
 * that thread is on a deadline.
 */
@FunctionalInterface
public interface AddressedHandler {

    String reply(Addressed addressed);
}
