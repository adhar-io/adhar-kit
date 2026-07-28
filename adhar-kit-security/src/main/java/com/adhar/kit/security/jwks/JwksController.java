package com.adhar.kit.security.jwks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publishes the public JWKS produced by {@link JwksKeyManager} so downstream
 * services can fetch the signing keys and verify issued tokens.
 *
 * <p>The endpoint path is configurable via {@code adhar.security.jwks.path}
 * (default {@code /.well-known/jwks.json}). Only the public key material is
 * exposed; private key parameters never leave the process.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
@RestController
public class JwksController {

    private final JwksKeyManager keyManager;

    /**
     * Creates the controller.
     *
     * @param keyManager source of the public JWKS
     */
    public JwksController(JwksKeyManager keyManager) {
        this.keyManager = keyManager;
        log.info("JWKS endpoint controller initialized");
    }

    /**
     * Returns the public JWKS as a JSON object.
     *
     * @return the JWKS ({@code {"keys":[...]}})
     */
    @GetMapping(
        path = "${adhar.security.jwks.path:/.well-known/jwks.json}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return keyManager.getPublicJwkSet().toJSONObject();
    }
}
