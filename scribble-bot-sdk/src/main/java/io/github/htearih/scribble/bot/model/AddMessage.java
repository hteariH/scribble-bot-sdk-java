package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Posts a message into the room as the bot.
 *
 * <p>{@code type} is not a record component, so it is pinned as a property explicitly — Jackson
 * derives record properties from the canonical constructor and would otherwise drop it.
 */
@JsonPropertyOrder({"type", "text"})
public record AddMessage(String text) implements Action {

    public static final String TYPE = "addMessage";

    @Override
    @JsonProperty("type")
    public String type() {
        return TYPE;
    }
}
