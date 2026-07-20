package com.adhar.adharkit.logging.event;

import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BusinessEventLogger}.
 */
class BusinessEventLoggerTest {

    private RecordingAppLogEventSink sink;
    private BusinessEventLogger logger;

    @BeforeEach
    void setUp() {
        AdharLoggingProperties properties = new AdharLoggingProperties();
        sink = new RecordingAppLogEventSink();
        logger = new BusinessEventLogger(new AppLogEventPublisher(properties,
                new LogDataMasker(properties.getMasking()), List.of(sink)));
    }

    @Test
    void businessEventPublishesSuccess() {
        logger.businessEvent("order", "ORDER_PLACED", Map.of("orderId", "o-1"));

        AppLogEvent event = sink.last();
        assertThat(event.getType()).isEqualTo(AppLogEventType.BUSINESS);
        assertThat(event.getCategory()).isEqualTo("order");
        assertThat(event.getName()).isEqualTo("ORDER_PLACED");
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.SUCCESS);
        assertThat(event.getMetadata()).containsEntry("orderId", "o-1");
    }

    @Test
    void businessEventWithoutMetadata() {
        logger.businessEvent("order", "ORDER_PLACED");
        assertThat(sink.last().getName()).isEqualTo("ORDER_PLACED");
    }

    @Test
    void businessEventWithMessage() {
        logger.businessEvent("order", "ORDER_PLACED", "order placed", Map.of("k", "v"));
        assertThat(sink.last().getMessage()).isEqualTo("order placed");
    }

    @Test
    void businessEventFailedPublishesFailureWithError() {
        logger.businessEventFailed("order", "ORDER_PLACED",
                new IllegalStateException("out of stock"), Map.of("orderId", "o-1"));

        AppLogEvent event = sink.last();
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
        assertThat(event.getSeverity()).isEqualTo(Level.ERROR);
        assertThat(event.getErrorType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(event.getErrorMessage()).isEqualTo("out of stock");
    }

    @Test
    void operationLifecycleMethodsPublishEvents() {
        logger.operationStarted("op");
        assertThat(sink.last().getOutcome()).isEqualTo(AppLogEventOutcome.STARTED);

        logger.operationSucceeded("op", 12, Map.of("k", "v"));
        assertThat(sink.last().getOutcome()).isEqualTo(AppLogEventOutcome.SUCCESS);
        assertThat(sink.last().getDurationMs()).isEqualTo(12L);

        logger.operationFailed("op", 20, new RuntimeException("x"), null);
        assertThat(sink.last().getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
        assertThat(sink.last().getErrorMessage()).isEqualTo("x");
    }

    @Test
    void operationScopePublishesSuccessOnClose() {
        try (BusinessEventLogger.OperationScope scope = logger.startOperation("order.fulfil")) {
            scope.metadata("orderId", "o-1");
        }

        AppLogEvent event = sink.last();
        assertThat(event.getType()).isEqualTo(AppLogEventType.OPERATION);
        assertThat(event.getName()).isEqualTo("order.fulfil");
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.SUCCESS);
        assertThat(event.getDurationMs()).isNotNull();
        assertThat(event.getMetadata()).containsEntry("orderId", "o-1");
    }

    @Test
    void operationScopeRecordsFailure() {
        try (BusinessEventLogger.OperationScope scope = logger.startOperation("op", "orders")) {
            scope.failure(new IllegalArgumentException("bad"));
        }

        AppLogEvent event = sink.last();
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
        assertThat(event.getSeverity()).isEqualTo(Level.ERROR);
        assertThat(event.getCategory()).isEqualTo("orders");
        assertThat(event.getErrorMessage()).isEqualTo("bad");
    }

    @Test
    void operationScopeSupportsExplicitOutcomeAndSuccessReset() {
        try (BusinessEventLogger.OperationScope scope = logger.startOperation("op")) {
            scope.outcome(AppLogEventOutcome.TIMEOUT);
            scope.success();
        }
        assertThat(sink.last().getOutcome()).isEqualTo(AppLogEventOutcome.SUCCESS);
    }

    @Test
    void operationScopeCloseIsIdempotent() {
        BusinessEventLogger.OperationScope scope = logger.startOperation("op");
        scope.close();
        scope.close();
        assertThat(sink.getEvents()).hasSize(1);
    }
}
