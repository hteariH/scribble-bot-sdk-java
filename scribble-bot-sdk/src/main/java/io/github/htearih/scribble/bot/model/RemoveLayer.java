package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Removes the bot's own layer, together with its frames and everything drawn on them. Removing a
 * layer that is not there does nothing, and the rest of the request still applies.
 *
 * @param localId the {@link PutLayer#localId()} of the layer
 */
@JsonPropertyOrder({"type", "localId"})
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoveLayer(long localId) implements Action {

    /** The wire discriminator. */
    public static final String TYPE = "sp.removeLayer";

    @Override
    @JsonProperty("type")
    public String type() {
        return TYPE;
    }
}
