package com.adhar.kit.analytics.pii;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PiiScrubber Tests")
class PiiScrubberTest {

    @Test
    @DisplayName("redacts configured keys regardless of case")
    void redactsConfiguredKeys() {
        PiiScrubber scrubber = new PiiScrubber(List.of("password", "SSN"), false);

        Map<String, Object> result = scrubber.scrub(Map.of(
                "password", "hunter2",
                "Ssn", "123-45-6789",
                "plan", "premium"
        ));

        assertEquals(PiiScrubber.REDACTED, result.get("password"));
        assertEquals(PiiScrubber.REDACTED, result.get("Ssn"));
        assertEquals("premium", result.get("plan"));
    }

    @Test
    @DisplayName("pattern detection redacts emails, SSNs, credit cards and phone numbers")
    void patternDetectionRedactsObviousPii() {
        PiiScrubber scrubber = new PiiScrubber(Set.of(), true);

        Map<String, Object> input = new HashMap<>();
        input.put("contact", "user@example.com");
        input.put("ssn_like", "123-45-6789");
        input.put("card", "4111111111111111");
        input.put("phone", "+1-415-555-2671");
        input.put("plan", "premium");
        input.put("count", 42);

        Map<String, Object> result = scrubber.scrub(input);

        assertEquals(PiiScrubber.REDACTED, result.get("contact"));
        assertEquals(PiiScrubber.REDACTED, result.get("ssn_like"));
        assertEquals(PiiScrubber.REDACTED, result.get("card"));
        assertEquals(PiiScrubber.REDACTED, result.get("phone"));
        assertEquals("premium", result.get("plan"));
        assertEquals(42, result.get("count"));
    }

    @Test
    @DisplayName("pattern detection disabled leaves PII-looking values untouched")
    void patternDetectionDisabled() {
        PiiScrubber scrubber = new PiiScrubber(Set.of(), false);

        Map<String, Object> result = scrubber.scrub(Map.of("contact", "user@example.com"));

        assertEquals("user@example.com", result.get("contact"));
    }

    @Test
    @DisplayName("null and empty inputs yield an empty map without throwing")
    void nullAndEmptyInputs() {
        PiiScrubber scrubber = new PiiScrubber(null, true);

        assertTrue(scrubber.scrub(null).isEmpty());
        assertTrue(scrubber.scrub(Map.of()).isEmpty());
    }

    @Test
    @DisplayName("non-PII-looking numeric-ish strings are left alone")
    void doesNotOverRedactShortStrings() {
        PiiScrubber scrubber = new PiiScrubber(Set.of(), true);

        Map<String, Object> result = scrubber.scrub(Map.of(
                "order_id", "ORD-42",
                "short", "1234"
        ));

        assertEquals("ORD-42", result.get("order_id"));
        assertEquals("1234", result.get("short"));
    }
}
