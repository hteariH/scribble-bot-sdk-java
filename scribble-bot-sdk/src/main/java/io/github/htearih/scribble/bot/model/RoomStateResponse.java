package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Response from getRoomStateMessages() containing the state of a room. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomStateResponse(List<RoomMessage> messages) {
}
