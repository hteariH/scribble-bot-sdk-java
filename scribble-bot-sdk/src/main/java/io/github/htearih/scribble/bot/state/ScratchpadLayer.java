package io.github.htearih.scribble.bot.state;

import java.util.List;

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
    private long lastEventId;

    ScratchpadLayer(long layerId, List<Long> frames, long lastEventId) {
        this.layerId = layerId;
        this.frames = frames;
        this.lastEventId = lastEventId;
    }

    /** A session-scoped identifier for this layer. */
    public long layerId() {
        return layerId;
    }

    /** The IDs of the frames this layer owns. */
    public List<Long> frames() {
        return frames;
    }

    /** The last event that created or modified this layer. */
    public long lastEventId() {
        return lastEventId;
    }

    void update(List<Long> frames, long lastEventId) {
        this.frames = frames;
        this.lastEventId = lastEventId;
    }

    @Override
    public String toString() {
        return "ScratchpadLayer[layerId=" + layerId + ", frames=" + frames + ", lastEventId=" + lastEventId + "]";
    }
}
