package io.github.htearih.scribble.bot.state;

import io.github.htearih.scribble.bot.model.AnimationMode;
import java.util.List;
import java.util.Map;

/**
 * A layer in a {@link ScratchpadState}: a top-level rendering z-index holding frames.
 *
 * <p>Frames are declared <em>by</em> layers. A frame exists because some layer lists its ID in
 * {@link #frames()}, and it is dropped the moment it is no longer listed.
 *
 * <p>Mutable, like the state that owns it — see {@link ScratchpadState} for why.
 */
public final class ScratchpadLayer {

    private final long layerId;
    private List<Long> frames;
    private AnimationMode animationMode;
    private long lastEventId;
    private Long localId;
    private Map<Long, Long> frameIdsByLocalId;

    ScratchpadLayer(long layerId) {
        this.layerId = layerId;
    }

    /** A session-scoped identifier for this layer. */
    public long layerId() {
        return layerId;
    }

    /**
     * The frame program of this layer: one frame ID per tick, in the order they are played. Repeated
     * frames appear several times.
     */
    public List<Long> frames() {
        return frames;
    }

    /**
     * How the layer plays its {@link #frames()}, or {@code null} for a mode this SDK version does
     * not know, which {@link ScratchpadState#visibleFrameId} plays as sequential.
     */
    public AnimationMode animationMode() {
        return animationMode;
    }

    /** The last event that created or modified this layer. */
    public long lastEventId() {
        return lastEventId;
    }

    /** The bot's own ID for this layer, or {@code null} unless it is one of the bot's own layers. */
    public Long localId() {
        return localId;
    }

    /**
     * The global IDs of the layer's frames keyed by the bot's own IDs for them, or {@code null}
     * unless it is one of the bot's own layers.
     */
    public Map<Long, Long> frameIdsByLocalId() {
        return frameIdsByLocalId;
    }

    void update(
            List<Long> frames,
            AnimationMode animationMode,
            long lastEventId,
            Long localId,
            Map<Long, Long> frameIdsByLocalId) {
        this.frames = frames;
        this.animationMode = animationMode;
        this.lastEventId = lastEventId;
        this.localId = localId;
        this.frameIdsByLocalId = frameIdsByLocalId;
    }

    @Override
    public String toString() {
        return "ScratchpadLayer[layerId=" + layerId + ", frames=" + frames + ", animationMode=" + animationMode
                + ", lastEventId=" + lastEventId + (localId == null ? "" : ", localId=" + localId) + "]";
    }
}
