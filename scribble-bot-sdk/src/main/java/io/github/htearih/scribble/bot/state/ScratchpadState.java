package io.github.htearih.scribble.bot.state;

import io.github.htearih.scribble.bot.model.AnimationMode;
import io.github.htearih.scribble.bot.model.RoomMessage;
import io.github.htearih.scribble.bot.model.ScratchpadLastEventIdMessage;
import io.github.htearih.scribble.bot.model.ScratchpadLayerMessage;
import io.github.htearih.scribble.bot.model.ScratchpadObjectMessage;
import io.github.htearih.scribble.bot.model.ScratchpadRollMessage;
import io.github.htearih.scribble.bot.model.ScratchpadSessionMetaMessage;
import io.github.htearih.scribble.bot.model.ScratchpadSetLayerOrderMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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
 *     var frameId = state.visibleFrameId(layerId);   // the frame viewers see now
 *     for (var object : state.frames().get(frameId).objects()) {
 *         draw(object);                              // already in render order
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

    /** Viewers' apps start sequential animations over every 15 minutes, which visibleFrameId must follow. */
    private static final long ANIMATION_PERIOD_MS = 15 * 60 * 1000;

    /** The tick of current drawings, for the odd case of layers without a session to read it from. */
    private static final int DEFAULT_TICK_MS = 125;

    /** One drawing session's worth of state, replaced wholesale when a new session begins. */
    private static final class DrawingSession {
        private ScratchpadSessionMetaMessage meta;
        private final Map<Long, ScratchpadLayer> layers = new LinkedHashMap<>();
        private List<Long> layerOrder = new ArrayList<>();
        private final Map<Long, ScratchpadObjectMessage> objects = new HashMap<>();
        private final Map<Long, ScratchpadFrame> frames = new LinkedHashMap<>();
        private double roll;
        private final Map<Long, ScratchpadLayer> ownLayers = new LinkedHashMap<>();
        private final Map<Long, ScratchpadFrame> ownFrames = new LinkedHashMap<>();
        private final Map<Long, ScratchpadObjectMessage> ownObjects = new LinkedHashMap<>();
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

    /** The room's last dice roll, from 0 (inclusive) to 1 (exclusive), which picks the frames of roll layers. */
    public double roll() {
        return session.roll;
    }

    /**
     * The frame of the layer that viewers see right now; for a {@link AnimationMode#RANDOM} layer,
     * one random viewer's. See {@link #visibleFrameId(long, long, double)}.
     */
    public Long visibleFrameId(long layerId) {
        return visibleFrameId(layerId, System.currentTimeMillis(), ThreadLocalRandom.current().nextDouble());
    }

    /** The frame of the layer that viewers see at {@code timeMillis}. See {@link #visibleFrameId(long, long, double)}. */
    public Long visibleFrameId(long layerId, long timeMillis) {
        return visibleFrameId(layerId, timeMillis, ThreadLocalRandom.current().nextDouble());
    }

    /**
     * The frame of the layer that viewers see at a given time, picked as viewers' apps do:
     *
     * <ul>
     *   <li><strong>{@link AnimationMode#SEQUENTIAL}:</strong> the frames loop on a clock shared by
     *       all viewers, so this is the frame everyone sees.
     *   <li><strong>{@link AnimationMode#ROLL}:</strong> the frame selected by the last dice roll,
     *       the same for everyone.
     *   <li><strong>{@link AnimationMode#RANDOM}:</strong> every viewer sees random frames of their
     *       own; {@code random} picks one. Viewers use the same number for all layers at a time.
     * </ul>
     *
     * @param layerId    the layer to look at
     * @param timeMillis the time, in epoch milliseconds
     * @param random     a number from 0 (inclusive) to 1 (exclusive), used by random layers only
     * @return the frame ID, or {@code null} if the layer is not there
     */
    public Long visibleFrameId(long layerId, long timeMillis, double random) {
        var layer = session.layers.get(layerId);
        if (layer == null || layer.frames().isEmpty()) {
            return null;
        }
        var frames = layer.frames();
        var count = frames.size();
        if (layer.animationMode() == AnimationMode.ROLL) {
            return frames.get(Math.min((int) Math.floor(session.roll * count), count - 1));
        }
        if (layer.animationMode() == AnimationMode.RANDOM) {
            return frames.get(Math.min((int) Math.floor(random * count), count - 1));
        }
        // Sequential, and any mode this version does not know, as upstream plays it.
        var meta = session.meta;
        long tickMs = meta == null || meta.animationTickMs() == null || meta.animationTickMs() <= 0
                ? DEFAULT_TICK_MS
                : meta.animationTickMs();
        var tick = Math.floorMod(Math.floorDiv(timeMillis, tickMs), ANIMATION_PERIOD_MS / tickMs);
        return frames.get((int) (tick % count));
    }

    /**
     * The bot's own layers, keyed by the local ID it gave them. Lets a bot that lost its memory —
     * restarted, say — find what it has already drawn.
     */
    public Map<Long, ScratchpadLayer> ownLayers() {
        return Collections.unmodifiableMap(session.ownLayers);
    }

    /** The bot's own frames, keyed by the local IDs it gave them in {@code frameLocalIds}. */
    public Map<Long, ScratchpadFrame> ownFrames() {
        return Collections.unmodifiableMap(session.ownFrames);
    }

    /** The bot's own objects, keyed by the local ID it gave them. */
    public Map<Long, ScratchpadObjectMessage> ownObjects() {
        return Collections.unmodifiableMap(session.ownObjects);
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
        } else if (message instanceof ScratchpadRollMessage roll) {
            session.roll = roll.roll();
            trackEventId(roll.lastEventId());
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
                unindexOwnFrames(layer);
                for (var frameId : List.copyOf(layer.frames())) {
                    deleteFrame(frameId);
                }
                if (layer.localId() != null && session.ownLayers.get(layer.localId()) == layer) {
                    session.ownLayers.remove(layer.localId());
                }
            }
            return;
        }

        if (layer != null) {
            unindexOwnFrames(layer);
            // Drop the frames this update took away.
            var kept = new HashSet<>(message.frames());
            for (var frameId : List.copyOf(layer.frames())) {
                if (!kept.contains(frameId)) {
                    deleteFrame(frameId);
                }
            }
        } else {
            layer = new ScratchpadLayer(message.layerId());
            session.layers.put(message.layerId(), layer);
        }
        layer.update(
                message.frames(),
                message.animationMode(),
                message.lastEventId(),
                message.localId(),
                message.frameIdsByLocalId());
        if (layer.localId() != null) {
            session.ownLayers.put(layer.localId(), layer);
        }

        // A program repeats frames, so the same ID may come several times; it is still one frame.
        for (var frameId : message.frames()) {
            session.frames.computeIfAbsent(frameId, id -> new ScratchpadFrame(id, message.layerId()));
        }
        if (message.frameIdsByLocalId() != null) {
            for (var entry : message.frameIdsByLocalId().entrySet()) {
                var frame = session.frames.get(entry.getValue());
                if (frame != null) {
                    session.ownFrames.put(entry.getKey(), frame);
                }
            }
        }
    }

    private void unindexOwnFrames(ScratchpadLayer layer) {
        if (layer.frameIdsByLocalId() == null) {
            return;
        }
        for (var entry : layer.frameIdsByLocalId().entrySet()) {
            var frame = session.ownFrames.get(entry.getKey());
            if (frame != null && frame.frameId() == entry.getValue()) {
                session.ownFrames.remove(entry.getKey());
            }
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
        if (message.localId() != null) {
            session.ownObjects.put(message.localId(), message);
        }
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
                if (object.localId() != null && session.ownObjects.get(object.localId()) == object) {
                    session.ownObjects.remove(object.localId());
                }
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
