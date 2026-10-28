package com.adhar.kit.dapr.secrets;

import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.http.DaprHttpClient;
import com.adhar.kit.dapr.http.DaprHttpResponse;
import com.adhar.kit.dapr.observation.DaprTraceContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads Dapr secrets into the environment before the application context starts, from the
 * sidecar's HTTP API ({@code /v1.0/secrets/<store>/<name>}, or {@code .../bulk}) or, in memory
 * mode, from {@code adhar.dapr.memory.secrets.*}.
 *
 * <p>This runs before any bean exists, so it reads its connection from the environment the
 * injector sets and waits (up to {@code adhar.dapr.sidecar.wait-timeout}) for the sidecar to be
 * ready. An {@code optional:} import that cannot be read contributes nothing; a required one fails
 * startup.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprSecretsConfigDataLoader implements ConfigDataLoader<DaprSecretsConfigDataResource> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ConfigData load(ConfigDataLoaderContext context, DaprSecretsConfigDataResource resource) throws IOException {
        DaprConnectionDetails connection = resource.getConnection();
        Map<String, Map<String, String>> secrets = connection.isMemory()
                ? memorySecrets(resource)
                : sidecarSecrets(connection, resource);
        Map<String, Object> properties = new LinkedHashMap<>();
        secrets.forEach((name, values) -> {
            values.forEach((key, value) -> properties.put(resource.getPrefix() + name + "." + key, value));
            if (values.size() == 1) {
                properties.put(resource.getPrefix() + name, values.values().iterator().next());
            }
        });
        return new ConfigData(List.of(new MapPropertySource("dapr-secrets:" + resource.getStore(), properties)));
    }

    private Map<String, Map<String, String>> sidecarSecrets(DaprConnectionDetails connection,
                                                           DaprSecretsConfigDataResource resource) throws IOException {
        DaprHttpClient http = new DaprHttpClient(connection, Duration.ofSeconds(10), DaprTraceContext.NONE);
        awaitSidecar(http, resource.getWaitTimeout());
        Map<String, Map<String, String>> secrets = new LinkedHashMap<>();
        String store = DaprHttpClient.encodeSegment(resource.getStore());
        if (resource.getNames().isEmpty()) {
            DaprHttpResponse response = read(http, "/v1.0/secrets/" + store + "/bulk", resource);
            secrets.putAll(MAPPER.readValue(response.body(), new TypeReference<Map<String, Map<String, String>>>() { }));
        } else {
            for (String name : resource.getNames()) {
                DaprHttpResponse response = read(http, "/v1.0/secrets/" + store + "/"
                        + DaprHttpClient.encodeSegment(name), resource);
                secrets.put(name, MAPPER.readValue(response.body(), new TypeReference<Map<String, String>>() { }));
            }
        }
        return secrets;
    }

    private static DaprHttpResponse read(DaprHttpClient http, String path, DaprSecretsConfigDataResource resource) {
        DaprHttpResponse response;
        try {
            response = http.get(path, null);
        } catch (RuntimeException e) {
            throw new ConfigDataResourceNotFoundException(resource, e);
        }
        if (!response.isSuccessful()) {
            throw new ConfigDataResourceNotFoundException(resource,
                    DaprHttpClient.toError("Failed to read Dapr secrets " + resource, response));
        }
        return response;
    }

    private static void awaitSidecar(DaprHttpClient http, Duration wait) {
        long deadline = System.nanoTime() + (wait == null ? Duration.ofSeconds(30) : wait).toNanos();
        while (true) {
            try {
                if (http.get("/v1.0/healthz/outbound", Duration.ofSeconds(2)).isSuccessful()) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // not up yet
            }
            if (System.nanoTime() >= deadline) {
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static Map<String, Map<String, String>> memorySecrets(DaprSecretsConfigDataResource resource) {
        Map<String, Map<String, String>> all = resource.getMemorySecrets();
        if (resource.getNames().isEmpty()) {
            return all;
        }
        Map<String, Map<String, String>> selected = new LinkedHashMap<>();
        for (String name : resource.getNames()) {
            Map<String, String> values = all.get(name);
            if (values == null) {
                throw new ConfigDataResourceNotFoundException(resource);
            }
            selected.put(name, values);
        }
        return selected;
    }
}
