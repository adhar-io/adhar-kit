package com.adhar.kit.security.service;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/**
 * Validates API keys against the SHA-256 hashes declared in configuration.
 *
 * <p>Keys are never stored in clear text: configuration declares the lowercase hex
 * SHA-256 hash of each key together with the principal name and roles it maps to.
 * Presented keys are hashed and compared using a constant-time comparison
 * ({@link MessageDigest#isEqual(byte[], byte[])}); every configured entry is checked
 * so timing does not reveal which (if any) entry matched.</p>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   security:
 *     api-key:
 *       enabled: true
 *       header-name: X-API-Key
 *       keys:
 *         - key-hash: 2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae
 *           principal: reporting-service
 *           roles:
 *             - SERVICE
 *             - REPORTING
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 * @see com.adhar.kit.security.filter.ApiKeyAuthenticationFilter
 */
@Slf4j
public class ApiKeyService {

    private final AdharSecurityProperties.ApiKeyProperties config;

    /**
     * Resolved identity of a valid API key.
     *
     * @param principal the principal name associated with the key
     * @param roles the roles granted to the key
     */
    public record ApiKeyPrincipal(String principal, Set<String> roles) {}

    /**
     * Creates the service.
     *
     * @param config API-key configuration (hashed keys and identities)
     */
    public ApiKeyService(AdharSecurityProperties.ApiKeyProperties config) {
        this.config = config;
        log.info("API key service initialized with {} configured key(s)", config.getKeys().size());
    }

    /**
     * Authenticates a presented API key.
     *
     * @param rawApiKey the API key value presented by the client
     * @return the identity mapped to the key, or empty if the key is unknown
     */
    public Optional<ApiKeyPrincipal> authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank() || config.getKeys().isEmpty()) {
            return Optional.empty();
        }

        byte[] presentedHash = sha256(rawApiKey);
        ApiKeyPrincipal matched = null;

        // Compare against every configured entry (no early exit) with a
        // constant-time digest comparison to avoid timing side channels.
        for (AdharSecurityProperties.ApiKeyProperties.ApiKeyCredential credential : config.getKeys()) {
            byte[] expectedHash = decodeHex(credential.getKeyHash());
            if (expectedHash == null) {
                continue;
            }
            if (MessageDigest.isEqual(expectedHash, presentedHash) && matched == null) {
                matched = new ApiKeyPrincipal(credential.getPrincipal(), Set.copyOf(credential.getRoles()));
            }
        }

        return Optional.ofNullable(matched);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JCA spec; this cannot happen on a compliant JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private byte[] decodeHex(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        try {
            return HexFormat.of().parseHex(hex.toLowerCase());
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring API key entry with invalid SHA-256 hex hash");
            return null;
        }
    }
}
