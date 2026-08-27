package io.github.htearih.scribble.bot;

import io.github.htearih.scribble.bot.model.Action;
import io.github.htearih.scribble.bot.model.ChatAddressedTrigger;
import java.util.List;

/**
 * Handles a chat message addressed to the bot, with the full trigger in hand: the author's stable
 * {@link ChatAddressedTrigger#userId()}, the {@link ChatAddressedTrigger#messageId()} to reply to,
 * and the {@link ChatAddressedTrigger#replyTo()} message when there is one.
 *
 * <p>Use this when the answer is more than one line of text — replying in thread, quoting a
 * fragment, posting several messages. {@link AddressedHandler} is the shorter road for the common case.
 */
@FunctionalInterface
public interface ChatAddressedHandler {

    List<Action> handle(ChatAddressedTrigger trigger);
}
