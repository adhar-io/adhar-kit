package com.adhar.kit.kubernetes.util;

import com.adhar.kit.kubernetes.model.PodInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the environment-driven helpers in {@link KubernetesUtils}.
 *
 * <p>The downward-API environment variables are provided to the test JVM by the
 * Surefire configuration of this module.</p>
 */
class KubernetesUtilsEnvTest {

    @Test
    void podIdentityFromEnvironment() {
        assertEquals("test-pod", KubernetesUtils.getPodName());
        assertEquals("test-namespace", KubernetesUtils.getNamespace());
        assertEquals("10.1.2.3", KubernetesUtils.getPodIp());
        assertEquals("test-node", KubernetesUtils.getNodeName());
        assertEquals("test-sa", KubernetesUtils.getServiceAccountName());
    }

    @Test
    void isRunningInKubernetesTrueWhenServiceEnvPresent() {
        assertTrue(KubernetesUtils.isRunningInKubernetes());
    }

    @Test
    void kubernetesApiServerBuiltFromEnv() {
        assertEquals("https://10.96.0.1:443", KubernetesUtils.getKubernetesApiServer());
    }

    @Test
    void getPodInfoFromEnvPopulatesFields() {
        PodInfo info = KubernetesUtils.getPodInfoFromEnv();

        assertEquals("test-pod", info.getName());
        assertEquals("test-namespace", info.getNamespace());
        assertEquals("10.1.2.3", info.getIp());
        assertEquals("test-node", info.getNodeName());
        assertEquals("test-sa", info.getServiceAccount());
    }

    @Test
    void isValidNamespaceDelegatesToResourceNameRules() {
        assertTrue(KubernetesUtils.isValidNamespace("production"));
        assertFalse(KubernetesUtils.isValidNamespace("Invalid_NS"));
    }

    @Test
    void sanitizeResourceNameDefaultsForBlank() {
        assertEquals("unknown", KubernetesUtils.sanitizeResourceName(null));
        assertEquals("unknown", KubernetesUtils.sanitizeResourceName(""));
    }

    @Test
    void sanitizeResourceNamePrefixesWhenAllInvalid() {
        // All characters are stripped, so a leading 'x' is added.
        assertEquals("x", KubernetesUtils.sanitizeResourceName("@@@"));
    }
}
