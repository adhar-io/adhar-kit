package com.adhar.kit.health.event;

import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HealthTransitionJson}.
 */
class HealthTransitionJsonTest {

    @Test
    void toJson_serializesAllFields() {
        Instant ts = Instant.parse("2026-01-01T10:15:30Z");
        HealthTransition t = new HealthTransition("db", Health.Status.UP, Health.Status.DOWN, ts);

        String json = HealthTransitionJson.toJson(t);

        assertThat(json).isEqualTo(
                "{\"indicator\":\"db\",\"from\":\"UP\",\"to\":\"DOWN\","
                        + "\"timestamp\":\"2026-01-01T10:15:30Z\",\"initial\":false}");
    }

    @Test
    void toJson_initialTransition_hasNullFromAndInitialTrue() {
        Instant ts = Instant.parse("2026-01-01T10:15:30Z");
        HealthTransition t = new HealthTransition("cache", null, Health.Status.UP, ts);

        String json = HealthTransitionJson.toJson(t);

        assertThat(json).contains("\"from\":null");
        assertThat(json).contains("\"initial\":true");
        assertThat(json).contains("\"to\":\"UP\"");
    }

    @Test
    void toJson_escapesQuotesBackslashesNewlinesTabs() {
        String tricky = "svc\"a\\b\nc\td";
        HealthTransition t = new HealthTransition(
                tricky, Health.Status.UP, Health.Status.UP, Instant.parse("2026-01-01T00:00:00Z"));

        String json = HealthTransitionJson.toJson(t);

        // No raw control chars leak through
        assertThat(json).doesNotContain("\n").doesNotContain("\t");
        // Escaped forms are present
        assertThat(json).contains("svc").contains("\\\"").contains("\\\\");
    }

    @Test
    void toJson_escapesLowControlCharactersAsUnicode() {
        String withControl = "a" + ((char) 1) + "b";
        HealthTransition t = new HealthTransition(
                withControl, Health.Status.UP, Health.Status.UP, Instant.parse("2026-01-01T00:00:00Z"));

        String json = HealthTransitionJson.toJson(t);

        assertThat(json).contains("u0001");
        assertThat(json.indexOf((char) 1)).isEqualTo(-1);
    }

    @Test
    void toJson_nullStatusesAndTimestamp_renderNull() {
        HealthTransition t = new HealthTransition("x", null, null, null);

        String json = HealthTransitionJson.toJson(t);

        assertThat(json).contains("\"from\":null");
        assertThat(json).contains("\"to\":null");
        assertThat(json).contains("\"timestamp\":null");
    }
}
