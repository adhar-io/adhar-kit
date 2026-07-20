package com.adhar.adharkit.logging.event;

import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AppLogEventPublisher}.
 */
class AppLogEventPublisherTest {

    private AdharLoggingProperties properties;
    private RecordingAppLogEventSink sink;
    private AppLogEventPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new AdharLoggingProperties();
        sink = new RecordingAppLogEventSink();
        publisher = new AppLogEventPublisher(properties,
                new LogDataMasker(properties.getMasking()), List.of(sink));
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void publishDispatchesToSink() {
        publisher.publish(AppLogEvent.builder().name("op").build());

        assertThat(sink.getEvents()).hasSize(1);
        assertThat(sink.last().getName()).isEqualTo("op");
    }

    @Test
    void publishIgnoresNullEvent() {
        publisher.publish(null);
        assertThat(sink.getEvents()).isEmpty();
    }

    @Test
    void publishDoesNothingWhenEventsDisabled() {
        properties.getEvents().setEnabled(false);
        publisher.publish(AppLogEvent.builder().name("op").build());

        assertThat(sink.getEvents()).isEmpty();
        assertThat(publisher.isEnabled()).isFalse();
    }

    @Test
    void publishEnrichesFromMdc() {
        MDC.put("correlationId", "corr-1");
        MDC.put("traceId", "trace-1");
        MDC.put("spanId", "span-1");
        MDC.put("userId", "user-1");
        MDC.put("tenantId", "tenant-1");

        publisher.publish(AppLogEvent.builder().name("op").build());

        AppLogEvent event = sink.last();
        assertThat(event.getCorrelationId()).isEqualTo("corr-1");
        assertThat(event.getTraceId()).isEqualTo("trace-1");
        assertThat(event.getSpanId()).isEqualTo("span-1");
        assertThat(event.getUserId()).isEqualTo("user-1");
        assertThat(event.getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    void explicitValuesWinOverMdc() {
        MDC.put("correlationId", "mdc-corr");
        MDC.put("userId", "mdc-user");

        publisher.publish(AppLogEvent.builder()
                .name("op")
                .correlationId("explicit-corr")
                .userId("explicit-user")
                .build());

        assertThat(sink.last().getCorrelationId()).isEqualTo("explicit-corr");
        assertThat(sink.last().getUserId()).isEqualTo("explicit-user");
    }

    @Test
    void publishMasksMessageAndMetadata() {
        publisher.publish(AppLogEvent.builder()
                .name("op")
                .message("login with password=hunter22 ok")
                .metadata("password", "hunter22")
                .metadata(Map.of("nested", Map.of("apiKey", "abc123")))
                .build());

        AppLogEvent event = sink.last();
        assertThat(event.getMessage()).doesNotContain("hunter22");
        assertThat(event.getMetadata().get("password")).isEqualTo(LogDataMasker.MASK_VALUE);
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) event.getMetadata().get("nested");
        assertThat(nested.get("apiKey")).isEqualTo(LogDataMasker.MASK_VALUE);
    }

    @Test
    void failingSinkDoesNotBreakPublishingOrOtherSinks() {
        RecordingAppLogEventSink second = new RecordingAppLogEventSink();
        AppLogEventPublisher failingFirst = new AppLogEventPublisher(properties,
                new LogDataMasker(properties.getMasking()),
                List.of(event -> {
                    throw new IllegalStateException("sink down");
                }, second));

        failingFirst.publish(AppLogEvent.builder().name("op").build());

        assertThat(second.getEvents()).hasSize(1);
    }

    @Test
    void nullSinkListIsTolerated() {
        AppLogEventPublisher noSinks = new AppLogEventPublisher(properties,
                new LogDataMasker(properties.getMasking()), null);
        noSinks.publish(AppLogEvent.builder().name("op").build());
        // no exception expected
        assertThat(noSinks.isEnabled()).isTrue();
    }
}
