package com.adhar.adharkit.cache.dapr;

import com.adhar.kit.dapr.serialization.DaprJsonSerializer;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.cache.support.NullValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * How cached values are stored in a Dapr state store: JSON carrying the class of every value, so
 * a cache hit returns the class that was cached (records, {@code java.time} values and the
 * elements of a {@code List<Order>} included) instead of the {@code Map} an untyped read gives,
 * which made {@code @Cacheable} fail with a {@code ClassCastException} on its first hit.
 *
 * <p>The type information is trusted on read, exactly as the module's Redis cache trusts it:
 * anything able to write the cache keys can choose the class a hit deserializes into, so the
 * state store must be one only the application writes.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class DaprCacheValueCodec {

    /** Spring's "cached null" marker, which must come back as the same singleton. */
    private static final byte[] NULL_MARKER = "{\"@adhar.cache.null\":true}".getBytes(StandardCharsets.UTF_8);

    private final ObjectMapper mapper;

    @SuppressWarnings("deprecation") // EVERYTHING is what types final classes (records, String) too
    public DaprCacheValueCodec() {
        this.mapper = DaprJsonSerializer.newObjectMapper();
        this.mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
    }

    /**
     * Serializes a value with its type.
     *
     * @param value the value ({@code null} and {@link NullValue} become the null marker)
     * @return the bytes to store
     */
    public byte[] encode(Object value) {
        if (value == null || value == NullValue.INSTANCE) {
            return NULL_MARKER.clone();
        }
        try {
            return mapper.writeValueAsBytes(value);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot cache a " + value.getClass().getName() + " as JSON", e);
        }
    }

    /**
     * Reads a stored value back as the class it was written with.
     *
     * @param bytes the stored bytes
     * @return the value, {@link NullValue#INSTANCE} for a cached null
     */
    public Object decode(byte[] bytes) {
        if (Arrays.equals(bytes, NULL_MARKER)) {
            return NullValue.INSTANCE;
        }
        try {
            return mapper.readValue(bytes, Object.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read a cached value", e);
        }
    }
}
