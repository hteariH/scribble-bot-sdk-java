package io.github.htearih.scribble.bot.state;

import io.github.htearih.scribble.bot.model.RoomMessage;
import java.util.List;

/**
 * A mutable state for a scribble.pub room.
 *
 * <p>It holds substates per room apps (scratchpad, chat) and routes incoming {@link RoomMessage}
 * to them.
 */
public class RoomState {

    private final ScratchpadState scratchpad = new ScratchpadState();

    /** The drawing surface of the room. */
    public ScratchpadState scratchpad() {
        return scratchpad;
    }

    /** Builds a state out of arrived messages. */
    public static RoomState fromMessages(List<RoomMessage> messages) {
        var state = new RoomState();
        state.applyMessages(messages);
        return state;
    }

    /** Applies an array of RoomMessages in order. */
    public void applyMessages(List<RoomMessage> messages) {
        scratchpad.applyMessages(messages);
    }

    /** Applies a single RoomMessage to the substate that owns it. */
    public void applyMessage(RoomMessage msg) {
        scratchpad.applyMessage(msg);
    }
}
