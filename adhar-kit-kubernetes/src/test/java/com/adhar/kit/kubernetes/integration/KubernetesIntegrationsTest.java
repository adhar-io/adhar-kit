package com.adhar.kit.kubernetes.integration;

import com.adhar.kit.kubernetes.config.KubernetesProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the framework integration helpers.
 *
 * <p>When enabled, {@code createClient} attempts to build a real Fabric8 client,
 * which is not possible in the unit-test environment; those calls are expected to
 * fail. The disabled path and the availability checks are fully asserted.</p>
 */
class KubernetesIntegrationsTest {

    private KubernetesProperties disabled() {
        KubernetesProperties props = new KubernetesProperties();
        props.setEnabled(false);
        return props;
    }

    private KubernetesProperties enabled() {
        KubernetesProperties props = new KubernetesProperties();
        props.setEnabled(true);
        return props;
    }

    @Test
    void springBootCreateClientDisabledReturnsNull() {
        assertNull(SpringBootKubernetesIntegration.createClient(disabled()));
    }

    @Test
    void springBootCreateClientEnabledAttemptsBuild() {
        assertThrows(Throwable.class,
            () -> SpringBootKubernetesIntegration.createClient(enabled()));
    }

    @Test
    void springBootAvailabilityCheck() {
        // spring-boot-starter is on the classpath in this module.
        assertTrue(SpringBootKubernetesIntegration.isSpringBootAvailable());
    }

    @Test
    void quarkusCreateClientDisabledReturnsNull() {
        assertNull(QuarkusKubernetesIntegration.createClient(disabled()));
    }

    @Test
    void quarkusCreateClientEnabledAttemptsBuild() {
        assertThrows(Throwable.class,
            () -> QuarkusKubernetesIntegration.createClient(enabled()));
    }

    @Test
    void quarkusNotAvailable() {
        assertFalse(QuarkusKubernetesIntegration.isQuarkusAvailable());
    }

    @Test
    void micronautCreateClientDisabledReturnsNull() {
        assertNull(MicronautKubernetesIntegration.createClient(disabled()));
    }

    @Test
    void micronautCreateClientEnabledAttemptsBuild() {
        assertThrows(Throwable.class,
            () -> MicronautKubernetesIntegration.createClient(enabled()));
    }

    @Test
    void micronautNotAvailable() {
        assertFalse(MicronautKubernetesIntegration.isMicronautAvailable());
    }
}
