package com.adhar.kit.dapr.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.dapr.serializer.DefaultObjectSerializer;
import io.dapr.utils.TypeRef;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;

/**
 * The serializer every kit client uses for state values, event payloads, binding data and job
 * data: the SDK's {@link DefaultObjectSerializer} semantics (raw {@code byte[]} untouched, JSON for
 * everything else, CloudEvent and protobuf handling) with a mapper that also understands
 * {@code java.time} and writes ISO-8601 text rather than epoch numbers.
 *
 * <p>The SDK's own mapper registers no modules, so a record with an {@code Instant} could not be
 * saved or published at all. ISO text is also what the TypeScript, Go and Python kits write, so
 * a value saved by one service reads back in another.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprJsonSerializer extends DefaultObjectSerializer {

    private static final ObjectMapper MAPPER = newObjectMapper();

    private final ObjectMapper objectMapper;

    public DaprJsonSerializer() {
        this(MAPPER);
    }

    public DaprJsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * A mapper configured the way the kit serializes Dapr payloads.
     *
     * @return a new mapper
     */
    public static ObjectMapper newObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /** The mapper behind this serializer, for converting already-parsed values. */
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    /**
     * Serializes without a checked exception.
     *
     * @param value the value
     * @return the bytes ({@code null} for {@code null})
     */
    public byte[] toBytes(Object value) {
        try {
            return serialize(value);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize " + value.getClass().getName(), e);
        }
    }

    /**
     * Deserializes without a checked exception.
     *
     * @param bytes the bytes
     * @param type  the target type
     * @param <T>   the target type
     * @return the value
     */
    public <T> T fromBytes(byte[] bytes, TypeRef<T> type) {
        try {
            return deserialize(bytes, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize into " + type.getType().getTypeName(), e);
        }
    }

    /**
     * Converts an already-parsed value (a {@code Map} from a CloudEvent, for example) to a type.
     *
     * @param value the value
     * @param type  the target type
     * @param <T>   the target type
     * @return the converted value
     */
    public <T> T convert(Object value, Type type) {
        return objectMapper.convertValue(value, objectMapper.constructType(type));
    }
}
