package io.github.htearih.scribble.bot.state;

import io.github.htearih.scribble.bot.model.ScratchpadObjectMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A frame in a {@link ScratchpadState}: the child of a layer that holds the drawing objects.
 *
 * <p>Only one frame per layer renders at any moment, and the platform currently sends exactly one —
 * the static preview.
 *
 * <p>Mutable, like the state that owns it — see {@link ScratchpadState} for why.
 */
public final class ScratchpadFrame {

    private final long frameId;
    private final long layerId;
    private final List<ScratchpadObjectMessage> objects = new ArrayList<>();

    ScratchpadFrame(long frameId, long layerId) {
        this.frameId = frameId;
        this.layerId = layerId;
    }

    /** A room-global identifier for this frame; it belongs to exactly one layer. */
    public long frameId() {
        return frameId;
    }

    /** The layer that owns this frame. */
    public long layerId() {
        return layerId;
    }

    /**
     * The objects on this frame, in ascending {@code objectId} order — which is both the order they
     * were drawn in and the order they must be rendered in, so this list can be drawn front to back
     * as it stands.
     */
    public List<ScratchpadObjectMessage> objects() {
        return Collections.unmodifiableList(objects);
    }

    List<ScratchpadObjectMessage> mutableObjects() {
        return objects;
    }

    @Override
    public String toString() {
        return "ScratchpadFrame[frameId=" + frameId + ", layerId=" + layerId + ", objects=" + objects.size() + "]";
    }
}
