package io.github.htearih.scribble.bot.state;

import io.github.htearih.scribble.bot.model.RoomMessage;
import io.github.htearih.scribble.bot.model.ScratchpadLastEventIdMessage;
import io.github.htearih.scribble.bot.model.ScratchpadLayerMessage;
import io.github.htearih.scribble.bot.model.ScratchpadObjectMessage;
import io.github.htearih.scribble.bot.model.ScratchpadSessionMetaMessage;
import io.github.htearih.scribble.bot.model.ScratchpadSetLayerOrderMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The drawing surface of a scribble.pub room, rebuilt from the {@code sp.*} messages that describe
 * it.
 *
 * <p>Feed it whatever {@link io.github.htearih.scribble.bot.ScribblePubBot#getScratchpadStateMessages}
 * returned, or apply messages one at a time as they arrive:
 *
 * <pre>{@code
 * var state = bot.getScratchpadState("main");
 * for (var layerId : state.layerOrder()) {           // bottom to top
 *     for (var frameId : state.layers().get(layerId).frames()) {
 *         for (var object : state.frames().get(frameId).objects()) {
 *             draw(object);                          // already in render order
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Mutable on purpose.</strong> This is a game-engine-style reducer, not a Redux one: a
 * busy room holds tens of thousands of objects, and rebuilding an immutable graph per event would
 * spend more time in the garbage collector than in the bot. Unrecognised message types are ignored
 * rather than rejected, so a newer platform cannot break an older bot.
 *
 * <p>Not thread-safe. Confine an instance to one thread, or guard it yourself.
 */
public final class ScratchpadState {

    /** One drawing session's worth of state, replaced wholesale when a new session begins. */
    private static final class DrawingSession {
        private ScratchpadSessionMetaMessage meta;
        private final Map<Long, ScratchpadLayer> layers = new LinkedHashMap<>();
        private List<Long> layerOrder = new ArrayList<>();
        private final Map<Long, ScratchpadObjectMessage> objects = new HashMap<>();
        private final Map<Long, ScratchpadFrame> frames = new LinkedHashMap<>();
        private long lastEventId = -1;
    }

    private DrawingSession session = new DrawingSession();

    /** Builds a state out of the messages that arrived. */
    public static ScratchpadState fromMessages(List<RoomMessage> messages) {
        var state = new ScratchpadState();
        state.applyMessages(messages);
        return state;
    }

    /** Metadata of the current drawing session, or {@code null} if none has arrived yet. */
    public ScratchpadSessionMetaMessage sessionMeta() {
        return session.meta;
    }

    /** The active layers, keyed by layer ID. */
    public Map<Long, ScratchpadLayer> layers() {
        return Collections.unmodifiableMap(session.layers);
    }

    /** Layer IDs from bottom to top. The bottommost is drawn on top of a white background. */
    public List<Long> layerOrder() {
        return Collections.unmodifiableList(session.layerOrder);
    }

    /** Every object in the room, keyed by object ID. */
    public Map<Long, ScratchpadObjectMessage> objects() {
        return Collections.unmodifiableMap(session.objects);
    }

    /** Every frame in the room, keyed by frame ID. */
    public Map<Long, ScratchpadFrame> frames() {
        return Collections.unmodifiableMap(session.frames);
    }

    /**
     * The scratchpad's event counter, or {@code -1} if nothing has arrived yet. This is the cursor to
     * resume a partial update from, and it resets with the session.
     */
    public long lastEventId() {
        return session.lastEventId;
    }

    /** Applies a batch of messages in order. A {@code null} list is a no-op. */
    public void applyMessages(List<RoomMessage> messages) {
        if (messages == null) {
            return;
        }
        for (var message : messages) {
            applyMessage(message);
        }
    }

    /**
     * Applies one message.
     *
     * <p>Do not mutate a message after handing it over: the state keeps the lists inside it rather
     * than copying them, which is safe for a freshly parsed payload and nothing else.
     */
    public void applyMessage(RoomMessage message) {
        if (message == null) {
            return;
        }
        // A plain instanceof chain rather than a pattern switch: the SDK compiles against Java 17,
        // which is Spring Boot 4's own floor.
        if (message instanceof ScratchpadSessionMetaMessage meta) {
            applySessionMeta(meta);
        } else if (message instanceof ScratchpadLayerMessage layer) {
            applyLayer(layer);
        } else if (message instanceof ScratchpadSetLayerOrderMessage order) {
            session.layerOrder = new ArrayList<>(order.order());
            trackEventId(order.lastEventId());
        } else if (message instanceof ScratchpadObjectMessage object) {
            applyObject(object);
        } else if (message instanceof ScratchpadLastEventIdMessage last) {
            trackEventId(last.lastEventId());
        }
        // Anything else is a type this SDK version does not model. Ignoring it is the contract.
    }

    private void applySessionMeta(ScratchpadSessionMetaMessage message) {
        // Session meta always begins a new drawing, so everything before it is gone.
        session = new DrawingSession();
        session.meta = message;
        trackEventId(message.eventId());
    }

    private void applyLayer(ScratchpadLayerMessage message) {
        var layer = session.layers.get(message.layerId());
        trackEventId(message.lastEventId());

        if (message.frames().isEmpty()) {
            // An empty frame list is how a layer is deleted.
            session.layers.remove(message.layerId());
            session.layerOrder.remove(Long.valueOf(message.layerId()));
            if (layer != null) {
                for (var frameId : List.copyOf(layer.frames())) {
                    deleteFrame(frameId);
                }
            }
            return;
        }

        if (layer != null) {
            // Drop the frames this update took away.
            var kept = new HashSet<>(message.frames());
            for (var frameId : List.copyOf(layer.frames())) {
                if (!kept.contains(frameId)) {
                    deleteFrame(frameId);
                }
            }
            layer.update(message.frames(), message.lastEventId());
        } else {
            session.layers.put(
                    message.layerId(),
                    new ScratchpadLayer(message.layerId(), message.frames(), message.lastEventId()));
        }

        for (var frameId : message.frames()) {
            session.frames.computeIfAbsent(frameId, id -> new ScratchpadFrame(id, message.layerId()));
        }
    }

    private void applyObject(ScratchpadObjectMessage message) {
        var frame = session.frames.get(message.frameId());
        if (frame == null) {
            // Nothing to attach it to. Not expected, but dropping it is the defined behaviour.
            return;
        }
        trackEventId(message.eventId());

        var existing = session.objects.put(message.objectId(), message);
        if (existing != null) {
            var objects = frame.mutableObjects();
            for (var i = 0; i < objects.size(); i++) {
                if (objects.get(i).objectId() == message.objectId()) {
                    objects.set(i, message);
                    return;
                }
            }
        }
        // Objects arrive in ascending objectId order, which is also their z-index, so appending
        // keeps the frame in render order without a sort.
        frame.mutableObjects().add(message);
    }

    private void deleteFrame(long frameId) {
        var frame = session.frames.remove(frameId);
        if (frame != null) {
            for (var object : frame.objects()) {
                session.objects.remove(object.objectId());
            }
        }
    }

    /**
     * Event IDs carry no ordering guarantee and some of them are {@code -1}, so the counter is the
     * maximum seen rather than the latest received.
     */
    private void trackEventId(long eventId) {
        if (eventId > session.lastEventId) {
            session.lastEventId = eventId;
        }
    }
}
