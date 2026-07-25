package com.adhar.kit.analytics.aspect;

import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.analytics.annotation.FeatureFlag;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureFlagAspect Tests")
class FeatureFlagAspectTest {

    @Mock
    private AnalyticsFacade analytics;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private FeatureFlagAspect aspect;

    static class Target {
        public String gated(String userId) {
            return "new-feature-result";
        }

        public String legacy(String userId) {
            return "legacy-result";
        }

        private String privateFallback(String userId) {
            return "private-fallback-result";
        }
    }

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        aspect = new FeatureFlagAspect(analytics);
        Method method = Target.class.getMethod("gated", String.class);
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.getMethod()).thenReturn(method);
        lenient().when(signature.getParameterNames()).thenReturn(new String[]{"userId"});
        lenient().when(signature.getParameterTypes()).thenReturn(new Class<?>[]{String.class});
        lenient().when(joinPoint.getArgs()).thenReturn(new Object[]{"user-1"});
        lenient().when(joinPoint.getTarget()).thenReturn(new Target());
    }

    private FeatureFlag annotation(Map<String, Object> overrides) {
        return new FeatureFlag() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return FeatureFlag.class;
            }

            @Override
            public String flag() {
                return (String) overrides.getOrDefault("flag", "my-flag");
            }

            @Override
            public String userIdParam() {
                return (String) overrides.getOrDefault("userIdParam", "userId");
            }

            @Override
            public boolean trackExposure() {
                return (boolean) overrides.getOrDefault("trackExposure", true);
            }

            @Override
            public boolean requireEnabled() {
                return (boolean) overrides.getOrDefault("requireEnabled", false);
            }

            @Override
            public String fallbackMethod() {
                return (String) overrides.getOrDefault("fallbackMethod", "");
            }
        };
    }

    @Test
    @DisplayName("proceeds without checking flags when analytics is unavailable")
    void proceedsWhenUnavailable() throws Throwable {
        when(analytics.isAvailable()).thenReturn(false);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.processFeatureFlag(joinPoint, annotation(Map.of()));

        assertEquals("ok", result);
        verify(analytics, never()).isFeatureEnabled(any(), any());
    }

    @Test
    @DisplayName("proceeds with original method when flag is enabled, and tracks exposure")
    void proceedsWhenFlagEnabled() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.isFeatureEnabled("user-1", "my-flag")).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("new-feature-result");

        Object result = aspect.processFeatureFlag(joinPoint, annotation(Map.of()));

        assertEquals("new-feature-result", result);
        verify(analytics).track(eq("user-1"), eq("$feature_flag_called"), any());
    }

    @Test
    @DisplayName("proceeds with original method when flag disabled and requireEnabled=false")
    void proceedsWhenFlagDisabledNotRequired() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.isFeatureEnabled("user-1", "my-flag")).thenReturn(false);
        when(joinPoint.proceed()).thenReturn("original-result");

        Object result = aspect.processFeatureFlag(joinPoint, annotation(Map.of("requireEnabled", false)));

        assertEquals("original-result", result);
    }

    @Test
    @DisplayName("returns a default value when flag disabled, requireEnabled=true, and no fallback method")
    void returnsDefaultWhenRequiredAndDisabled() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.isFeatureEnabled("user-1", "my-flag")).thenReturn(false);

        Object result = aspect.processFeatureFlag(joinPoint, annotation(Map.of("requireEnabled", true)));

        assertNull(result);
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("invokes the fallback method when flag disabled, requireEnabled=true, and a fallback is configured")
    void invokesFallbackMethod() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.isFeatureEnabled("user-1", "my-flag")).thenReturn(false);

        Object result = aspect.processFeatureFlag(joinPoint,
                annotation(Map.of("requireEnabled", true, "fallbackMethod", "legacy")));

        assertEquals("legacy-result", result);
    }

    @Test
    @DisplayName("falls back to a private declared method when no public one matches")
    void invokesPrivateFallbackMethod() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.isFeatureEnabled("user-1", "my-flag")).thenReturn(false);

        Object result = aspect.processFeatureFlag(joinPoint,
                annotation(Map.of("requireEnabled", true, "fallbackMethod", "privateFallback")));

        assertEquals("private-fallback-result", result);
    }

    @Test
    @DisplayName("returns default when the configured fallback method does not exist")
    void returnsDefaultWhenFallbackMissing() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.isFeatureEnabled("user-1", "my-flag")).thenReturn(false);

        Object result = aspect.processFeatureFlag(joinPoint,
                annotation(Map.of("requireEnabled", true, "fallbackMethod", "doesNotExist")));

        assertNull(result);
    }

    @Test
    @DisplayName("does not track exposure when trackExposure=false")
    void skipsExposureTracking() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(analytics.isFeatureEnabled("user-1", "my-flag")).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.processFeatureFlag(joinPoint, annotation(Map.of("trackExposure", false)));

        verify(analytics, never()).track(any(), any(), any());
    }

    @Test
    @DisplayName("proceeds without checking flags when the user id parameter is missing")
    void proceedsWhenUserIdMissing() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.processFeatureFlag(joinPoint, annotation(Map.of("userIdParam", "nonexistent")));

        assertEquals("ok", result);
        verify(analytics, never()).isFeatureEnabled(any(), any());
    }
}
