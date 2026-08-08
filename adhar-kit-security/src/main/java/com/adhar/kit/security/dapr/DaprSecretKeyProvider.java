package com.adhar.kit.security.dapr;

import com.adhar.kit.dapr.DaprFacade;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Resolves JWT signing key material from a Dapr secret store, so the signing
 * secret lives in the platform's secret backend (Vault, Kubernetes secrets,
 * AWS Secrets Manager, ... - whatever the sidecar's component is) instead of
 * application properties.
 *
 * <p>The secret is fetched once and cached for the lifetime of this provider;
 * it is never logged. A missing or unreadable secret fails loudly - silently
 * falling back to a generated or hardcoded key would defeat the point of
 * externalizing it.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class DaprSecretKeyProvider {

    private final DaprFacade daprFacade;
    private final String secretStoreName;
    private final String secretName;

    private volatile String cachedSecret;

    /**
     * Creates the provider.
     *
     * @param daprFacade      the Dapr facade used for secret reads
     * @param secretStoreName the Dapr secret store component name
     * @param secretName      the name of the secret holding the JWT signing key
     */
    public DaprSecretKeyProvider(DaprFacade daprFacade, String secretStoreName, String secretName) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
        this.secretStoreName = Objects.requireNonNull(secretStoreName, "secretStoreName must not be null");
        this.secretName = Objects.requireNonNull(secretName, "secretName must not be null");
    }

    /**
     * Returns the signing secret, fetching it from the Dapr secret store on
     * first use and caching it afterwards.
     *
     * @return the secret value (never null or blank)
     * @throws IllegalStateException when the secret is missing or unreadable
     */
    public String resolve() {
        String secret = cachedSecret;
        if (secret != null) {
            return secret;
        }
        synchronized (this) {
            if (cachedSecret != null) {
                return cachedSecret;
            }
            String fetched;
            try {
                fetched = daprFacade.getSecret(secretStoreName, secretName);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to read JWT signing secret '" + secretName
                        + "' from Dapr secret store '" + secretStoreName + "'", e);
            }
            if (fetched == null || fetched.isBlank()) {
                throw new IllegalStateException("JWT signing secret '" + secretName
                        + "' not found in Dapr secret store '" + secretStoreName
                        + "' - refusing to fall back to a generated key");
            }
            log.info("Resolved JWT signing secret '{}' from Dapr secret store '{}'",
                    secretName, secretStoreName);
            cachedSecret = fetched;
            return fetched;
        }
    }
}
