package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The room's last dice roll, which people make with {@code /roll} in the chat.
 *
 * <p>Every layer in {@link AnimationMode#ROLL} mode shows the frame at
 * {@code frames[floor(roll * frames.size())]}. In a state snapshot it comes after
 * {@link ScratchpadSetLayerOrderMessage}.
 *
 * @param type        always {@value #TYPE}
 * @param roll        a number from 0 (inclusive) to 1 (exclusive)
 * @param lastEventId the ID of the event that made the roll; {@code -1} in a state snapshot
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScratchpadRollMessage(String type, double roll, long lastEventId) implements RoomMessage {

    /** The wire discriminator for this message. */
    public static final String TYPE = "sp.roll";

    public ScratchpadRollMessage {
        type = type == null ? TYPE : type;
    }
}
