package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;

/**
 * A message in a room's state stream.
 *
 * Can be one of several message types (session meta, layer, object) that represent
 * different aspects of the room's drawing state.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = RoomMessage.class)
@JsonSubTypes({})
public record RoomMessage() {
}
