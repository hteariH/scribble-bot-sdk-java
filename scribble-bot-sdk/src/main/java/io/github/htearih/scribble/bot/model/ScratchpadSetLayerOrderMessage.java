package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Sets the bottom-to-top stacking order of a room's layers. The bottommost layer is drawn on top of
 * a white background.
 *
 * @param type        always {@value #TYPE}
 * @param order       layer IDs from bottom to top
 * @param lastEventId the ID of the event that set this order. It is {@code -1} in a state snapshot,
 *                    where the order is defined once, after all layers have arrived
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScratchpadSetLayerOrderMessage(String type, List<Long> order, long lastEventId) implements RoomMessage {

    /** The wire discriminator for this message. */
    public static final String TYPE = "sp.layerOrder";

    public ScratchpadSetLayerOrderMessage {
        type = type == null ? TYPE : type;
        order = order == null ? List.of() : List.copyOf(order);
    }
}
