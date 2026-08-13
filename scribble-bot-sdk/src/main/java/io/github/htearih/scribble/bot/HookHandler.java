package io.github.htearih.scribble.bot;

import io.github.htearih.scribble.bot.model.Action;
import io.github.htearih.scribble.bot.model.HookRequest;
import java.util.List;

/**
 * The low-level handler: the validated payload in, the actions to perform out. Equivalent to
 * {@code bot.on("hook", …)} in the TypeScript SDK.
 *
 * <p>Prefer {@link MentionHandler} unless you need to return something other than one message.
 */
@FunctionalInterface
public interface HookHandler {

    List<Action> handle(HookRequest request);
}
