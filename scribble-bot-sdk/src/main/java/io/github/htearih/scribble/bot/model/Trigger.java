package io.github.htearih.scribble.bot.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The event scribble.pub delivered, discriminated by {@link #type()}.
 *
 * <p>The discriminant is {@code type} — the same field name {@link Action#type()} uses outbound.
 * (Upstream 0.3.0 and earlier spelled it {@code trigger}; 0.4.0 removed that spelling outright, and
 * this port never carried it.)
 *
 * <p><strong>Unknown types are not an error.</strong> The platform is free to invent triggers this
 * SDK has never heard of, and a bot that answers those with a 400 looks broken from the outside.
 * Anything unrecognised deserialises to {@link UnsupportedTrigger} and is acknowledged with an
 * empty action list unless {@link io.github.htearih.scribble.bot.ScribblePubBot#onUnsupported}
 * claims it.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true,
        defaultImpl = UnsupportedTrigger.class)
@JsonSubTypes({@JsonSubTypes.Type(value = ChatAddressedTrigger.class, name = ChatAddressedTrigger.TYPE)})
public sealed interface Trigger permits ChatAddressedTrigger, UnsupportedTrigger {

    /** The wire discriminator, e.g. {@code chat.addressed}. */
    String type();

    /** The room the event happened in. */
    String room();

    /** Platform timestamp, in seconds since the Unix epoch, passed through unchanged. */
    long timestamp();

    /**
     * The base API URL of this room's host instance (e.g. {@code https://eu.scribble.pub}), used to
     * reach the room without an intermediate redirect.
     */
    String directUrl();

    /** Whether this SDK version understands the trigger, i.e. it is not an {@link UnsupportedTrigger}. */
    default boolean isSupported() {
        return !(this instanceof UnsupportedTrigger);
    }

    /** Whether the base fields the SDK needs to answer at all are present. */
    default boolean isComplete() {
        return room() != null && !room().isBlank() && directUrl() != null && !directUrl().isBlank();
    }
}
