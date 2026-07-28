package com.adhar.kit.test.junit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link AdharKitDynamicPropertyConfiguration} - verifies the {@link DynamicPropertyRegistrar}
 * bean copies every collected connection property into the Spring dynamic property registry, with
 * suppliers that read {@link ContainerConnectionInfo} live.
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@DisplayName("AdharKitDynamicPropertyConfiguration Tests")
class AdharKitDynamicPropertyConfigurationTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ContainerConnectionInfo.getInstance().clear();
    }

    @Test
    @DisplayName("registrar should register a live supplier for each collected property")
    void testRegistrarCopiesProperties() {
        ContainerConnectionInfo.getInstance().put("spring.datasource.url", "jdbc:postgresql://host/db");
        ContainerConnectionInfo.getInstance().put("spring.data.redis.host", "redis-host");

        DynamicPropertyRegistrar registrar = new AdharKitDynamicPropertyConfiguration().adharContainerPropertyRegistrar();
        assertNotNull(registrar);

        Map<String, Supplier<Object>> registered = new HashMap<>();
        DynamicPropertyRegistry registry = registered::put;
        registrar.accept(registry);

        assertEquals(2, registered.size());
        assertEquals("jdbc:postgresql://host/db", registered.get("spring.datasource.url").get());
        assertEquals("redis-host", registered.get("spring.data.redis.host").get());
    }

    @Test
    @DisplayName("registered suppliers should reflect later updates to the holder")
    void testSupplierIsLive() {
        ContainerConnectionInfo.getInstance().put("k", "old");
        DynamicPropertyRegistrar registrar = new AdharKitDynamicPropertyConfiguration().adharContainerPropertyRegistrar();

        Map<String, Supplier<Object>> registered = new HashMap<>();
        registrar.accept((DynamicPropertyRegistry) registered::put);

        ContainerConnectionInfo.getInstance().put("k", "new");
        assertEquals("new", registered.get("k").get());
    }
}
