package com.adhar.adharkit.logging.event;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AppLogEvent}.
 */
class AppLogEventTest {

    @Test
    void buildAppliesDefaults() {
        AppLogEvent event = AppLogEvent.builder().name("op").build();

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getTimestamp()).isNotNull();
        assertThat(event.getType()).isEqualTo(AppLogEventType.OPERATION);
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.SUCCESS);
        assertThat(event.getSeverity()).isEqualTo(Level.INFO);
        assertThat(event.getMetadata()).isEmpty();
        assertThat(event.getTags()).isEmpty();
    }

    @Test
    void buildKeepsExplicitValues() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        AppLogEvent event = AppLogEvent.builder()
                .eventId("id-1")
                .timestamp(now)
                .type(AppLogEventType.BUSINESS)
                .category("order")
                .name("ORDER_PLACED")
                .message("placed")
                .outcome(AppLogEventOutcome.PARTIAL)
                .severity(Level.WARN)
                .source(AppLogEventTest.class)
                .durationMs(42L)
                .correlationId("corr")
                .traceId("trace")
                .spanId("span")
                .userId("user")
                .tenantId("tenant")
                .metadata("orderId", "o-1")
                .metadata(Map.of("amount", 10))
                .tags("a", "b")
                .build();

        assertThat(event.getEventId()).isEqualTo("id-1");
        assertThat(event.getTimestamp()).isEqualTo(now);
        assertThat(event.getType()).isEqualTo(AppLogEventType.BUSINESS);
        assertThat(event.getCategory()).isEqualTo("order");
        assertThat(event.getName()).isEqualTo("ORDER_PLACED");
        assertThat(event.getMessage()).isEqualTo("placed");
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.PARTIAL);
        assertThat(event.getSeverity()).isEqualTo(Level.WARN);
        assertThat(event.getSource()).isEqualTo(AppLogEventTest.class.getName());
        assertThat(event.getDurationMs()).isEqualTo(42L);
        assertThat(event.getCorrelationId()).isEqualTo("corr");
        assertThat(event.getTraceId()).isEqualTo("trace");
        assertThat(event.getSpanId()).isEqualTo("span");
        assertThat(event.getUserId()).isEqualTo("user");
        assertThat(event.getTenantId()).isEqualTo("tenant");
        assertThat(event.getMetadata()).containsEntry("orderId", "o-1").containsEntry("amount", 10);
        assertThat(event.getTags()).containsExactly("a", "b");
    }

    @Test
    void errorFromThrowablePopulatesTypeAndMessage() {
        AppLogEvent event = AppLogEvent.builder()
                .error(new IllegalStateException("boom"))
                .outcome(AppLogEventOutcome.FAILURE)
                .build();

        assertThat(event.getErrorType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(event.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void toMapSkipsNullAndEmptyFields() {
        AppLogEvent event = AppLogEvent.builder().name("op").build();
        Map<String, Object> map = event.toMap();

        assertThat(map).containsKeys("eventId", "timestamp", "type", "name", "outcome");
        assertThat(map).doesNotContainKeys("category", "message", "durationMs", "correlationId",
                "errorType", "metadata", "tags");
    }

    @Test
    void toMapIncludesPopulatedFields() {
        AppLogEvent event = AppLogEvent.builder()
                .name("op")
                .durationMs(10L)
                .metadata("k", "v")
                .tags("t")
                .error(new RuntimeException("x"))
                .build();
        Map<String, Object> map = event.toMap();

        assertThat(map).containsEntry("durationMs", 10L);
        assertThat(map).containsEntry("errorType", RuntimeException.class.getName());
        assertThat(map.get("metadata")).isEqualTo(Map.of("k", "v"));
        assertThat(map).containsKey("tags");
    }

    @Test
    void toBuilderCopiesAllFields() {
        AppLogEvent original = AppLogEvent.builder()
                .type(AppLogEventType.AUDIT)
                .category("audit")
                .name("USER_DELETED")
                .userId("admin")
                .metadata("resourceId", "u-1")
                .tags("compliance")
                .durationMs(5L)
                .build();

        AppLogEvent copy = original.toBuilder().build();

        assertThat(copy.toMap()).isEqualTo(original.toMap());
    }

    @Test
    void toStringContainsEventFields() {
        AppLogEvent event = AppLogEvent.builder().name("op").build();
        assertThat(event.toString()).contains("op").contains("OPERATION");
    }

    @Test
    void builderIgnoresNullMetadataKeyAndBlankTags() {
        AppLogEvent event = AppLogEvent.builder()
                .metadata((String) null, "x")
                .metadata((Map<String, ?>) null)
                .tags((String[]) null)
                .tags("", null, "ok")
                .build();

        assertThat(event.getMetadata()).isEmpty();
        assertThat(event.getTags()).containsExactly("ok");
    }
}
