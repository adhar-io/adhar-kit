package com.adhar.kit.analytics.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DecideResult Tests")
class DecideResultTest {

    @Test
    @DisplayName("null featureFlags defaults to an empty map")
    void nullDefaultsToEmpty() {
        DecideResult result = new DecideResult(null);
        assertTrue(result.featureFlags().isEmpty());
    }

    @Test
    @DisplayName("empty() factory returns an empty result")
    void emptyFactory() {
        assertTrue(DecideResult.empty().featureFlags().isEmpty());
    }

    @Test
    @DisplayName("preserves provided flags")
    void preservesFlags() {
        DecideResult result = new DecideResult(Map.of("flag-a", true));
        assertEquals(true, result.featureFlags().get("flag-a"));
    }
}
