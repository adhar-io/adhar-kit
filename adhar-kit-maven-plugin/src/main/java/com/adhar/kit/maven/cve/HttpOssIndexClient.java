package com.adhar.kit.maven.cve;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;

/**
 * {@link OssIndexClient} backed by the public Sonatype OSS Index REST API
 * ({@code POST https://ossindex.sonatype.org/api/v3/component-report}). No
 * authentication is required for modest request volumes.
 *
 * <p>Responses are cached under a caller-supplied directory (typically {@code
 * target/}) keyed by a hash of the requested purls, so repeated builds do not
 * re-query the service. Delete the cache directory to force a refresh.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class HttpOssIndexClient implements OssIndexClient {

    private static final String ENDPOINT = "https://ossindex.sonatype.org/api/v3/component-report";
    private static final int MAX_COORDINATES_PER_REQUEST = 128;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final File cacheDir;
    private final Log log;

    public HttpOssIndexClient(File cacheDir, Log log) {
        this(cacheDir, log, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build());
    }

    HttpOssIndexClient(File cacheDir, Log log, HttpClient httpClient) {
        this.cacheDir = cacheDir;
        this.log = log;
        this.httpClient = httpClient;
    }

    @Override
    public String componentReport(List<String> purls) throws IOException {
        File cacheFile = cacheFileFor(purls);
        if (cacheFile != null && cacheFile.isFile()) {
            log.info("Using cached OSS Index report: " + cacheFile);
            return Files.readString(cacheFile.toPath(), StandardCharsets.UTF_8);
        }

        String json = fetch(purls);

        if (cacheFile != null) {
            try {
                if (cacheDir != null) {
                    cacheDir.mkdirs();
                }
                Files.writeString(cacheFile.toPath(), json, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Could not write OSS Index cache file " + cacheFile + ": " + e.getMessage());
            }
        }
        return json;
    }

    private String fetch(List<String> purls) throws IOException {
        // OSS Index caps coordinates per request; chunk and merge the arrays.
        ArrayNode merged = objectMapper.createArrayNode();
        for (int start = 0; start < purls.size(); start += MAX_COORDINATES_PER_REQUEST) {
            List<String> chunk = purls.subList(start, Math.min(purls.size(), start + MAX_COORDINATES_PER_REQUEST));
            String body = requestBody(chunk);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("OSS Index returned HTTP " + response.statusCode());
                }
                for (var node : objectMapper.readTree(response.body())) {
                    merged.add(node);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("OSS Index request interrupted", e);
            }
        }
        return objectMapper.writeValueAsString(merged);
    }

    private String requestBody(List<String> purls) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode coordinates = root.putArray("coordinates");
        purls.forEach(coordinates::add);
        return objectMapper.writeValueAsString(root);
    }

    private File cacheFileFor(List<String> purls) {
        if (cacheDir == null) {
            return null;
        }
        return new File(cacheDir, "adhar-cve-" + hash(purls) + ".json");
    }

    private static String hash(List<String> purls) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String purl : purls) {
                digest.update(purl.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(purls.hashCode());
        }
    }
}
