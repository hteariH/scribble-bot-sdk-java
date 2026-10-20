package io.github.htearih.scribble.bot.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;

/**
 * Maps a packed {@code rgba} colour between Java's signed {@code int} and the unsigned 32-bit number
 * the wire carries.
 *
 * <p>Red with any alpha is above {@link Integer#MAX_VALUE} on the wire — opaque red is
 * {@code 4278190335} — so a plain {@code int} binding fails on half of all colours one way and sends
 * a negative number the platform rejects the other. The bits are the same either way, which is what
 * {@link io.github.htearih.scribble.bot.color.Rgba} works on.
 */
public final class UnsignedRgba {

    private UnsignedRgba() {
    }

    /** Writes the colour as the unsigned number the platform expects. */
    public static final class Serializer extends ValueSerializer<Integer> {
        @Override
        public void serialize(Integer value, JsonGenerator generator, SerializationContext context) {
            generator.writeNumber(Integer.toUnsignedLong(value));
        }
    }

    /** Reads an unsigned (or, leniently, signed) 32-bit number into the same bits as an {@code int}. */
    public static final class Deserializer extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            return (int) parser.getLongValue();
        }
    }
}
