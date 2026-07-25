package com.adhar.kit.analytics.aspect;

import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.analytics.annotation.TrackEvent;
import com.adhar.kit.analytics.pii.PiiScrubber;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackEventAspect Tests")
class TrackEventAspectTest {

    @Mock
    private AnalyticsFacade analytics;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private TrackEventAspect aspect;

    static class Target {
        public String annotated(String userId, String param1) {
            return "ok";
        }
    }

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        aspect = new TrackEventAspect(analytics);
        Method method = Target.class.getMethod("annotated", String.class, String.class);
        // lenient(): the "unavailable" test short-circuits before these are ever consulted.
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.getMethod()).thenReturn(method);
        lenient().when(signature.getParameterNames()).thenReturn(new String[]{"userId", "param1"});
        lenient().when(joinPoint.getArgs()).thenReturn(new Object[]{"user-1", "value1"});
    }

    private TrackEvent annotation(Map<String, Object> overrides) {
        return new TrackEvent() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return TrackEvent.class;
            }

            @Override
            public String event() {
                return (String) overrides.getOrDefault("event", "Method Called");
            }

            @Override
            public String userIdParam() {
                return (String) overrides.getOrDefault("userIdParam", "userId");
            }

            @Override
            public String[] properties() {
                return (String[]) overrides.getOrDefault("properties", new String[0]);
            }

            @Override
            public String[] excludeProperties() {
                return (String[]) overrides.getOrDefault("excludeProperties", new String[0]);
            }

            @Override
            public boolean trackOnEntry() {
                return (boolean) overrides.getOrDefault("trackOnEntry", false);
            }

            @Override
            public boolean trackOnSuccess() {
                return (boolean) overrides.getOrDefault("trackOnSuccess", true);
            }

            @Override
            public boolean trackOnFailure() {
                return (boolean) overrides.getOrDefault("trackOnFailure", false);
            }

            @Override
            public boolean includeReturnValue() {
                return (boolean) overrides.getOrDefault("includeReturnValue", false);
            }

            @Override
            public String returnValueProperty() {
                return (String) overrides.getOrDefault("returnValueProperty", "result");
            }

            @Override
            public boolean async() {
                return (boolean) overrides.getOrDefault("async", true);
            }
        };
    }

    @Test
    @DisplayName("skips tracking entirely when analytics unavailable but still proceeds")
    void skipsWhenUnavailable() throws Throwable {
        when(analytics.isAvailable()).thenReturn(false);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.processTrackEvent(joinPoint, annotation(Map.of()));

        assertEquals("ok", result);
        verify(analytics, never()).track(any(), any(), any());
    }

    @Test
    @DisplayName("tracks on success with included parameters as properties")
    void tracksOnSuccessWithProperties() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.getPiiScrubber()).thenReturn(new PiiScrubber(Set.of(), false));
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.processTrackEvent(joinPoint, annotation(Map.of("event", "Did Thing")));

        ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analytics).track(eq("user-1"), eq("Did Thing"), propsCaptor.capture());
        assertEquals("value1", propsCaptor.getValue().get("param1"));
        assertFalse(propsCaptor.getValue().containsKey("userId"));
    }

    @Test
    @DisplayName("only includes explicitly listed properties when properties() is non-empty")
    void tracksOnlyIncludedProperties() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.getPiiScrubber()).thenReturn(new PiiScrubber(Set.of(), false));
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.processTrackEvent(joinPoint, annotation(Map.of("properties", new String[]{"param1"})));

        ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analytics).track(any(), any(), propsCaptor.capture());
        assertEquals(Set.of("param1"), propsCaptor.getValue().keySet());
    }

    @Test
    @DisplayName("includes the return value under the configured property name")
    void includesReturnValue() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.getPiiScrubber()).thenReturn(new PiiScrubber(Set.of(), false));
        when(joinPoint.proceed()).thenReturn("computed-result");

        aspect.processTrackEvent(joinPoint, annotation(Map.of("includeReturnValue", true)));

        ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analytics).track(any(), any(), propsCaptor.capture());
        assertEquals("computed-result", propsCaptor.getValue().get("result"));
    }

    @Test
    @DisplayName("tracks on entry before proceeding, in addition to on success")
    void tracksOnEntry() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.getPiiScrubber()).thenReturn(new PiiScrubber(Set.of(), false));
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.processTrackEvent(joinPoint, annotation(Map.of("trackOnEntry", true)));

        verify(analytics, times(2)).track(any(), any(), any());
    }

    @Test
    @DisplayName("tracks on failure and rethrows the original exception")
    void tracksOnFailure() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.getPiiScrubber()).thenReturn(new PiiScrubber(Set.of(), false));
        RuntimeException boom = new RuntimeException("boom");
        when(joinPoint.proceed()).thenThrow(boom);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> aspect.processTrackEvent(joinPoint,
                        annotation(Map.of("trackOnFailure", true, "trackOnSuccess", false))));

        assertSame(boom, thrown);
        ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analytics).track(any(), any(), propsCaptor.capture());
        assertEquals("RuntimeException", propsCaptor.getValue().get("error"));
        assertEquals("boom", propsCaptor.getValue().get("error_message"));
    }

    @Test
    @DisplayName("substitutes {param} placeholders in the event name")
    void substitutesEventNamePlaceholders() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.getPiiScrubber()).thenReturn(new PiiScrubber(Set.of(), false));
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.processTrackEvent(joinPoint, annotation(Map.of("event", "Called with {param1}")));

        verify(analytics).track(any(), eq("Called with value1"), any());
    }

    @Test
    @DisplayName("proceeds without tracking when the configured user id parameter is missing")
    void proceedsWhenUserIdMissing() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.processTrackEvent(joinPoint, annotation(Map.of("userIdParam", "nonexistent")));

        assertEquals("ok", result);
        verify(analytics, never()).track(any(), any(), any());
    }

    @Test
    @DisplayName("scrubs properties through the facade's PiiScrubber before tracking")
    void scrubsPiiBeforeTracking() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.getPiiScrubber()).thenReturn(new PiiScrubber(Set.of("param1"), false));
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.processTrackEvent(joinPoint, annotation(Map.of()));

        ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analytics).track(any(), any(), propsCaptor.capture());
        assertEquals(PiiScrubber.REDACTED, propsCaptor.getValue().get("param1"));
    }
}
