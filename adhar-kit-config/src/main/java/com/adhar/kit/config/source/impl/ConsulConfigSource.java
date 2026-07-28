package com.adhar.kit.config.source.impl;

import com.adhar.kit.config.source.ConfigSource;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * HashiCorp Consul KV configuration source.
 *
 * <p>Reads a whole KV subtree from Consul over HTTP using the JDK
 * {@link java.net.http.HttpClient} (no third-party HTTP dependency). The
 * configured {@code prefix} is fetched recursively ({@code ?recurse=true}); each
 * returned entry carries a Base64-encoded value which is decoded to a string.</p>
 *
 * <p>Keys are namespaced under the prefix in Consul (e.g.
 * {@code config/myapp/database/url}). The prefix is stripped and the remaining
 * path separators ({@code /}) are converted to dots so the example above becomes
 * {@code database.url}.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * ConfigSource consul = new ConsulConfigSource(
 *     "http://localhost:8500",  // url
 *     "config/myapp",           // prefix
 *     null,                      // ACL token (optional)
 *     140,                       // priority
 *     true);                     // refreshable
 * String dbUrl = (String) consul.getProperty("database.url").orElse(null);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ConsulConfigSource implements ConfigSource {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String url;
    private final String prefix;
    private final String token;
    private final int priority;
    private final boolean refreshable;
    private final HttpClient httpClient;

    private volatile Map<String, Object> config = new HashMap<>();

    /**
     * Creates a Consul source with default priority (140) and refresh enabled.
     *
     * @param url Consul agent URL (e.g. {@code http://localhost:8500})
     * @param prefix KV key prefix to load recursively
     */
    public ConsulConfigSource(String url, String prefix) {
        this(url, prefix, null, 140, true);
    }

    /**
     * Creates a fully configured Consul source.
     *
     * @param url Consul agent URL (e.g. {@code http://localhost:8500})
     * @param prefix KV key prefix to load recursively
     * @param token ACL token (nullable)
     * @param priority source priority (higher overrides lower)
     * @param refreshable whether periodic refresh is supported
     */
    public ConsulConfigSource(String url, String prefix, String token, int priority, boolean refreshable) {
        this.url = url;
        this.prefix = prefix == null ? "" : prefix;
        this.token = token;
        this.priority = priority;
        this.refreshable = refreshable;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        loadFromConsul();
    }

    @Override
    public String getType() {
        return "consul";
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isEnabled() {
        return url != null && !url.isBlank();
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
        return loadFromConsul();
    }

    /**
     * Fetches the KV subtree from Consul and rebuilds the property map.
     *
     * @return {@code true} when the subtree was read successfully
     */
    private boolean loadFromConsul() {
        if (!isEnabled()) {
            log.warn("ConsulConfigSource is not configured (url) - skipping load");
            return false;
        }
        try {
            String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            String encodedPrefix = URLEncoder.encode(prefix, StandardCharsets.UTF_8).replace("%2F", "/");
            String requestUrl = base + "/v1/kv/" + encodedPrefix + "?recurse=true";

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET();
            if (token != null && !token.isBlank()) {
                builder.header("X-Consul-Token", token);
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                log.warn("Consul prefix '{}' not found (404) - loading empty configuration", prefix);
                this.config = new HashMap<>();
                return true;
            }
            if (response.statusCode() / 100 != 2) {
                log.error("Consul returned HTTP {} for {}", response.statusCode(), requestUrl);
                return false;
            }

            JsonNode root = MAPPER.readTree(response.body());
            Map<String, Object> loaded = new HashMap<>();
            if (root.isArray()) {
                for (JsonNode entry : root) {
                    String rawKey = entry.path("Key").asText();
                    JsonNode valueNode = entry.path("Value");
                    if (rawKey == null || rawKey.isEmpty() || rawKey.endsWith("/")) {
                        continue; // skip folder markers
                    }
                    String configKey = toConfigKey(rawKey);
                    if (valueNode.isNull() || valueNode.isMissingNode()) {
                        loaded.put(configKey, "");
                        continue;
                    }
                    String decoded = new String(Base64.getDecoder().decode(valueNode.asText()),
                            StandardCharsets.UTF_8);
                    loaded.put(configKey, decoded);
                }
            }

            this.config = loaded;
            log.info("Loaded {} properties from Consul prefix {}", loaded.size(), prefix);
            return true;
        } catch (Exception e) {
            log.error("Failed to load configuration from Consul prefix {}", prefix, e);
            return false;
        }
    }

    /**
     * Strips the prefix from a raw Consul key and converts path separators to dots.
     */
    private String toConfigKey(String rawKey) {
        String stripped = rawKey;
        if (!prefix.isEmpty() && stripped.startsWith(prefix)) {
            stripped = stripped.substring(prefix.length());
        }
        if (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }
        return stripped.replace('/', '.');
    }
}
