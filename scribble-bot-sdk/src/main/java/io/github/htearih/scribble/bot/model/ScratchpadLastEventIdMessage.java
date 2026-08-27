package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The scratchpad's global event counter, closing a state snapshot.
 *
 * <p>Because some events are skipped — object removal, for one — it may be larger than any event ID
 * received by other means. Keep it as the cursor to resume partial updates from.
 *
 * @param type        always {@value #TYPE}
 * @param lastEventId the counter value
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScratchpadLastEventIdMessage(String type, long lastEventId) implements RoomMessage {

    /** The wire discriminator for this message. */
    public static final String TYPE = "sp.lastEventId";

    public ScratchpadLastEventIdMessage {
        type = type == null ? TYPE : type;
    }
}
