package io.github.htearih.scribble.bot.json;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The SDK's JSON codec. Jackson 3 ({@code tools.jackson}), the same generation Spring Boot 4 ships.
 *
 * <p>Wrap an existing {@link ObjectMapper} to reuse an application's configuration; the default one
 * is deliberately vanilla, because the wire format is five flat fields and anything clever here
 * only risks changing them.
 */
public final class Json {

    private final ObjectMapper mapper;

    public Json() {
        this(JsonMapper.builder().build());
    }

    public Json(ObjectMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper is required");
        }
        this.mapper = mapper;
    }

    /** @throws tools.jackson.core.JacksonException when the bytes are not readable as {@code type} */
    public <T> T read(byte[] bytes, Class<T> type) {
        return mapper.readValue(bytes == null ? new byte[0] : bytes, type);
    }

    public byte[] write(Object value) {
        return writeAsString(value).getBytes(StandardCharsets.UTF_8);
    }

    public String writeAsString(Object value) {
        return mapper.writeValueAsString(value);
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
