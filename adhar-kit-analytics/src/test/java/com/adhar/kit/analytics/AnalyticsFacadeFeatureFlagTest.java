package com.adhar.kit.analytics;

import com.adhar.kit.analytics.client.DecideResult;
import com.adhar.kit.analytics.client.PostHogClient;
import com.adhar.kit.analytics.config.AnalyticsProperties;
import com.adhar.kit.analytics.consent.ConsentGateway;
import com.adhar.kit.analytics.consent.InMemoryConsentStore;
import com.adhar.kit.analytics.pii.PiiScrubber;
import com.adhar.kit.analytics.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the TTL-based feature flag cache: repeated checks within the TTL
 * window don't re-call {@code /decide}, but the decision is refreshed once
 * the TTL elapses (using an injectable {@link MutableClock} instead of
 * sleeping).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsFacade feature flag TTL cache Tests")
class AnalyticsFacadeFeatureFlagTest {

    @Mock
    private PostHogClient client;

    private MutableClock clock;
    private AnalyticsFacade facade;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
        facade = new AnalyticsFacade(
                client, false, 100, Duration.ofSeconds(10), 100, AnalyticsProperties.OverflowPolicy.DROP_OLDEST,
                Duration.ofSeconds(30), clock,
                new ConsentGateway(new InMemoryConsentStore()),
                new PiiScrubber(Set.of(), false));
    }

    @Test
    @DisplayName("caches the decision and does not re-call /decide within the TTL window")
    void cachesDecisionWithinTtl() {
        when(client.decide("user-1")).thenReturn(new DecideResult(Map.of("flag-a", true)));

        assertTrue(facade.isFeatureEnabled("user-1", "flag-a"));
        assertTrue(facade.isFeatureEnabled("user-1", "flag-a"));

        verify(client, times(1)).decide("user-1");
    }

    @Test
    @DisplayName("re-fetches from /decide once the TTL has elapsed")
    void reFetchesAfterTtlExpires() {
        when(client.decide("user-1")).thenReturn(new DecideResult(Map.of("flag-a", true)));

        facade.isFeatureEnabled("user-1", "flag-a");
        clock.advance(Duration.ofSeconds(31));
        facade.isFeatureEnabled("user-1", "flag-a");

        verify(client, times(2)).decide("user-1");
    }

    @Test
    @DisplayName("a flag absent from the /decide response is cached as disabled to avoid hammering /decide")
    void unknownFlagCachedAsDisabled() {
        when(client.decide("user-1")).thenReturn(DecideResult.empty());

        assertFalse(facade.isFeatureEnabled("user-1", "missing-flag"));
        assertFalse(facade.isFeatureEnabled("user-1", "missing-flag"));

        verify(client, times(1)).decide("user-1");
    }

    @Test
    @DisplayName("getFeatureFlag() returns the cached variant value")
    void getFeatureFlagReturnsVariant() {
        when(client.decide("user-1")).thenReturn(new DecideResult(Map.of("button-color", "blue")));

        assertEquals("blue", facade.getFeatureFlag("user-1", "button-color"));
        assertEquals("blue", facade.getFeatureFlag("user-1", "button-color"));
        verify(client, times(1)).decide("user-1");
    }

    @Test
    @DisplayName("getFeatureFlag() returns null when the flag is absent")
    void getFeatureFlagReturnsNullWhenAbsent() {
        when(client.decide("user-1")).thenReturn(DecideResult.empty());
        assertNull(facade.getFeatureFlag("user-1", "missing"));
    }

    @Test
    @DisplayName("reloadFeatureFlags() clears the cache, forcing a re-fetch")
    void reloadFeatureFlagsForcesRefetch() {
        when(client.decide("user-1")).thenReturn(new DecideResult(Map.of("flag-a", true)));

        facade.isFeatureEnabled("user-1", "flag-a");
        facade.reloadFeatureFlags();
        facade.isFeatureEnabled("user-1", "flag-a");

        verify(client, times(2)).decide("user-1");
    }

    @Test
    @DisplayName("preloadFeatureFlags() populates the cache for every flag in one /decide call")
    void preloadFeatureFlagsPopulatesCache() {
        when(client.decide("user-1")).thenReturn(new DecideResult(Map.of("flag-a", true, "flag-b", "variant-x")));

        facade.preloadFeatureFlags("user-1");

        assertTrue(facade.isFeatureEnabled("user-1", "flag-a"));
        assertEquals("variant-x", facade.getFeatureFlag("user-1", "flag-b"));
        verify(client, times(1)).decide("user-1");
    }
}
