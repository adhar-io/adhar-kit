package com.adhar.adharkit.messaging.outbox;

import com.adhar.kit.messaging.exception.MessagingException;
import com.adhar.kit.messaging.outbox.OutboxPayloadCodec;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link OutboxPayloadCodec}.
 */
class OutboxPayloadCodecTest {

    private final OutboxPayloadCodec codec = new OutboxPayloadCodec();

    public static class Sample {
        public String name;
        public int value;

        public Sample() {
        }

        public Sample(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Sample other)) {
                return false;
            }
            return value == other.value && Objects.equals(name, other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }

    @Test
    void serializeThenDeserializeRoundTripsPojo() {
        Sample original = new Sample("order", 42);
        String json = codec.serialize(original);
        assertTrue(json.contains("order"));

        Object restored = codec.deserialize(json, Sample.class.getName());
        assertInstanceOf(Sample.class, restored);
        assertEquals(original, restored);
    }

    @Test
    void deserializeUnknownTypeFallsBackToRawJson() {
        Object result = codec.deserialize("{\"a\":1}", "com.example.DoesNotExist");
        assertEquals("{\"a\":1}", result);
    }

    @Test
    void deserializeNullOrBlankTypeReturnsRawJson() {
        assertEquals("raw", codec.deserialize("raw", null));
        assertEquals("raw", codec.deserialize("raw", "  "));
    }

    @Test
    void deserializeStringTypeUnwrapsJsonString() {
        String json = codec.serialize("hello");
        assertEquals("hello", codec.deserialize(json, String.class.getName()));
    }

    @Test
    void serializeFailureThrowsMessagingException() {
        // A bare Object has no serializable properties; the default ObjectMapper's
        // FAIL_ON_EMPTY_BEANS makes this throw, which the codec wraps.
        assertThrows(MessagingException.class, () -> codec.serialize(new Object()));
    }

    @Test
    void deserializeInvalidJsonForKnownTypeThrows() {
        assertThrows(MessagingException.class,
                () -> codec.deserialize("not-json", Sample.class.getName()));
    }
}
