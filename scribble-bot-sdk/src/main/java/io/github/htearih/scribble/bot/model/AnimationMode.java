package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a layer plays its frames. See {@code https://scribble.pub/docs/animations#animation-modes}.
 *
 * <p>Upstream promises more modes later. A mode this SDK version does not know reads as
 * {@code null} rather than failing the whole state fetch, and
 * {@link io.github.htearih.scribble.bot.state.ScratchpadState#visibleFrameId} plays it as
 * {@link #SEQUENTIAL}, the same fallback upstream uses.
 */
public enum AnimationMode {

    /** In order, in a loop, on a clock shared by every viewer. The default for a new layer. */
    SEQUENTIAL("sequential"),

    /** In a random order, different for every viewer. */
    RANDOM("random"),

    /**
     * The frame selected by the room's last dice roll ({@link ScratchpadRollMessage}), the same for
     * everyone: {@code frames[floor(roll * frames.size())]}.
     */
    ROLL("roll");

    private final String wire;

    AnimationMode(String wire) {
        this.wire = wire;
    }

    /** The wire spelling, e.g. {@code "roll"}. */
    @JsonValue
    public String wire() {
        return wire;
    }

    /** The mode spelled {@code wire}, or {@code null} for one this SDK version does not know. */
    @JsonCreator
    public static AnimationMode fromWire(String wire) {
        for (var mode : values()) {
            if (mode.wire.equals(wire)) {
                return mode;
            }
        }
        return null;
    }
}
