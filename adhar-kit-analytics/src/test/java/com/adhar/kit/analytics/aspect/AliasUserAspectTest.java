package com.adhar.kit.analytics.aspect;

import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.analytics.annotation.AliasUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AliasUserAspect Tests")
class AliasUserAspectTest {

    @Mock
    private AnalyticsFacade analytics;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private AliasUserAspect aspect;

    @BeforeEach
    void setUp() throws Throwable {
        aspect = new AliasUserAspect(analytics);
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.getParameterNames()).thenReturn(new String[]{"distinctId", "userId"});
        lenient().when(joinPoint.getArgs()).thenReturn(new Object[]{"anon-1", "user-1"});
        lenient().when(joinPoint.proceed()).thenReturn("ok");
    }

    private AliasUser annotation(Map<String, Object> overrides) {
        return new AliasUser() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return AliasUser.class;
            }

            @Override
            public String distinctIdParam() {
                return (String) overrides.getOrDefault("distinctIdParam", "distinctId");
            }

            @Override
            public String aliasParam() {
                return (String) overrides.getOrDefault("aliasParam", "userId");
            }

            @Override
            public boolean trackEvent() {
                return (boolean) overrides.getOrDefault("trackEvent", false);
            }

            @Override
            public String eventName() {
                return (String) overrides.getOrDefault("eventName", "User Aliased");
            }
        };
    }

    @Test
    @DisplayName("proceeds without aliasing when analytics is unavailable")
    void proceedsWhenUnavailable() throws Throwable {
        when(analytics.isAvailable()).thenReturn(false);

        Object result = aspect.processAliasUser(joinPoint, annotation(Map.of()));

        assertEquals("ok", result);
        verify(analytics, never()).alias(any(), any());
    }

    @Test
    @DisplayName("aliases the distinct id to the alias")
    void aliasesUser() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);

        Object result = aspect.processAliasUser(joinPoint, annotation(Map.of()));

        assertEquals("ok", result);
        verify(analytics).alias("anon-1", "user-1");
        verify(analytics, never()).track(any(), any(), any());
    }

    @Test
    @DisplayName("tracks an aliasing event when trackEvent=true")
    void tracksEventWhenConfigured() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);

        aspect.processAliasUser(joinPoint, annotation(Map.of("trackEvent", true, "eventName", "Converted")));

        verify(analytics).alias("anon-1", "user-1");
        verify(analytics).track(eq("user-1"), eq("Converted"), any());
    }

    @Test
    @DisplayName("does not alias when either distinctId or alias parameter is missing")
    void skipsWhenIdsMissing() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);

        Object result = aspect.processAliasUser(joinPoint, annotation(Map.of("aliasParam", "nonexistent")));

        assertEquals("ok", result);
        verify(analytics, never()).alias(any(), any());
    }
}
