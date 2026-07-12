package com.adhar.kit.kubernetes.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the hand-written logic of {@link NamespaceInfo}.
 */
class NamespaceInfoTest {

    @Test
    void isActiveTrueIgnoringCase() {
        assertTrue(NamespaceInfo.builder().status("Active").build().isActive());
        assertTrue(NamespaceInfo.builder().status("active").build().isActive());
    }

    @Test
    void isActiveFalseForOtherStatuses() {
        assertFalse(NamespaceInfo.builder().status("Terminating").build().isActive());
        assertFalse(NamespaceInfo.builder().build().isActive());
    }
}
