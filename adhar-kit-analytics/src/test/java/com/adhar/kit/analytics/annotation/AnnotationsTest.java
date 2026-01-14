package com.adhar.kit.analytics.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for analytics annotations.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("Analytics Annotations Tests")
class AnnotationsTest {

    @Test
    @DisplayName("TrackEvent annotation should have correct defaults")
    void testTrackEventDefaults() throws NoSuchMethodException {
        class TestClass {
            @TrackEvent(event = "Test Event")
            public void testMethod(String userId) {}
        }

        TrackEvent annotation = TestClass.class
            .getMethod("testMethod", String.class)
            .getAnnotation(TrackEvent.class);

        assertNotNull(annotation);
        assertEquals("Test Event", annotation.event());
        assertEquals("userId", annotation.userIdParam());
        assertFalse(annotation.trackOnEntry());
        assertTrue(annotation.trackOnSuccess());
        assertFalse(annotation.trackOnFailure());
        assertTrue(annotation.async());
    }

    @Test
    @DisplayName("TrackPageView annotation should have correct defaults")
    void testTrackPageViewDefaults() throws NoSuchMethodException {
        class TestClass {
            @TrackPageView(page = "/home")
            public void testMethod() {}
        }

        TrackPageView annotation = TestClass.class
            .getMethod("testMethod")
            .getAnnotation(TrackPageView.class);

        assertNotNull(annotation);
        assertEquals("/home", annotation.page());
        assertEquals("session", annotation.userIdFrom());
        assertTrue(annotation.includeReferrer());
        assertTrue(annotation.includeUserAgent());
    }

    @Test
    @DisplayName("IdentifyUser annotation should have correct defaults")
    void testIdentifyUserDefaults() throws NoSuchMethodException {
        class TestClass {
            @IdentifyUser
            public void testMethod(String userId) {}
        }

        IdentifyUser annotation = TestClass.class
            .getMethod("testMethod", String.class)
            .getAnnotation(IdentifyUser.class);

        assertNotNull(annotation);
        assertEquals("userId", annotation.userIdParam());
        assertTrue(annotation.includeObjectFields());
        assertArrayEquals(
            new String[]{"password", "token", "secret"},
            annotation.excludeProperties()
        );
    }

    @Test
    @DisplayName("FeatureFlag annotation should have correct defaults")
    void testFeatureFlagDefaults() throws NoSuchMethodException {
        class TestClass {
            @FeatureFlag(flag = "test-flag")
            public void testMethod(String userId) {}
        }

        FeatureFlag annotation = TestClass.class
            .getMethod("testMethod", String.class)
            .getAnnotation(FeatureFlag.class);

        assertNotNull(annotation);
        assertEquals("test-flag", annotation.flag());
        assertEquals("userId", annotation.userIdParam());
        assertTrue(annotation.trackExposure());
        assertFalse(annotation.requireEnabled());
    }

    @Test
    @DisplayName("TrackGroup annotation should have correct defaults")
    void testTrackGroupDefaults() throws NoSuchMethodException {
        class TestClass {
            @TrackGroup
            public void testMethod(String userId, String groupId) {}
        }

        TrackGroup annotation = TestClass.class
            .getMethod("testMethod", String.class, String.class)
            .getAnnotation(TrackGroup.class);

        assertNotNull(annotation);
        assertEquals("userId", annotation.userIdParam());
        assertEquals("groupId", annotation.groupIdParam());
        assertEquals("company", annotation.groupType());
        assertTrue(annotation.trackEvent());
        assertEquals("User Added to Group", annotation.eventName());
    }

    @Test
    @DisplayName("AliasUser annotation should have correct defaults")
    void testAliasUserDefaults() throws NoSuchMethodException {
        class TestClass {
            @AliasUser
            public void testMethod(String distinctId, String userId) {}
        }

        AliasUser annotation = TestClass.class
            .getMethod("testMethod", String.class, String.class)
            .getAnnotation(AliasUser.class);

        assertNotNull(annotation);
        assertEquals("distinctId", annotation.distinctIdParam());
        assertEquals("userId", annotation.aliasParam());
        assertFalse(annotation.trackEvent());
        assertEquals("User Aliased", annotation.eventName());
    }

    @Test
    @DisplayName("EnableAnalytics annotation should have correct defaults")
    void testEnableAnalyticsDefaults() {
        @EnableAnalytics
        class TestClass {}

        EnableAnalytics annotation = TestClass.class
            .getAnnotation(EnableAnalytics.class);

        assertNotNull(annotation);
        assertEquals("userId", annotation.userIdParam());
        assertEquals("param", annotation.userIdFrom());
        assertTrue(annotation.includeAllMethods());
        assertEquals("", annotation.eventPrefix());
        assertFalse(annotation.trackPageViews());
        assertTrue(annotation.async());
    }

    @Test
    @DisplayName("TrackSession annotation should have correct defaults")
    void testTrackSessionDefaults() throws NoSuchMethodException {
        class TestClass {
            @TrackSession
            public void testMethod(String userId, String sessionId) {}
        }

        TrackSession annotation = TestClass.class
            .getMethod("testMethod", String.class, String.class)
            .getAnnotation(TrackSession.class);

        assertNotNull(annotation);
        assertEquals("userId", annotation.userIdParam());
        assertEquals("sessionId", annotation.sessionIdParam());
        assertEquals("start", annotation.action());
        assertTrue(annotation.trackDuration());
    }

    @Test
    @DisplayName("Annotations should support custom values")
    void testCustomValues() throws NoSuchMethodException {
        class TestClass {
            @TrackEvent(
                event = "Custom Event",
                userIdParam = "customUserId",
                properties = {"prop1", "prop2"},
                trackOnEntry = true,
                async = false
            )
            public void testMethod(String customUserId) {}
        }

        TrackEvent annotation = TestClass.class
            .getMethod("testMethod", String.class)
            .getAnnotation(TrackEvent.class);

        assertEquals("Custom Event", annotation.event());
        assertEquals("customUserId", annotation.userIdParam());
        assertArrayEquals(new String[]{"prop1", "prop2"}, annotation.properties());
        assertTrue(annotation.trackOnEntry());
        assertFalse(annotation.async());
    }
}

