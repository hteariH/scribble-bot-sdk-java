package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The body scribble.pub POSTs to a bot's webhook. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HookRequest(Trigger trigger) {

    public boolean isValid() {
        return trigger != null && trigger.isComplete();
    }
}
