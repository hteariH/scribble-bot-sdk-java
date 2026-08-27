package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The body of {@code GET /api/v0/room/{room}/scratchpad/state}: the messages that (re)build the
 * scratchpad, in the order they must be applied, closed by a {@link ScratchpadLastEventIdMessage}.
 *
 * <p>Feed it to {@link io.github.htearih.scribble.bot.state.ScratchpadState#fromMessages} rather
 * than interpreting the list by hand.
 *
 * @param messages the state-building messages, in application order
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScratchpadStateResponse(List<RoomMessage> messages) {

    public ScratchpadStateResponse {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
