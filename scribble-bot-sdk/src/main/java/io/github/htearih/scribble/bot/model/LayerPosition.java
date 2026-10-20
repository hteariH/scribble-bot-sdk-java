package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Where a new {@link PutLayer} goes among the other layers. It only places a new layer: an existing
 * one keeps its place, so an update or a retry never moves it.
 *
 * <p>{@link At#ABOVE} and {@link At#BELOW} take exactly one of {@link #layerId()} or
 * {@link #layerLocalId()}; {@link At#TOP} and {@link At#BOTTOM} take neither. If the layer named is
 * not there, the new one goes on top.
 *
 * @param at           where, relative to all layers or to the one named
 * @param layerId      the global ID of any layer, e.g. {@link DefaultLayers#FOREGROUND_LAYER_ID}, or
 *                     {@code null}
 * @param layerLocalId the {@link PutLayer#localId()} of one of the bot's own layers, or {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record LayerPosition(At at, Long layerId, Long layerLocalId) {

    /** Above all other layers. */
    public static LayerPosition top() {
        return new LayerPosition(At.TOP, null, null);
    }

    /** Below all other layers, the background one included. */
    public static LayerPosition bottom() {
        return new LayerPosition(At.BOTTOM, null, null);
    }

    /** Right above the layer with the global ID {@code layerId}. */
    public static LayerPosition above(long layerId) {
        return new LayerPosition(At.ABOVE, layerId, null);
    }

    /** Right below the layer with the global ID {@code layerId}. */
    public static LayerPosition below(long layerId) {
        return new LayerPosition(At.BELOW, layerId, null);
    }

    /** Right above the bot's own layer {@code layerLocalId}. */
    public static LayerPosition aboveOwn(long layerLocalId) {
        return new LayerPosition(At.ABOVE, null, layerLocalId);
    }

    /** Right below the bot's own layer {@code layerLocalId}. */
    public static LayerPosition belowOwn(long layerLocalId) {
        return new LayerPosition(At.BELOW, null, layerLocalId);
    }

    /** The {@code at} field of a {@link LayerPosition}. */
    public enum At {
        TOP("top"),
        BOTTOM("bottom"),
        ABOVE("above"),
        BELOW("below");

        private final String wire;

        At(String wire) {
            this.wire = wire;
        }

        /** The wire spelling, e.g. {@code "above"}. */
        @JsonValue
        public String wire() {
            return wire;
        }

        /** The value spelled {@code wire}, or {@code null} for an unknown one. */
        @JsonCreator
        public static At fromWire(String wire) {
            for (var at : values()) {
                if (at.wire.equals(wire)) {
                    return at;
                }
            }
            return null;
        }

        /** Whether this placement is relative to another layer, and so needs one named. */
        public boolean isRelative() {
            return this == ABOVE || this == BELOW;
        }
    }
}
