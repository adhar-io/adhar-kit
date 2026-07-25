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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link AnalyticsFacade} consults the {@link ConsentGateway}
 * before sending (per-distinct-id opt-out) and always scrubs properties
 * through the {@link PiiScrubber} on the send path.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsFacade consent / PII governance Tests")
class AnalyticsFacadeConsentPiiTest {

    @Mock
    private PostHogClient client;

    private ConsentGateway consentGateway;
    private AnalyticsFacade facade;

    @BeforeEach
    void setUp() {
        consentGateway = new ConsentGateway(new InMemoryConsentStore(List.of("blocked-user")));
        facade = new AnalyticsFacade(
                client, false, 100, Duration.ofSeconds(10), 100, AnalyticsProperties.OverflowPolicy.DROP_OLDEST,
                Duration.ofSeconds(60), Clock.systemUTC(),
                consentGateway,
                new PiiScrubber(Set.of("password"), true));
    }

    @Test
    @DisplayName("consent gateway blocks track() for an opted-out distinct id")
    void consentBlocksTrack() {
        facade.track("blocked-user", "Event", Map.of("x", 1));
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("consent gateway allows a non-opted-out distinct id")
    void consentAllowsNonOptedOutUsers() {
        facade.track("allowed-user", "Event", Map.of());
        verify(client).capture(any());
    }

    @Test
    @DisplayName("consent gateway also blocks identify/alias/group")
    void consentBlocksIdentifyAliasGroup() {
        facade.identify("blocked-user", Map.of());
        facade.alias("blocked-user", "other");
        facade.group("blocked-user", "g1", Map.of());
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("opting a user back in re-enables sends")
    void optInReEnablesSends() {
        consentGateway.optIn("blocked-user");
        facade.track("blocked-user", "Event", Map.of());
        verify(client).capture(any());
    }

    @Test
    @DisplayName("PII scrubber redacts configured keys and pattern-detected values before send")
    void piiScrubberRedactsPropertiesBeforeSend() {
        facade.track("user-1", "Login", Map.of("password", "secret123", "email", "a@b.com", "plan", "pro"));

        ArgumentCaptor<CaptureEvent> captor = ArgumentCaptor.forClass(CaptureEvent.class);
        verify(client).capture(captor.capture());
        Map<String, Object> properties = captor.getValue().properties();
        assertEquals(PiiScrubber.REDACTED, properties.get("password"));
        assertEquals(PiiScrubber.REDACTED, properties.get("email"));
        assertEquals("pro", properties.get("plan"));
    }

    @Test
    @DisplayName("PII scrubber also applies to identify() properties")
    void piiScrubberAppliesToIdentify() {
        facade.identify("user-1", Map.of("password", "hunter2"));

        ArgumentCaptor<CaptureEvent> captor = ArgumentCaptor.forClass(CaptureEvent.class);
        verify(client).capture(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> set = (Map<String, Object>) captor.getValue().properties().get("$set");
        assertEquals(PiiScrubber.REDACTED, set.get("password"));
    }

    @Test
    @DisplayName("getPiiScrubber()/getConsentGateway() expose the configured instances")
    void gettersExposeConfiguredInstances() {
        assertNotNull(facade.getPiiScrubber());
        assertSame(consentGateway, facade.getConsentGateway());
    }
}
