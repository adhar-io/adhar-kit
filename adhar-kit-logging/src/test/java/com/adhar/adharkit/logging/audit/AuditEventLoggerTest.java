package com.adhar.adharkit.logging.audit;

import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import com.adhar.adharkit.logging.event.RecordingAppLogEventSink;
import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuditEventLogger}.
 */
class AuditEventLoggerTest {

    private AdharLoggingProperties properties;
    private RecordingAppLogEventSink sink;
    private AuditEventLogger auditLogger;

    @BeforeEach
    void setUp() {
        properties = new AdharLoggingProperties();
        sink = new RecordingAppLogEventSink();
        LogDataMasker masker = new LogDataMasker(properties.getMasking());
        auditLogger = new AuditEventLogger(properties,
                new AppLogEventPublisher(properties, masker, List.of(sink)), masker);
    }

    @Test
    void successPublishesAuditEventWithResourceAndReason() {
        auditLogger.event("USER_UPDATED")
                .actor("admin")
                .resource("User", "u-1")
                .reason("self-service")
                .metadata("channel", "web")
                .tags("compliance")
                .success();

        AppLogEvent event = sink.last();
        assertThat(event.getType()).isEqualTo(AppLogEventType.AUDIT);
        assertThat(event.getName()).isEqualTo("USER_UPDATED");
        assertThat(event.getCategory()).isEqualTo("audit");
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.SUCCESS);
        assertThat(event.getUserId()).isEqualTo("admin");
        assertThat(event.getMetadata())
                .containsEntry("resourceType", "User")
                .containsEntry("resourceId", "u-1")
                .containsEntry("reason", "self-service")
                .containsEntry("channel", "web");
        assertThat(event.getTags()).contains("compliance");
    }

    @Test
    void changesAreMaskedByFieldName() {
        auditLogger.event("USER_UPDATED")
                .change("email", "old@example.com", "new@example.com")
                .change("password", "oldSecret", "newSecret")
                .success();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes =
                (List<Map<String, Object>>) sink.last().getMetadata().get("changes");
        assertThat(changes).hasSize(2);
        assertThat(changes.get(0)).containsEntry("field", "email")
                .containsEntry("oldValue", "old@example.com");
        assertThat(changes.get(1)).containsEntry("field", "password")
                .containsEntry("oldValue", LogDataMasker.MASK_VALUE)
                .containsEntry("newValue", LogDataMasker.MASK_VALUE);
    }

    @Test
    void changesSkippedWhenDisabled() {
        properties.getAudit().setIncludeChanges(false);
        auditLogger.event("USER_UPDATED")
                .change("email", "a", "b")
                .success();

        assertThat(sink.last().getMetadata()).doesNotContainKey("changes");
    }

    @Test
    void failurePublishesErrorDetails() {
        auditLogger.event("PAYMENT_APPROVED")
                .failure(new IllegalStateException("limit exceeded"));

        AppLogEvent event = sink.last();
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
        assertThat(event.getSeverity()).isEqualTo(Level.ERROR);
        assertThat(event.getErrorMessage()).isEqualTo("limit exceeded");
    }

    @Test
    void deniedPublishesWarnEvent() {
        auditLogger.event("DOCUMENT_ACCESS").denied();

        assertThat(sink.last().getOutcome()).isEqualTo(AppLogEventOutcome.DENIED);
        assertThat(sink.last().getSeverity()).isEqualTo(Level.WARN);
    }

    @Test
    void explicitOutcomeLogging() {
        auditLogger.event("EXPORT").category("data").log(AppLogEventOutcome.PARTIAL);

        assertThat(sink.last().getOutcome()).isEqualTo(AppLogEventOutcome.PARTIAL);
        assertThat(sink.last().getCategory()).isEqualTo("data");

        auditLogger.event("EXPORT").log(AppLogEventOutcome.FAILURE);
        assertThat(sink.last().getSeverity()).isEqualTo(Level.ERROR);
    }

    @Test
    void disabledAuditPublishesNothing() {
        properties.getAudit().setEnabled(false);
        auditLogger.event("USER_UPDATED").success();

        assertThat(sink.getEvents()).isEmpty();
    }
}
