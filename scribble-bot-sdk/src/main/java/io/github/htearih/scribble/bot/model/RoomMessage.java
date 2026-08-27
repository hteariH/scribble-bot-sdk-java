package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Any message describing what is in a room. Today that is only the scratchpad's {@code sp.*} events;
 * chat will join them later, which is why the type is not named after the scratchpad.
 *
 * <p><strong>Read these leniently.</strong> The platform adds fields and message types as it grows,
 * and a bot that fails on an unrecognised one breaks the moment the server is upgraded. Unknown
 * types deserialise to {@link UnknownRoomMessage} and are ignored by
 * {@link io.github.htearih.scribble.bot.state.ScratchpadState}.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true,
        defaultImpl = UnknownRoomMessage.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ScratchpadSessionMetaMessage.class, name = ScratchpadSessionMetaMessage.TYPE),
    @JsonSubTypes.Type(value = ScratchpadLayerMessage.class, name = ScratchpadLayerMessage.TYPE),
    @JsonSubTypes.Type(value = ScratchpadSetLayerOrderMessage.class, name = ScratchpadSetLayerOrderMessage.TYPE),
    @JsonSubTypes.Type(value = ScratchpadObjectMessage.class, name = ScratchpadObjectMessage.TYPE),
    @JsonSubTypes.Type(value = ScratchpadLastEventIdMessage.class, name = ScratchpadLastEventIdMessage.TYPE)
})
public sealed interface RoomMessage
        permits ScratchpadSessionMetaMessage,
                ScratchpadLayerMessage,
                ScratchpadSetLayerOrderMessage,
                ScratchpadObjectMessage,
                ScratchpadLastEventIdMessage,
                UnknownRoomMessage {

    /** The wire discriminator, e.g. {@code sp.layer}. */
    String type();
}
