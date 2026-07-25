package com.adhar.kit.analytics;

import com.adhar.kit.analytics.client.CaptureEvent;
import com.adhar.kit.analytics.client.PostHogClient;
import com.adhar.kit.analytics.config.AnalyticsProperties;
import com.adhar.kit.analytics.consent.ConsentGateway;
import com.adhar.kit.analytics.consent.InMemoryConsentStore;
import com.adhar.kit.analytics.pii.PiiScrubber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link AnalyticsFacade} builds a correctly-shaped
 * {@link CaptureEvent} for each operation (track/identify/alias/group) and
 * routes it through {@link PostHogClient#capture(CaptureEvent)} - fixing the
 * legacy bug where every operation posted the same mis-shaped payload to
 * {@code /capture/} regardless of intent. Uses the package-private
 * constructor to inject a mock client, so no singleton state is touched and
 * no network call ever happens.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsFacade routing / payload shape Tests")
class AnalyticsFacadeRoutingTest {

    @Mock
    private PostHogClient client;

    private AnalyticsFacade facade;

    @BeforeEach
    void setUp() {
        facade = new AnalyticsFacade(
                client,
                false, // synchronous fallback so capture() is invoked immediately, deterministically
                100, Duration.ofSeconds(10), 100, AnalyticsProperties.OverflowPolicy.DROP_OLDEST,
                Duration.ofSeconds(60), Clock.systemUTC(),
                new ConsentGateway(new InMemoryConsentStore()),
                new PiiScrubber(Set.of(), false));
    }

    @Test
    @DisplayName("track() sends a capture event with the given event name and properties")
    void trackSendsCaptureEvent() {
        facade.track("user-1", "Signed Up", Map.of("plan", "pro"));

        CaptureEvent event = captureSent();
        assertEquals("Signed Up", event.event());
        assertEquals("user-1", event.distinctId());
        assertEquals("pro", event.properties().get("plan"));
    }

    @Test
    @DisplayName("identify() builds a $identify event with properties nested under $set")
    void identifyBuildsIdentifyEvent() {
        facade.identify("user-1", Map.of("email", "a@b.com"));

        CaptureEvent event = captureSent();
        assertEquals("$identify", event.event());
        assertEquals("user-1", event.distinctId());
        @SuppressWarnings("unchecked")
        Map<String, Object> set = (Map<String, Object>) event.properties().get("$set");
        assertEquals("a@b.com", set.get("email"));
    }

    @Test
    @DisplayName("alias() builds a $create_alias event carrying distinctId/alias")
    void aliasBuildsCreateAliasEvent() {
        facade.alias("anon-1", "user-1");

        CaptureEvent event = captureSent();
        assertEquals("$create_alias", event.event());
        assertEquals("anon-1", event.distinctId());
        assertEquals("user-1", event.properties().get("alias"));
    }

    @Test
    @DisplayName("group() builds a $groupidentify event with $group_type and $group_key")
    void groupBuildsGroupIdentifyEvent() {
        facade.group("user-1", "company-1", Map.of("name", "Acme", "$group_type", "organization"));

        CaptureEvent event = captureSent();
        assertEquals("$groupidentify", event.event());
        assertEquals("user-1", event.distinctId());
        assertEquals("organization", event.properties().get("$group_type"));
        assertEquals("company-1", event.properties().get("$group_key"));
        assertEquals("Acme", event.properties().get("name"));
    }

    @Test
    @DisplayName("group() defaults $group_type to \"company\" when not provided")
    void groupDefaultsGroupType() {
        facade.group("user-1", "company-1", Map.of("name", "Acme"));

        assertEquals("company", captureSent().properties().get("$group_type"));
    }

    @Test
    @DisplayName("trackPageView() sends a $pageview capture event with $current_url")
    void trackPageViewSendsPageviewEvent() {
        facade.trackPageView("user-1", "/dashboard");

        CaptureEvent event = captureSent();
        assertEquals("$pageview", event.event());
        assertEquals("/dashboard", event.properties().get("$current_url"));
    }

    @Test
    @DisplayName("null user id / event name / alias / group id are rejected without contacting the client")
    void nullsAreRejected() {
        facade.track(null, "Event");
        facade.track("user-1", null);
        facade.identify(null, Map.of());
        facade.alias(null, "user-1");
        facade.alias("user-1", null);
        facade.group(null, "g1", Map.of());
        facade.group("user-1", null, Map.of());

        verifyNoInteractions(client);
    }

    private CaptureEvent captureSent() {
        ArgumentCaptor<CaptureEvent> captor = ArgumentCaptor.forClass(CaptureEvent.class);
        verify(client).capture(captor.capture());
        return captor.getValue();
    }
}
