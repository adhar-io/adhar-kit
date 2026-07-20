package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.LogOperation;
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
 * Unit tests for {@link LogOperationAspect}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogOperationAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private RecordingAppLogEventSink sink;
    private LogOperationAspect aspect;

    @SuppressWarnings("unused")
    static class SampleTarget {
        @LogOperation(value = "order.fulfil", category = "order", includeArgs = true,
                includeResult = true, tags = {"critical"})
        public String fulfil(String orderId, int quantity) {
            return "ok";
        }

        @LogOperation
        public void defaultNamed() {
        }

        public void notAnnotated() {
        }
    }

    @LogOperation(category = "batch")
    @SuppressWarnings("unused")
    static class ClassAnnotated {
        public void run() {
        }
    }

    @BeforeEach
    void setUp() {
        AdharLoggingProperties properties = new AdharLoggingProperties();
        LogDataMasker masker = new LogDataMasker(properties.getMasking());
        sink = new RecordingAppLogEventSink();
        aspect = new LogOperationAspect(
                new AppLogEventPublisher(properties, masker, List.of(sink)), masker);
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    private void givenMethod(Class<?> clazz, String name, Class<?>... params) throws Exception {
        Method method = clazz.getDeclaredMethod(name, params);
        when(signature.getMethod()).thenReturn(method);
    }

    @Test
    void publishesOperationEventWithArgsAndResult() throws Throwable {
        givenMethod(SampleTarget.class, "fulfil", String.class, int.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"o-1 password=x", 2});
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.trackOperation(joinPoint);

        assertThat(result).isEqualTo("ok");
        AppLogEvent event = sink.last();
        assertThat(event.getType()).isEqualTo(AppLogEventType.OPERATION);
        assertThat(event.getName()).isEqualTo("order.fulfil");
        assertThat(event.getCategory()).isEqualTo("order");
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.SUCCESS);
        assertThat(event.getDurationMs()).isNotNull();
        assertThat(event.getTags()).contains("critical");
        @SuppressWarnings("unchecked")
        List<Object> args = (List<Object>) event.getMetadata().get("arguments");
        assertThat(args).hasSize(2);
        assertThat((String) args.get(0)).doesNotContain("password=x");
        assertThat(args.get(1)).isEqualTo(2);
        assertThat(event.getMetadata().get("result")).isEqualTo("ok");
    }

    @Test
    void failurePublishesFailureEventAndRethrows() throws Throwable {
        givenMethod(SampleTarget.class, "fulfil", String.class, int.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("out of stock"));

        assertThatThrownBy(() -> aspect.trackOperation(joinPoint))
                .isInstanceOf(IllegalStateException.class);

        AppLogEvent event = sink.last();
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
        assertThat(event.getSeverity()).isEqualTo(Level.ERROR);
        assertThat(event.getErrorMessage()).isEqualTo("out of stock");
    }

    @Test
    void defaultsOperationNameToClassAndMethod() throws Throwable {
        givenMethod(SampleTarget.class, "defaultNamed");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.trackOperation(joinPoint);

        assertThat(sink.last().getName()).isEqualTo("SampleTarget.defaultNamed");
    }

    @Test
    void classLevelAnnotationIsUsed() throws Throwable {
        givenMethod(ClassAnnotated.class, "run");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.trackOperation(joinPoint);

        assertThat(sink.last().getCategory()).isEqualTo("batch");
        assertThat(sink.last().getName()).isEqualTo("ClassAnnotated.run");
    }

    @Test
    void notAnnotatedMethodProceedsWithoutEvent() throws Throwable {
        givenMethod(SampleTarget.class, "notAnnotated");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.trackOperation(joinPoint);

        assertThat(sink.getEvents()).isEmpty();
    }

    @Test
    void complexArgumentsAreReducedToTypeName() throws Throwable {
        givenMethod(SampleTarget.class, "fulfil", String.class, int.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{new StringBuilder("complex"), null});
        when(joinPoint.proceed()).thenReturn(new SampleTarget());

        aspect.trackOperation(joinPoint);

        @SuppressWarnings("unchecked")
        List<Object> args = (List<Object>) sink.last().getMetadata().get("arguments");
        assertThat(args.get(0)).isEqualTo("[StringBuilder]");
        assertThat(args.get(1)).isNull();
        assertThat(sink.last().getMetadata().get("result")).isEqualTo("[SampleTarget]");
    }
}
