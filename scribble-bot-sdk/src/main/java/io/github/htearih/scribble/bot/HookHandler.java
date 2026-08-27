package io.github.htearih.scribble.bot;

import io.github.htearih.scribble.bot.model.Action;
import io.github.htearih.scribble.bot.model.Trigger;
import java.util.List;

/**
 * The catch-all handler: any trigger the bot has no specific handler for, in; the actions to
 * perform, out. Equivalent to {@code bot.on("hook", …)} in the TypeScript SDK.
 *
 * <p>Takes the {@link Trigger} itself rather than the enclosing request — upstream 0.4.0 made the
 * same change, and the envelope never carried anything else worth reading.
 *
 * <p>Prefer {@link AddressedHandler}, or {@link ChatAddressedHandler} when you need the whole trigger.
 */
@FunctionalInterface
public interface HookHandler {

    List<Action> handle(Trigger trigger);
}
