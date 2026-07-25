package com.adhar.kit.analytics.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link PostHogClient} implementation backed by OkHttp.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class OkHttpPostHogClient implements PostHogClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;

    public OkHttpPostHogClient(OkHttpClient httpClient, ObjectMapper objectMapper, String apiKey, String apiUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiUrl = normalize(apiUrl);
    }

    private static String normalize(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public void capture(CaptureEvent event) {
        if (event == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("api_key", apiKey);
        payload.put("event", event.event());
        payload.put("properties", withDistinctId(event));
        payload.put("timestamp", event.timestamp().toString());
        post("/capture/", payload);
    }

    @Override
    public void batch(List<CaptureEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("api_key", apiKey);
        List<Map<String, Object>> batch = new ArrayList<>(events.size());
        for (CaptureEvent event : events) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("event", event.event());
            item.put("properties", withDistinctId(event));
            item.put("timestamp", event.timestamp().toString());
            batch.add(item);
        }
        payload.put("batch", batch);
        post("/batch/", payload);
    }

    @Override
    @SuppressWarnings("unchecked")
    public DecideResult decide(String distinctId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("api_key", apiKey);
            payload.put("distinct_id", distinctId);

            String json = objectMapper.writeValueAsString(payload);
            Request request = new Request.Builder()
                    .url(apiUrl + "/decide/?v=3")
                    .post(RequestBody.create(json, JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("PostHog /decide call failed: {}", response.code());
                    return DecideResult.empty();
                }
                Map<String, Object> result = objectMapper.readValue(response.body().string(), Map.class);
                Object flags = result.get("featureFlags");
                if (flags instanceof Map) {
                    return new DecideResult((Map<String, Object>) flags);
                }
                return DecideResult.empty();
            }
        } catch (IOException e) {
            log.error("Failed to call PostHog /decide endpoint", e);
            return DecideResult.empty();
        }
    }

    private Map<String, Object> withDistinctId(CaptureEvent event) {
        Map<String, Object> properties = new LinkedHashMap<>(event.properties());
        properties.putIfAbsent("distinct_id", event.distinctId());
        return properties;
    }

    private void post(String path, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            Request request = new Request.Builder()
                    .url(apiUrl + path)
                    .post(RequestBody.create(json, JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("PostHog API call to {} failed: {}", path, response.code());
                }
            }
        } catch (IOException e) {
            log.error("Failed to call PostHog endpoint " + path, e);
        }
    }

    @Override
    public void close() {
        try {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        } catch (Exception e) {
            log.debug("Error while closing PostHog HTTP client", e);
        }
    }
}
