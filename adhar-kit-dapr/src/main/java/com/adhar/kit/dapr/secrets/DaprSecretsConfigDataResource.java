package com.adhar.kit.dapr.secrets;

import com.adhar.kit.dapr.DaprConnectionDetails;
import org.springframework.boot.context.config.ConfigDataResource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Secrets to load from one Dapr secret store: named ones, or every secret when {@code names} is
 * empty.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class DaprSecretsConfigDataResource extends ConfigDataResource {

    private final String store;
    private final List<String> names;
    private final String prefix;
    private final DaprConnectionDetails connection;
    private final Duration waitTimeout;
    private final Map<String, Map<String, String>> memorySecrets;

    public DaprSecretsConfigDataResource(String store, List<String> names, String prefix, boolean optional) {
        this(store, names, prefix, optional, null, Duration.ofSeconds(30), Map.of());
    }

    /**
     * @param store         the secret store
     * @param names         the secrets to load, or empty for every secret
     * @param prefix        prefix of the resulting property names
     * @param optional      whether a failed read is ignored
     * @param connection    the sidecar connection resolved from the environment
     * @param waitTimeout   how long to wait for the sidecar
     * @param memorySecrets the memory-mode secrets ({@code adhar.dapr.memory.secrets})
     */
    public DaprSecretsConfigDataResource(String store, List<String> names, String prefix, boolean optional,
                                         DaprConnectionDetails connection, Duration waitTimeout,
                                         Map<String, Map<String, String>> memorySecrets) {
        super(optional);
        this.store = store;
        this.names = List.copyOf(names);
        this.prefix = prefix == null ? "" : prefix;
        this.connection = connection;
        this.waitTimeout = waitTimeout;
        this.memorySecrets = memorySecrets == null ? Map.of() : Map.copyOf(memorySecrets);
    }

    public DaprConnectionDetails getConnection() {
        return connection != null ? connection : DaprConnectionDetails.fromEnvironment();
    }

    public Duration getWaitTimeout() {
        return waitTimeout;
    }

    public Map<String, Map<String, String>> getMemorySecrets() {
        return memorySecrets;
    }

    public String getStore() {
        return store;
    }

    public List<String> getNames() {
        return names;
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DaprSecretsConfigDataResource other && store.equals(other.store)
                && names.equals(other.names) && prefix.equals(other.prefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(store, names, prefix);
    }

    @Override
    public String toString() {
        return "dapr-secrets:" + store + (names.isEmpty() ? "" : "/" + String.join(",", names));
    }
}
