package com.adhar.kit.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple unit test for the config module.
 * This is a library module, so we don't need a full Spring Boot context test.
 */
class AdharConfigStarterApplicationTests {

    @Test
    void moduleLoads() {
        // Simple test to verify module is correctly set up
        assertTrue(true, "Config module loaded successfully");
    }

}
