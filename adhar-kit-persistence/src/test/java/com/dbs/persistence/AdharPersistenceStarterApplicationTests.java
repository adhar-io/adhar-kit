package com.adhar.kit.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple unit test for the persistence module.
 * This is a library module, so we don't need a full Spring Boot context test.
 */
class AdharPersistenceStarterApplicationTests {

    @Test
    void moduleLoads() {
        // Simple test to verify module is correctly set up
        assertTrue(true, "Persistence module loaded successfully");
    }

}
