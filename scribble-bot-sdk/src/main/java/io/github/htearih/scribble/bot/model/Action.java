package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Something the bot asks the platform to do in a room. A flat discriminated union on {@code type}:
 * the discriminator sits next to the payload fields rather than wrapping them.
 *
 * <p>Chat has {@link AddMessage} ({@code chat.addMessage}). The scratchpad has the drawing actions
 * — {@link CheckSession}, {@link PutLayer}, {@link RemoveLayer}, {@link PutObject} and
 * {@link RemoveObject} — which identify everything by the bot's own local IDs, so drawing is fully
 * fire-and-forget. A request's drawing actions apply together, in order, with nothing else in
 * between; a missing target is skipped and the rest still applies.
 *
 * <p>The unprefixed {@code addMessage} spelling that upstream deprecated in 0.3.0 was removed in
 * 0.4.0 and is not accepted; this port never emitted it.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = AddMessage.class, name = AddMessage.TYPE),
    @JsonSubTypes.Type(value = CheckSession.class, name = CheckSession.TYPE),
    @JsonSubTypes.Type(value = PutLayer.class, name = PutLayer.TYPE),
    @JsonSubTypes.Type(value = RemoveLayer.class, name = RemoveLayer.TYPE),
    @JsonSubTypes.Type(value = PutObject.class, name = PutObject.TYPE),
    @JsonSubTypes.Type(value = RemoveObject.class, name = RemoveObject.TYPE)
})
public sealed interface Action permits AddMessage, CheckSession, PutLayer, RemoveLayer, PutObject, RemoveObject {

    /** The most actions one request or hook response may carry. */
    int MAX_ACTIONS = 5000;

    /** The largest a request or hook response may be, in bytes of JSON. */
    int MAX_PAYLOAD_BYTES = 4 * 1024 * 1024;

    /** The largest local ID any action may carry: 2^53-1, the platform's cap for JavaScript interop. */
    long MAX_LOCAL_ID = AddMessage.MAX_LOCAL_ID;

    /** The wire discriminator. */
    String type();

    /** Post {@code text} into the room as the bot. */
    static AddMessage addMessage(String text) {
        return new AddMessage(text);
    }

    /** Cancel the request's drawing unless the room is still on drawing session {@code session}. */
    static CheckSession checkSession(long session) {
        return new CheckSession(session);
    }

    /** Remove the bot's own layer {@code localId}. */
    static RemoveLayer removeLayer(long localId) {
        return new RemoveLayer(localId);
    }

    /** Remove the bot's own object {@code localId}. */
    static RemoveObject removeObject(long localId) {
        return new RemoveObject(localId);
    }

    /** Whether this action draws on the scratchpad, i.e. it is one of the {@code sp.*} actions. */
    @JsonIgnore
    default boolean isDrawing() {
        return type().startsWith("sp.");
    }
}
