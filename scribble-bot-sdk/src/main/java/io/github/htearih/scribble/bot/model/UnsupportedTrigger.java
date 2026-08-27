package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A trigger type this SDK version does not model.
 *
 * <p>The platform adds triggers over time, and a bot that rejects the ones it has not been taught
 * looks broken rather than merely old. Everything unrecognised lands here with its base fields
 * intact, is acknowledged with HTTP 200 and an empty action list, and only reaches a handler if one
 * was registered through {@link io.github.htearih.scribble.bot.ScribblePubBot#onUnsupported}.
 *
 * <p>Upgrade the SDK to answer a trigger properly; this type exists so that not upgrading stays
 * harmless.
 *
 * @param type      the wire discriminator the platform sent, whatever it was
 * @param room      the room the event happened in
 * @param timestamp platform timestamp, in seconds since the Unix epoch
 * @param directUrl the base API URL of this room's host instance
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnsupportedTrigger(String type, String room, long timestamp, String directUrl) implements Trigger {
}
