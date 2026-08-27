package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A room message this SDK version does not model, kept only so an unrecognised type can be skipped
 * instead of failing the whole state fetch.
 *
 * @param type the wire discriminator the platform sent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnknownRoomMessage(String type) implements RoomMessage {
}
