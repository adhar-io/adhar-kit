package com.adhar.kit.analytics.aspect;

import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.analytics.annotation.TrackGroup;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackGroupAspect Tests")
class TrackGroupAspectTest {

    @Mock
    private AnalyticsFacade analytics;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private TrackGroupAspect aspect;

    @BeforeEach
    void setUp() throws Throwable {
        aspect = new TrackGroupAspect(analytics);
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.getParameterNames()).thenReturn(new String[]{"userId", "groupId", "companyName"});
        lenient().when(joinPoint.getArgs()).thenReturn(new Object[]{"user-1", "group-1", "Acme"});
        lenient().when(joinPoint.proceed()).thenReturn("ok");
    }

    private TrackGroup annotation(Map<String, Object> overrides) {
        return new TrackGroup() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return TrackGroup.class;
            }

            @Override
            public String userIdParam() {
                return (String) overrides.getOrDefault("userIdParam", "userId");
            }

            @Override
            public String groupIdParam() {
                return (String) overrides.getOrDefault("groupIdParam", "groupId");
            }

            @Override
            public String groupType() {
                return (String) overrides.getOrDefault("groupType", "company");
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
            public boolean trackEvent() {
                return (boolean) overrides.getOrDefault("trackEvent", true);
            }

            @Override
            public String eventName() {
                return (String) overrides.getOrDefault("eventName", "User Added to Group");
            }
        };
    }

    @Test
    @DisplayName("proceeds without grouping when analytics is unavailable")
    void proceedsWhenUnavailable() throws Throwable {
        when(analytics.isAvailable()).thenReturn(false);

        Object result = aspect.processTrackGroup(joinPoint, annotation(Map.of()));

        assertEquals("ok", result);
        verify(analytics, never()).group(any(), any(), any());
    }

    @Test
    @DisplayName("groups the user and tracks the membership event with $group_type set")
    void groupsUserAndTracksEvent() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);

        aspect.processTrackGroup(joinPoint, annotation(Map.of()));

        ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analytics).group(eq("user-1"), eq("group-1"), propsCaptor.capture());
        assertEquals("company", propsCaptor.getValue().get("$group_type"));
        assertEquals("Acme", propsCaptor.getValue().get("companyName"));
        assertFalse(propsCaptor.getValue().containsKey("userId"));
        assertFalse(propsCaptor.getValue().containsKey("groupId"));

        verify(analytics).track(eq("user-1"), eq("User Added to Group"), any());
    }

    @Test
    @DisplayName("does not track a membership event when trackEvent=false")
    void skipsEventWhenDisabled() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);

        aspect.processTrackGroup(joinPoint, annotation(Map.of("trackEvent", false)));

        verify(analytics).group(any(), any(), any());
        verify(analytics, never()).track(any(), any(), any());
    }

    @Test
    @DisplayName("only includes explicitly listed properties when properties() is non-empty")
    void includesOnlyListedProperties() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);

        aspect.processTrackGroup(joinPoint, annotation(Map.of("properties", new String[]{"companyName"})));

        ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analytics).group(any(), any(), propsCaptor.capture());
        assertEquals(Set.of("$group_type", "companyName"), propsCaptor.getValue().keySet());
    }

    @Test
    @DisplayName("does not group when either userId or groupId is missing")
    void skipsWhenIdsMissing() throws Throwable {
        when(analytics.isAvailable()).thenReturn(true);

        Object result = aspect.processTrackGroup(joinPoint, annotation(Map.of("groupIdParam", "nonexistent")));

        assertEquals("ok", result);
        verify(analytics, never()).group(any(), any(), any());
    }
}
