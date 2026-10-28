package com.adhar.kit.dapr;

import io.dapr.config.Properties;
import io.dapr.config.Property;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Where the sidecar is and who this app is, resolved once from configuration and the environment
 * the Dapr injector sets on every annotated pod.
 *
 * <p>Resolution follows the adhar-kit contract shared by every kit language:</p>
 * <ul>
 *   <li>HTTP: {@code DAPR_HTTP_ENDPOINT} (full URL), else
 *       {@code http://${DAPR_SIDECAR_IP:-127.0.0.1}:${DAPR_HTTP_PORT:-3500}}.</li>
 *   <li>Token: {@code DAPR_API_TOKEN}, sent as the {@code dapr-api-token} header.</li>
 *   <li>App id: {@code APP_ID}, {@code DAPR_APP_ID}, {@code ADHAR_APP_NAME}, then
 *       {@code spring.application.name}.</li>
 *   <li>Mode ({@code ADHAR_DAPR_MODE}): {@code auto} picks the sidecar when
 *       {@code DAPR_HTTP_PORT} or {@code DAPR_HTTP_ENDPOINT} is set and memory otherwise.</li>
 * </ul>
 * <p>The matching {@code adhar.dapr.*} property wins over each environment variable.</p>
 *
 * @param mode          the effective mode: {@link DaprMode#SIDECAR} or {@link DaprMode#MEMORY}
 * @param requestedMode what was configured (may be {@link DaprMode#AUTO})
 * @param httpEndpoint  the sidecar HTTP base URL, without a trailing slash
 * @param grpcEndpoint  the sidecar gRPC endpoint, or {@code null} for the SDK default
 * @param grpcPort      the sidecar gRPC port, or {@code null} for the SDK default
 * @param apiToken      the Dapr API token, or {@code null}
 * @param appId         this application's Dapr app id
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record DaprConnectionDetails(
        DaprMode mode,
        DaprMode requestedMode,
        String httpEndpoint,
        String grpcEndpoint,
        Integer grpcPort,
        String apiToken,
        String appId) {

    /** Default sidecar HTTP port. */
    public static final int DEFAULT_HTTP_PORT = 3500;

    /** Header carrying the Dapr API token, in both directions. */
    public static final String API_TOKEN_HEADER = "dapr-api-token";

    /**
     * Resolves from system properties and environment variables only (no Spring).
     *
     * @return the resolved details
     */
    public static DaprConnectionDetails fromEnvironment() {
        return resolve(key -> {
            String value = System.getProperty(key);
            return value != null ? value : System.getenv(key);
        });
    }

    /**
     * Resolves from a key lookup that answers both {@code adhar.dapr.*} property names and the
     * environment variable names listed on this type.
     *
     * @param lookup returns the value for a key, or {@code null}
     * @return the resolved details
     */
    public static DaprConnectionDetails resolve(Function<String, String> lookup) {
        DaprMode requested = DaprMode.parse(first(lookup, "adhar.dapr.mode", "ADHAR_DAPR_MODE"));
        String endpoint = first(lookup, "adhar.dapr.http-endpoint", "DAPR_HTTP_ENDPOINT");
        String port = first(lookup, "adhar.dapr.dapr-http-port", "adhar.dapr.dapr-port", "DAPR_HTTP_PORT");
        String sidecarIp = first(lookup, "DAPR_SIDECAR_IP");

        String httpEndpoint;
        if (endpoint != null) {
            httpEndpoint = stripTrailingSlash(endpoint);
        } else {
            String host = sidecarIp != null ? sidecarIp : "127.0.0.1";
            httpEndpoint = "http://" + host + ":" + (port != null ? Integer.parseInt(port.trim()) : DEFAULT_HTTP_PORT);
        }

        DaprMode effective = switch (requested) {
            case SIDECAR -> DaprMode.SIDECAR;
            case MEMORY -> DaprMode.MEMORY;
            case AUTO -> (endpoint != null || port != null) ? DaprMode.SIDECAR : DaprMode.MEMORY;
        };

        String grpcPort = first(lookup, "adhar.dapr.dapr-grpc-port", "DAPR_GRPC_PORT");
        return new DaprConnectionDetails(
                effective,
                requested,
                httpEndpoint,
                first(lookup, "adhar.dapr.grpc-endpoint", "DAPR_GRPC_ENDPOINT"),
                grpcPort != null ? Integer.valueOf(grpcPort.trim()) : null,
                first(lookup, "adhar.dapr.api-token", "DAPR_API_TOKEN"),
                firstOr(lookup, "unknown", "adhar.dapr.app-id", "APP_ID", "DAPR_APP_ID", "ADHAR_APP_NAME",
                        "spring.application.name"));
    }

    /** {@code true} when no sidecar is used. */
    public boolean isMemory() {
        return mode == DaprMode.MEMORY;
    }

    /**
     * Overrides for the Dapr SDK's own {@link Properties}, so the gRPC client, the workflow runtime
     * and the actor runtime talk to the same sidecar with the same token as the kit.
     *
     * @return property overrides
     */
    public Map<Property<?>, String> sdkOverrides() {
        Map<Property<?>, String> overrides = new LinkedHashMap<>();
        overrides.put(Properties.HTTP_ENDPOINT, httpEndpoint);
        if (grpcEndpoint != null) {
            overrides.put(Properties.GRPC_ENDPOINT, grpcEndpoint);
        }
        if (grpcPort != null) {
            overrides.put(Properties.GRPC_PORT, String.valueOf(grpcPort));
        }
        if (apiToken != null) {
            overrides.put(Properties.API_TOKEN, apiToken);
        }
        return overrides;
    }

    /**
     * The SDK {@link Properties} carrying {@link #sdkOverrides()}.
     *
     * @return SDK properties
     */
    public Properties sdkProperties() {
        Map<String, String> byName = new LinkedHashMap<>();
        sdkOverrides().forEach((property, value) -> byName.put(property.getName(), value));
        return new Properties(byName);
    }

    @Override
    public String toString() {
        // Never print the token.
        return "DaprConnectionDetails[mode=" + mode + ", requestedMode=" + requestedMode
                + ", httpEndpoint=" + httpEndpoint + ", grpcEndpoint=" + grpcEndpoint
                + ", grpcPort=" + grpcPort + ", apiToken=" + (apiToken == null ? "none" : "***")
                + ", appId=" + appId + "]";
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

    private static String firstOr(Function<String, String> lookup, String fallback, String... keys) {
        String value = first(lookup, keys);
        return value != null ? value : fallback;
    }

    private static String stripTrailingSlash(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
