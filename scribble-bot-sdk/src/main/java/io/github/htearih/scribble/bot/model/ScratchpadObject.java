package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A drawn object in the state.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScratchpadObject(int objectId, int frameId, int eventId) {
}
