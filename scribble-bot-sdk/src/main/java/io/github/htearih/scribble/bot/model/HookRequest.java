package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The body scribble.pub POSTs to a bot's webhook. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HookRequest(Trigger trigger) {

    /**
     * Whether the delivery carries enough to be dispatched.
     *
     * <p>An {@link UnsupportedTrigger} counts as valid: the SDK acknowledges triggers it does not
     * model rather than answering 400, so only a structurally broken payload is rejected.
     */
    public boolean isValid() {
        return trigger != null && trigger.isComplete();
    }
}
