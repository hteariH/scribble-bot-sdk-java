package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Creates, updates or deletes a scratchpad layer.
 *
 * <p>Upsert the layer by its {@link #layerId()}, and upsert or delete its frames to match
 * {@link #frames()}. <strong>An empty {@code frames} list means the layer was deleted.</strong>
 *
 * <p><em>Layers vs. frames.</em> Layers define the top-level rendering z-index; frames belong to
 * layers and hold the actual drawing objects. Only one frame per layer is rendered at any moment,
 * picked by the layer's {@link #animationMode()} — see
 * {@link io.github.htearih.scribble.bot.state.ScratchpadState#visibleFrameId}. Objects within a frame
 * render in ascending {@code objectId} order. See {@code https://scribble.pub/docs/animations}.
 *
 * @param type              always {@value #TYPE}
 * @param layerId           a session-scoped identifier for the layer. Layers, frames and objects each
 *                          have their own independent ID counters
 * @param frames            the layer's frame program — see {@link #frames()}. Empty means the layer
 *                          was deleted
 * @param animationMode     how the layer plays its frames, or {@code null} for a mode this SDK
 *                          version does not know
 * @param lastEventId       the ID of the last event that created or modified this layer
 * @param localId           the {@link PutLayer#localId()} this bot gave the layer, or {@code null}
 * @param frameIdsByLocalId the global IDs of the layer's frames keyed by this bot's
 *                          {@link PutLayer#frameLocalIds()} for them, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScratchpadLayerMessage(
        String type,
        long layerId,
        List<Long> frames,
        AnimationMode animationMode,
        long lastEventId,
        Long localId,
        Map<Long, Long> frameIdsByLocalId)
        implements RoomMessage {

    /** The wire discriminator for this message. */
    public static final String TYPE = "sp.layer";

    public ScratchpadLayerMessage {
        type = type == null ? TYPE : type;
        frames = frames == null ? List.of() : List.copyOf(frames);
        frameIdsByLocalId = frameIdsByLocalId == null ? null : Map.copyOf(frameIdsByLocalId);
    }

    /** A sequential layer no bot claims as its own. */
    public ScratchpadLayerMessage(String type, long layerId, List<Long> frames, long lastEventId) {
        this(type, layerId, frames, AnimationMode.SEQUENTIAL, lastEventId, null, null);
    }

    /**
     * The layer's frame program: one frame ID per tick, in the order they are played. A frame shown
     * for several ticks ("Repeat frame" in the UI) appears several times. Frame IDs are room-global
     * and each belongs to exactly one layer; a new one should be created in the state.
     */
    @Override
    public List<Long> frames() {
        return frames;
    }

    /**
     * The {@link PutLayer#localId()} this bot gave the layer. Present only on the bot's own layers;
     * other bots' local IDs are never shown.
     */
    @Override
    public Long localId() {
        return localId;
    }

    /**
     * The global IDs of the layer's frames keyed by this bot's own IDs for them, e.g.
     * {@code {7: 103}}. Present only on the bot's own layers.
     */
    @Override
    public Map<Long, Long> frameIdsByLocalId() {
        return frameIdsByLocalId;
    }

    /** Whether this message deletes the layer, i.e. it carries no frames. */
    public boolean isDeletion() {
        return frames.isEmpty();
    }
}
