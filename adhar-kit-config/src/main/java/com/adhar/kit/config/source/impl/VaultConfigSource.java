package com.adhar.kit.config.source.impl;

import com.adhar.kit.config.source.ConfigSource;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * HashiCorp Vault configuration source (KV secrets engine).
 *
 * <p>Reads secrets from a Vault KV backend over HTTP using the JDK
 * {@link java.net.http.HttpClient} (no third-party HTTP dependency). Token
 * authentication is used via the {@code X-Vault-Token} header, and an optional
 * enterprise namespace via {@code X-Vault-Namespace}.</p>
 *
 * <p>Both KV version 2 (default) and KV version 1 layouts are supported. For KV
 * v2 the configured {@code path} (e.g. {@code secret/myapp}) is automatically
 * rewritten to insert the {@code data} segment ({@code secret/data/myapp}) and
 * the response is read from {@code data.data}; for KV v1 the response is read
 * from {@code data}.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * ConfigSource vault = new VaultConfigSource(
 *     "http://localhost:8200",   // address
 *     "secret/myapp",            // path
 *     null,                       // namespace (enterprise, optional)
 *     "s.myToken",               // token
 *     150,                        // priority
 *     true,                       // refreshable
 *     2);                         // kv version
 * String dbPassword = (String) vault.getProperty("database.password").orElse(null);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class VaultConfigSource implements ConfigSource {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String address;
    private final String path;
    private final String namespace;
    private final String token;
    private final int priority;
    private final boolean refreshable;
    private final int kvVersion;
    private final HttpClient httpClient;

    private volatile Map<String, Object> config = new HashMap<>();

    /**
     * Creates a Vault KV v2 source with default priority (150) and refresh enabled.
     *
     * @param address Vault address (e.g. {@code http://localhost:8200})
     * @param path secret path (e.g. {@code secret/myapp})
     * @param token Vault token used for authentication
     */
    public VaultConfigSource(String address, String path, String token) {
        this(address, path, null, token, 150, true, 2);
    }

    /**
     * Creates a fully configured Vault source.
     *
     * @param address Vault address (e.g. {@code http://localhost:8200})
     * @param path secret path (e.g. {@code secret/myapp})
     * @param namespace enterprise namespace (nullable)
     * @param token Vault token used for authentication
     * @param priority source priority (higher overrides lower)
     * @param refreshable whether periodic refresh is supported
     * @param kvVersion KV secrets engine version (1 or 2)
     */
    public VaultConfigSource(String address, String path, String namespace, String token,
                             int priority, boolean refreshable, int kvVersion) {
        this.address = address;
        this.path = path;
        this.namespace = namespace;
        this.token = token;
        this.priority = priority;
        this.refreshable = refreshable;
        this.kvVersion = kvVersion == 1 ? 1 : 2;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        loadFromVault();
    }

    @Override
    public String getType() {
        return "vault";
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isEnabled() {
        return address != null && !address.isBlank() && token != null && !token.isBlank();
    }

    @Override
    public Map<String, Object> loadConfig() {
        return new HashMap<>(config);
    }

    @Override
    public Optional<Object> getProperty(String key) {
        return Optional.ofNullable(config.get(key));
    }

    @Override
    public boolean supportsRefresh() {
        return refreshable;
    }

    @Override
    public boolean refresh() {
        return loadFromVault();
    }

    /**
     * Fetches the secret data from Vault and rebuilds the property map.
     *
     * @return {@code true} when the secret was read successfully
     */
    private boolean loadFromVault() {
        if (!isEnabled()) {
            log.warn("VaultConfigSource is not fully configured (address/token) - skipping load");
            return false;
        }
        try {
            String url = buildUrl();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("X-Vault-Token", token)
                    .GET();
            if (namespace != null && !namespace.isBlank()) {
                builder.header("X-Vault-Namespace", namespace);
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                log.error("Vault returned HTTP {} for {}", response.statusCode(), url);
                return false;
            }

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode dataNode = kvVersion == 2
                    ? root.path("data").path("data")
                    : root.path("data");

            Map<String, Object> loaded = new HashMap<>();
            if (dataNode.isObject()) {
                flattenJson("", dataNode, loaded);
            } else {
                log.warn("Vault response did not contain a data object at {}", url);
            }

            this.config = loaded;
            log.info("Loaded {} properties from Vault path {}", loaded.size(), path);
            return true;
        } catch (Exception e) {
            log.error("Failed to load configuration from Vault path {}", path, e);
            return false;
        }
    }

    /**
     * Builds the Vault REST URL for the configured path and KV version.
     *
     * @return absolute Vault KV data URL
     */
    private String buildUrl() {
        String base = address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
        String p = path == null ? "" : (path.startsWith("/") ? path.substring(1) : path);
        if (kvVersion == 2) {
            int slash = p.indexOf('/');
            if (slash > 0) {
                p = p.substring(0, slash) + "/data/" + p.substring(slash + 1);
            } else {
                p = p + "/data";
            }
        }
        return base + "/v1/" + p;
    }

    /**
     * Recursively flattens a JSON object into dot-notation keys.
     */
    private void flattenJson(String prefix, JsonNode node, Map<String, Object> target) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isObject()) {
                flattenJson(key, value, target);
            } else {
                target.put(key, value.asText());
            }
        }
    }
}
