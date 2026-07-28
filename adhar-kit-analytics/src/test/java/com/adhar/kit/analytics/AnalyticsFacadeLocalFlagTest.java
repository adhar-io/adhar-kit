package com.adhar.kit.analytics;

import com.adhar.kit.analytics.client.DecideResult;
import com.adhar.kit.analytics.client.PostHogClient;
import com.adhar.kit.analytics.config.AnalyticsProperties;
import com.adhar.kit.analytics.consent.ConsentGateway;
import com.adhar.kit.analytics.consent.InMemoryConsentStore;
import com.adhar.kit.analytics.flag.FlagDefinition;
import com.adhar.kit.analytics.flag.LocalFlagEvaluator;
import com.adhar.kit.analytics.pii.PiiScrubber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the AnalyticsFacade local-flag-evaluation path with fallback to the
 * {@code /decide}-backed TTL cache.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsFacade local flag evaluation Tests")
class AnalyticsFacadeLocalFlagTest {

    @Mock
    private PostHogClient client;

    private AnalyticsFacade facade;
    private LocalFlagEvaluator evaluator;

    @BeforeEach
    void setUp() {
        facade = new AnalyticsFacade(client, false, 100, Duration.ofSeconds(10), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, Duration.ofSeconds(30), Clock.systemUTC(),
                new ConsentGateway(new InMemoryConsentStore()), new PiiScrubber(Set.of(), false));
        evaluator = new LocalFlagEvaluator();
        facade.setLocalFlagEvaluator(evaluator);
    }

    @AfterEach
    void tearDown() {
        if (facade != null && facade.isAvailable()) {
            facade.shutdown();
        }
    }

    @Test
    @DisplayName("isFeatureEnabled(props) decides locally without calling /decide")
    void localEvaluationSkipsDecide() {
        evaluator.addDefinition(new FlagDefinition("beta", true,
                List.of(new FlagDefinition.Group(
                        List.of(new FlagDefinition.Condition("plan", "premium", "exact")), 100, null)),
                List.of()));

        assertTrue(facade.isFeatureEnabled("u1", "beta", Map.of("plan", "premium")));
        assertFalse(facade.isFeatureEnabled("u1", "beta", Map.of("plan", "free")));
        verify(client, never()).decide(anyString());
    }

    @Test
    @DisplayName("falls back to /decide when the flag cannot be evaluated locally (missing property)")
    void fallsBackToDecideOnInconclusive() {
        evaluator.addDefinition(new FlagDefinition("beta", true,
                List.of(new FlagDefinition.Group(
                        List.of(new FlagDefinition.Condition("plan", "premium", "exact")), 100, null)),
                List.of()));
        when(client.decide("u1")).thenReturn(new DecideResult(Map.of("beta", true)));

        // No 'plan' property supplied -> local evaluation inconclusive -> server path.
        assertTrue(facade.isFeatureEnabled("u1", "beta", Map.of()));
        verify(client, times(1)).decide("u1");
    }

    @Test
    @DisplayName("falls back to /decide for a flag with no local definition")
    void fallsBackForUnknownFlag() {
        when(client.decide("u1")).thenReturn(new DecideResult(Map.of("other", true)));
        assertTrue(facade.isFeatureEnabled("u1", "other", Map.of()));
        verify(client, times(1)).decide("u1");
    }

    @Test
    @DisplayName("getFeatureFlag(props) returns the local variant without calling /decide")
    void getFeatureFlagLocalVariant() {
        evaluator.addDefinition(new FlagDefinition("exp", true,
                List.of(new FlagDefinition.Group(List.of(), 100, "variant-a")),
                List.of(new FlagDefinition.Variant("variant-a", 50), new FlagDefinition.Variant("variant-b", 50))));

        assertEquals("variant-a", facade.getFeatureFlag("u1", "exp", Map.of()));
        verify(client, never()).decide(anyString());
    }

    @Test
    @DisplayName("getFeatureFlag(props) returns null for a locally-disabled flag")
    void getFeatureFlagLocalDisabled() {
        evaluator.addDefinition(new FlagDefinition("off", false, List.of(), List.of()));
        assertNull(facade.getFeatureFlag("u1", "off", Map.of()));
        verify(client, never()).decide(anyString());
    }

    @Test
    @DisplayName("without a local evaluator set, the property overloads use the /decide path")
    void noEvaluatorUsesDecide() {
        facade.setLocalFlagEvaluator(null);
        when(client.decide("u1")).thenReturn(new DecideResult(Map.of("beta", true)));

        assertTrue(facade.isFeatureEnabled("u1", "beta", Map.of("plan", "premium")));
        assertEquals("true", facade.getFeatureFlag("u1", "beta", Map.of()));
        verify(client, atLeastOnce()).decide("u1");
    }

    @Test
    @DisplayName("getLocalFlagEvaluator exposes the configured evaluator")
    void exposesEvaluator() {
        assertSame(evaluator, facade.getLocalFlagEvaluator());
    }
}
