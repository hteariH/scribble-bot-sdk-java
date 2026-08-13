package io.github.htearih.scribble.bot;

/**
 * The convenient way to write a bot: take a mention, return the line to post. The SDK flattens the
 * answer to plain text, truncates it and wraps it in an {@code addMessage} action; returning
 * {@code null} or blank posts nothing at all.
 *
 * <p>Called on the caller's thread, synchronously — see {@link ScribblePubBot#handleHook} for why
 * that thread is on a deadline.
 */
@FunctionalInterface
public interface MentionHandler {

    String reply(Mention mention);
}
