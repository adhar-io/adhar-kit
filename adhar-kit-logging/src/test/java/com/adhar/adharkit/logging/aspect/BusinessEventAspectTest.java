package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.BusinessEvent;
import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import com.adhar.adharkit.logging.event.RecordingAppLogEventSink;
import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.event.Level;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BusinessEventAspect}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BusinessEventAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private RecordingAppLogEventSink sink;
    private BusinessEventAspect aspect;

    @SuppressWarnings("unused")
    static class SampleTarget {
        @BusinessEvent(value = "ORDER_PLACED", category = "order", includeArgs = true, includeResult = true)
        public String placeOrder(String cartId) {
            return "order-1";
        }

        @BusinessEvent
        public void defaultNamed() {
        }

        public void notAnnotated() {
        }
    }

    @BeforeEach
    void setUp() {
        AdharLoggingProperties properties = new AdharLoggingProperties();
        LogDataMasker masker = new LogDataMasker(properties.getMasking());
        sink = new RecordingAppLogEventSink();
        aspect = new BusinessEventAspect(
                new AppLogEventPublisher(properties, masker, List.of(sink)), masker);
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    private void givenMethod(String name, Class<?>... params) throws Exception {
        Method method = SampleTarget.class.getDeclaredMethod(name, params);
        when(signature.getMethod()).thenReturn(method);
    }

    @Test
    void successPublishesBusinessEvent() throws Throwable {
        givenMethod("placeOrder", String.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"cart-1"});
        when(joinPoint.proceed()).thenReturn("order-1");

        Object result = aspect.publishBusinessEvent(joinPoint);

        assertThat(result).isEqualTo("order-1");
        AppLogEvent event = sink.last();
        assertThat(event.getType()).isEqualTo(AppLogEventType.BUSINESS);
        assertThat(event.getName()).isEqualTo("ORDER_PLACED");
        assertThat(event.getCategory()).isEqualTo("order");
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.SUCCESS);
        assertThat(event.getDurationMs()).isNotNull();
        assertThat(event.getMetadata()).containsKeys("arguments", "result");
    }

    @Test
    void failurePublishesFailureEventAndRethrows() throws Throwable {
        givenMethod("placeOrder", String.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"cart-1"});
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("empty cart"));

        assertThatThrownBy(() -> aspect.publishBusinessEvent(joinPoint))
                .isInstanceOf(IllegalArgumentException.class);

        AppLogEvent event = sink.last();
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
        assertThat(event.getSeverity()).isEqualTo(Level.ERROR);
        assertThat(event.getErrorMessage()).isEqualTo("empty cart");
    }

    @Test
    void defaultsEventNameToMethodName() throws Throwable {
        givenMethod("defaultNamed");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.publishBusinessEvent(joinPoint);

        assertThat(sink.last().getName()).isEqualTo("defaultNamed");
        assertThat(sink.last().getMetadata()).doesNotContainKeys("arguments", "result");
    }

    @Test
    void notAnnotatedMethodProceedsWithoutEvent() throws Throwable {
        givenMethod("notAnnotated");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.publishBusinessEvent(joinPoint);

        assertThat(sink.getEvents()).isEmpty();
    }
}
