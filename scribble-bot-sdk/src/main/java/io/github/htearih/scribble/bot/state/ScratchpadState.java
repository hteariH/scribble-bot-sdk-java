package io.github.htearih.scribble.bot.state;

import io.github.htearih.scribble.bot.model.RoomMessage;
import io.github.htearih.scribble.bot.model.ScratchpadObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A mutable state for the drawing surface of a scribble.pub room.
 *
 * <p>This class accepts {@link RoomMessage} events and builds/updates the scratchpad's current
 * state. It is mutable (game-engine style) rather than immutable (React/Redux style) to prevent
 * massive GC overhead when processing tens of thousands of objects.
 */
public class ScratchpadState {

    private final Map<Integer, ScratchpadLayer> layers = new HashMap<>();
    private final Map<Integer, ScratchpadObject> objects = new HashMap<>();
    private final Map<Integer, ScratchpadFrame> frames = new HashMap<>();
    private int lastEventId = -1;

    /** Builds a state out of arrived messages. */
    public static ScratchpadState fromMessages(List<RoomMessage> messages) {
        var state = new ScratchpadState();
        state.applyMessages(messages);
        return state;
    }

    /** All active layers in the room, keyed by layerId. */
    public Map<Integer, ScratchpadLayer> layers() {
        return Collections.unmodifiableMap(layers);
    }

    /** All objects in the room, keyed by objectId. */
    public Map<Integer, ScratchpadObject> objects() {
        return Collections.unmodifiableMap(objects);
    }

    /** An index mapping frameId to its ScratchpadFrame. */
    public Map<Integer, ScratchpadFrame> frames() {
        return Collections.unmodifiableMap(frames);
    }

    /** The scratchpad's event counter, or -1 if nothing has arrived yet. */
    public int lastEventId() {
        return lastEventId;
    }

    /** Applies an array of RoomMessages in order. */
    public void applyMessages(List<RoomMessage> messages) {
        if (messages == null) return;
        for (var msg : messages) {
            applyMessage(msg);
        }
    }

    /** Applies the given RoomMessage to the state. */
    public void applyMessage(RoomMessage msg) {
        if (msg == null) return;
        // Placeholder for message processing - will be expanded as message types are defined
        // For now, this is a no-op as the full message type hierarchy is not yet modeled
    }

    private void trackEventId(int eventId) {
        if (eventId > lastEventId) {
            lastEventId = eventId;
        }
    }
}
