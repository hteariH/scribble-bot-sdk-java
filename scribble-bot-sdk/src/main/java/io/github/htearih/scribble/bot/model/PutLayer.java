package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Creates the bot's own layer, or replaces the frame program of the one it already has under the
 * same {@link #localId()}.
 *
 * <p>Putting a layer that already exists replaces only its {@link #frameLocalIds()} (and its
 * {@link #animationMode()}, if given). It keeps its place — {@link #position()} only places a new
 * layer, and layers cannot be reordered by bots or people — and sending the same program again
 * changes nothing, so retries are safe.
 *
 * @param localId        the bot's own ID for this layer, unique among its layers in the room's
 *                       drawing session; from 1 to {@link Action#MAX_LOCAL_ID}
 * @param frameLocalIds  the layer's frames in the order they play, as the bot's own IDs for them —
 *                       see {@link #frameLocalIds()}
 * @param animationMode  how the layer plays, or {@code null}: a new layer is then
 *                       {@link AnimationMode#SEQUENTIAL} and an existing one keeps its mode
 * @param position       where a new layer goes, or {@code null} for on top of all others
 */
@JsonPropertyOrder({"type", "localId", "frameLocalIds", "animationMode", "position"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PutLayer(long localId, List<Long> frameLocalIds, AnimationMode animationMode, LayerPosition position)
        implements Action {

    /** The wire discriminator. */
    public static final String TYPE = "sp.putLayer";

    /** The most entries a {@link #frameLocalIds()} program may hold. */
    public static final int MAX_FRAMES = 256;

    /** A layer playing {@code frameLocalIds} with the defaults: sequential, and on top if new. */
    public PutLayer(long localId, List<Long> frameLocalIds) {
        this(localId, frameLocalIds, null, null);
    }

    public PutLayer {
        // Kept nullable rather than defaulted, so validation can name a missing program; a copy
        // otherwise, because a caller's list must not change the payload after validation.
        frameLocalIds = frameLocalIds == null ? null : Collections.unmodifiableList(new ArrayList<>(frameLocalIds));
    }

    @Override
    @JsonProperty("type")
    public String type() {
        return TYPE;
    }

    /**
     * The layer's frames in the order they play: one entry per tick, as the bot's own IDs for them.
     *
     * <ul>
     *   <li><strong>New IDs create frames.</strong> An ID the layer has not had yet makes a new,
     *       empty frame.
     *   <li><strong>Repeated IDs repeat frames.</strong> {@code [1, 2, 1, 3]} shows frame 1 twice,
     *       like "Repeat frame" in the UI. They are not copies: what is drawn on frame 1 shows on
     *       both of its ticks.
     *   <li><strong>Missing IDs remove frames.</strong> A frame left out of a later put is removed
     *       with its objects, and its ID creates a new, empty frame if used again.
     * </ul>
     *
     * <p>Frame IDs are unique across all of the bot's layers, which is why drawing on one takes only
     * the frame ({@link PutObject#frameLocalId()}). Giving a layer an ID another of the bot's layers
     * holds makes the platform skip the whole action; {@link io.github.htearih.scribble.bot.Validation}
     * catches it within a single request. Must hold from 1 to {@value #MAX_FRAMES} entries — use
     * {@link RemoveLayer} to remove a layer.
     */
    @Override
    public List<Long> frameLocalIds() {
        return frameLocalIds;
    }

    /** The same layer, playing in {@code mode}. */
    public PutLayer withAnimationMode(AnimationMode mode) {
        return new PutLayer(localId, frameLocalIds, mode, position);
    }

    /** The same layer, placed at {@code position} if it is new. */
    public PutLayer at(LayerPosition position) {
        return new PutLayer(localId, frameLocalIds, animationMode, position);
    }
}
