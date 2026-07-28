package com.adhar.adharkit.security.jwks;

import com.adhar.kit.security.jwks.JwksController;
import com.adhar.kit.security.jwks.JwksKeyManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JwksController}.
 */
class JwksControllerTest {

    @Test
    void publishesPublicJwks() {
        JwksKeyManager manager = new JwksKeyManager(2048, 1);
        JwksController controller = new JwksController(manager);

        Map<String, Object> body = controller.jwks();

        assertThat(body).containsKey("keys");
        @SuppressWarnings("unchecked")
        List<Object> keys = (List<Object>) body.get("keys");
        assertThat(keys).hasSize(1);
        // Must not expose private key material.
        assertThat(body.toString()).doesNotContain("\"d\"");
    }

    @Test
    void reflectsRotation() {
        JwksKeyManager manager = new JwksKeyManager(2048, 1);
        JwksController controller = new JwksController(manager);

        manager.rotate();
        Map<String, Object> body = controller.jwks();

        @SuppressWarnings("unchecked")
        List<Object> keys = (List<Object>) body.get("keys");
        assertThat(keys).hasSize(2);
    }
}
