package com.adhar.kit.dapr;

import java.util.function.Function;

/**
 * The component names a service uses when it does not name one. The platform provides components
 * with exactly these names in every environment namespace, so services never have to configure
 * them; {@code ADHAR_DAPR_PUBSUB}, {@code ADHAR_DAPR_STATESTORE}, {@code ADHAR_DAPR_SECRETSTORE},
 * {@code ADHAR_DAPR_CONFIGSTORE} and {@code ADHAR_DAPR_LOCKSTORE} (or the matching
 * {@code adhar.dapr.*} properties) override them.
 *
 * @param pubsub      pub/sub component ({@code pubsub})
 * @param stateStore  state store ({@code statestore})
 * @param secretStore secret store ({@code secretstore})
 * @param configStore configuration store ({@code configstore})
 * @param lockStore   lock store ({@code lockstore})
 * @param crypto      cryptography component ({@code crypto})
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record DaprComponents(String pubsub, String stateStore, String secretStore, String configStore,
                             String lockStore, String crypto) {

    public static final String DEFAULT_PUBSUB = "pubsub";
    public static final String DEFAULT_STATE_STORE = "statestore";
    public static final String DEFAULT_SECRET_STORE = "secretstore";
    public static final String DEFAULT_CONFIG_STORE = "configstore";
    public static final String DEFAULT_LOCK_STORE = "lockstore";
    public static final String DEFAULT_CRYPTO = "crypto";

    public DaprComponents {
        pubsub = orDefault(pubsub, DEFAULT_PUBSUB);
        stateStore = orDefault(stateStore, DEFAULT_STATE_STORE);
        secretStore = orDefault(secretStore, DEFAULT_SECRET_STORE);
        configStore = orDefault(configStore, DEFAULT_CONFIG_STORE);
        lockStore = orDefault(lockStore, DEFAULT_LOCK_STORE);
        crypto = orDefault(crypto, DEFAULT_CRYPTO);
    }

    /** The platform defaults. */
    public static DaprComponents defaults() {
        return new DaprComponents(null, null, null, null, null, null);
    }

    /** Defaults overridden by system properties and environment variables only (no Spring). */
    public static DaprComponents fromEnvironment() {
        return resolve(key -> {
            String value = System.getProperty(key);
            return value != null ? value : System.getenv(key);
        });
    }

    /**
     * Resolves each name from its {@code adhar.dapr.*} property, then its environment variable.
     *
     * @param lookup returns the value for a key, or {@code null}
     * @return the component names
     */
    public static DaprComponents resolve(Function<String, String> lookup) {
        return new DaprComponents(
                first(lookup, "adhar.dapr.pubsub", "ADHAR_DAPR_PUBSUB"),
                first(lookup, "adhar.dapr.state-store", "ADHAR_DAPR_STATESTORE"),
                first(lookup, "adhar.dapr.secret-store", "ADHAR_DAPR_SECRETSTORE"),
                first(lookup, "adhar.dapr.config-store", "adhar.dapr.configuration-store", "ADHAR_DAPR_CONFIGSTORE"),
                first(lookup, "adhar.dapr.lock-store", "ADHAR_DAPR_LOCKSTORE"),
                first(lookup, "adhar.dapr.crypto.default-component", "ADHAR_DAPR_CRYPTO"));
    }

    private static String first(Function<String, String> lookup, String... keys) {
        for (String key : keys) {
            String value = lookup.apply(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
