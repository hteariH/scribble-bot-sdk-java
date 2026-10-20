package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Removes the bot's own object. Removing one that is not there does nothing, and the rest of the
 * request still applies.
 *
 * @param localId the {@link PutObject#localId()} of the object
 */
@JsonPropertyOrder({"type", "localId"})
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoveObject(long localId) implements Action {

    /** The wire discriminator. */
    public static final String TYPE = "sp.removeObject";

    @Override
    @JsonProperty("type")
    public String type() {
        return TYPE;
    }
}
