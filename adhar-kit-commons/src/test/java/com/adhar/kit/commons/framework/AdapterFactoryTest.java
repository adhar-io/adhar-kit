package com.adhar.kit.commons.framework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdapterFactoryTest {

    @Test
    void createAdapterForDetectedFrameworkReturnsRegisteredAdapter() {
        // Detected framework is SPRING_BOOT in the test classpath; two adapters are registered.
        FrameworkAdapter<String> adapter = AdapterFactory.createAdapter(String.class);
        assertNotNull(adapter);
        assertEquals(Framework.SPRING_BOOT, adapter.getSupportedFramework());
    }

    @Test
    void createAdapterForSpecificFrameworkReturnsMatch() {
        FrameworkAdapter<String> adapter = AdapterFactory.createAdapter(String.class, Framework.SPRING_BOOT);
        assertEquals(Framework.SPRING_BOOT, adapter.getSupportedFramework());
        assertNotNull(adapter.getService());
    }

    @Test
    void createAdapterThrowsWhenNoAdapterForFramework() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AdapterFactory.createAdapter(String.class, Framework.QUARKUS));
        assertTrue(ex.getMessage().contains("QUARKUS"));
    }

    @Test
    void getServiceReturnsServiceInstance() {
        String service = AdapterFactory.getService(String.class);
        assertNotNull(service);
        assertTrue(service.endsWith("spring-service"));
    }

    @Test
    void isAdapterAvailableTrueForDetectedFramework() {
        assertTrue(AdapterFactory.isAdapterAvailable(String.class));
    }
}
