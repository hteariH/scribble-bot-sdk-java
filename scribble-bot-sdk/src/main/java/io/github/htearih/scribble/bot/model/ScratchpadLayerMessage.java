package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Creates, updates or deletes a scratchpad layer.
 *
 * <p>Upsert the layer by its {@link #layerId()}, and upsert or delete its frames to match
 * {@link #frames()}. <strong>An empty {@code frames} list means the layer was deleted.</strong>
 *
 * <p><em>Layers vs. frames.</em> Layers define the top-level rendering z-index; frames belong to
 * layers and hold the actual drawing objects. Only one frame per layer is rendered at any moment,
 * and the server currently sends exactly one — the static preview. Objects within a frame render in
 * ascending {@code objectId} order. See {@code https://scribble.pub/docs/animations}.
 *
 * @param type        always {@value #TYPE}
 * @param layerId     a session-scoped identifier for the layer. Layers, frames and objects each
 *                    have their own independent ID counters
 * @param frames      the IDs of this layer's frames. Frame IDs are room-global and each belongs to
 *                    exactly one layer. Empty means the layer was deleted
 * @param lastEventId the ID of the last event that created or modified this layer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScratchpadLayerMessage(String type, long layerId, List<Long> frames, long lastEventId)
        implements RoomMessage {

    /** The wire discriminator for this message. */
    public static final String TYPE = "sp.layer";

    public ScratchpadLayerMessage {
        type = type == null ? TYPE : type;
        frames = frames == null ? List.of() : List.copyOf(frames);
    }

    /** Whether this message deletes the layer, i.e. it carries no frames. */
    public boolean isDeletion() {
        return frames.isEmpty();
    }
}
