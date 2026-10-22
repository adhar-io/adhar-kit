package com.adhar.kit.tracing.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Maps the standard OpenTelemetry environment variables onto the kit's tracing properties
 * (and Spring Boot's own, for applications that add Boot's OpenTelemetry starter).
 * <p>
 * Neither the kit's SDK nor Spring Boot reads {@code OTEL_*} variables. A service deployed
 * with {@code OTEL_EXPORTER_OTLP_ENDPOINT=http://alloy...:4318} therefore kept exporting to
 * the default {@code localhost:4317} over gRPC, and no trace ever reached Tempo.
 * <p>
 * The mapped values sit at the LOWEST precedence: a property the application sets wins.
 * <ul>
 *   <li>{@code OTEL_EXPORTER_OTLP_TRACES_ENDPOINT} (as-is), else {@code OTEL_EXPORTER_OTLP_ENDPOINT}
 *       (+ {@code /v1/traces} for HTTP) → {@code adhar.tracing.open-telemetry.endpoint},
 *       {@code management.opentelemetry.tracing.export.otlp.endpoint}</li>
 *   <li>{@code OTEL_EXPORTER_OTLP_[TRACES_]PROTOCOL} (default {@code http/protobuf}) →
 *       {@code adhar.tracing.open-telemetry.use-grpc}, {@code ...otlp.transport}</li>
 *   <li>{@code OTEL_TRACES_EXPORTER=none} → both exporters off</li>
 *   <li>{@code OTEL_SERVICE_NAME} → {@code adhar.tracing.resource.service-name}</li>
 *   <li>{@code OTEL_RESOURCE_ATTRIBUTES} → {@code adhar.tracing.resource.attributes[...]},
 *       {@code management.opentelemetry.resource-attributes[...]}; a missing
 *       {@code deployment.environment.name} is filled from the kit's resolved environment</li>
 *   <li>{@code OTEL_TRACES_SAMPLER_ARG} → {@code adhar.tracing.sampling.probability}; Spring
 *       Boot's {@code management.tracing.sampling.probability} defaults to 1.0 here</li>
 * </ul>
 * In a pod, a {@code deployment.environment[.name]=local} in {@code OTEL_RESOURCE_ATTRIBUTES}
 * is NOT taken literally: services scaffolded before the environment was set per overlay
 * hard-coded it in the base Rollout, so every environment's traces said {@code local}. It is
 * replaced by the kit's resolved environment ({@code ADHAR_ENVIRONMENT}, else the namespace).
 * <p>
 * It also switches on the observation of the clients Spring Boot leaves unobserved by
 * default (see {@link #OBSERVATION_DEFAULTS}), so their calls become spans that carry the
 * trace context: Kafka's {@code KafkaTemplate} and listener containers, RabbitMQ's template
 * and listener containers. JDBC spans (datasource-micrometer) record connection acquisition
 * and queries, never parameter values. All of it is overridable by the application.
 */
public class OtelEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Name of the contributed property source. */
    public static final String PROPERTY_SOURCE_NAME = "adharOtelEnvironment";

    private static final String KIT = "adhar.tracing.";
    private static final String BOOT_OTLP = "management.opentelemetry.tracing.export.otlp.";

    /**
     * Observation switches whose Spring Boot default is OFF. Without them a Kafka send has
     * no span and, worse, no {@code traceparent} record header, so the consumer starts a new
     * trace. (RestClient, RestTemplate and WebClient built from Boot's builders, Lettuce
     * commands and incoming HTTP requests are observed by Boot unconditionally.)
     */
    static final Map<String, String> OBSERVATION_DEFAULTS = Map.of(
            "spring.kafka.template.observation-enabled", "true",
            "spring.kafka.listener.observation-enabled", "true",
            "spring.rabbitmq.template.observation-enabled", "true",
            "spring.rabbitmq.listener.simple.observation-enabled", "true",
            "spring.rabbitmq.listener.direct.observation-enabled", "true",
            "spring.rabbitmq.listener.stream.observation-enabled", "true",
            // datasource-micrometer: a span per connection acquisition and per query. Not
            // "fetch" - a span per ResultSet is noise at any real row count.
            "jdbc.includes", "connection,query",
            "jdbc.datasource-proxy.include-parameter-values", "false");

    /** The deployment environment a pod is never in, but old scaffolds hard-coded. */
    static final String LOCAL_ENVIRONMENT = "local";

    static final Path SERVICE_ACCOUNT_NAMESPACE =
            Path.of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");

    private final Function<String, String> env;
    private final Path namespaceFile;

    public OtelEnvironmentPostProcessor() {
        this(System::getenv);
    }

    OtelEnvironmentPostProcessor(Function<String, String> env) {
        this(env, SERVICE_ACCOUNT_NAMESPACE);
    }

    OtelEnvironmentPostProcessor(Function<String, String> env, Path namespaceFile) {
        this.env = env;
        this.namespaceFile = namespaceFile;
    }

    @Override
    public int getOrder() {
        // After application config, and after the metrics module resolved adhar.environment.
        return ConfigDataEnvironmentPostProcessor.ORDER + 2;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> mapped = map();
        alignProtocolWithAnExplicitEndpoint(environment, mapped);
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, mapped));
    }

    /**
     * An application that names its collector ({@code adhar.tracing.open-telemetry.endpoint})
     * but not the protocol must not get the protocol of the OTEL_* variables: the gateway
     * scaffold pinned {@code http://alloy...:4317} (the gRPC port) while its Rollout says
     * {@code OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf}, which would POST HTTP to a gRPC
     * listener and export nothing. The protocol then follows the endpoint: port 4317 is
     * gRPC, port 4318 or a {@code /v1/traces} path is HTTP; anything else keeps the kit's
     * default ({@code use-grpc=true}).
     */
    static void alignProtocolWithAnExplicitEndpoint(ConfigurableEnvironment environment, Map<String, Object> mapped) {
        String useGrpcKey = KIT + "open-telemetry.use-grpc";
        String explicit = environment.getProperty(KIT + "open-telemetry.endpoint");
        if (explicit == null || environment.containsProperty(useGrpcKey)) {
            return;
        }
        Boolean grpc = grpcImpliedBy(explicit);
        if (grpc == null) {
            mapped.remove(useGrpcKey);
        } else {
            mapped.put(useGrpcKey, String.valueOf(grpc));
        }
    }

    /** {@code true} for a gRPC collector address, {@code false} for an HTTP one, else {@code null}. */
    static Boolean grpcImpliedBy(String endpoint) {
        try {
            java.net.URI uri = java.net.URI.create(endpoint.trim());
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.endsWith("/v1/traces") || uri.getPort() == 4318) {
                return false;
            }
            if (uri.getPort() == 4317) {
                return true;
            }
        } catch (IllegalArgumentException e) {
            // Not a URI: leave it to the kit's default.
        }
        return null;
    }

    /** The property values implied by the process environment. */
    Map<String, Object> map() {
        Map<String, Object> properties = new LinkedHashMap<>();

        String protocol = firstText(get("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL"), get("OTEL_EXPORTER_OTLP_PROTOCOL"),
                "http/protobuf");
        boolean grpc = "grpc".equalsIgnoreCase(protocol.trim());
        String endpoint = tracesEndpoint(grpc);
        if (endpoint != null) {
            properties.put(KIT + "open-telemetry.endpoint", endpoint);
            properties.put(KIT + "open-telemetry.use-grpc", String.valueOf(grpc));
            properties.put(BOOT_OTLP + "endpoint", endpoint);
            properties.put(BOOT_OTLP + "transport", grpc ? "grpc" : "http");
        }

        String exporter = get("OTEL_TRACES_EXPORTER");
        if (exporter != null && "none".equalsIgnoreCase(exporter.trim())) {
            properties.put(KIT + "open-telemetry.enabled", "false");
            properties.put("management.tracing.export.otlp.enabled", "false");
        }

        String serviceName = get("OTEL_SERVICE_NAME");
        if (serviceName != null) {
            properties.put(KIT + "resource.service-name", serviceName.trim());
        }

        Map<String, String> attributes = parseResourceAttributes(get("OTEL_RESOURCE_ATTRIBUTES"));
        if (serviceName != null) {
            attributes.remove("service.name"); // OTEL_SERVICE_NAME wins, as in the SDK
        }
        if (runningInKubernetes()) {
            for (String key : new String[] {"deployment.environment", "deployment.environment.name"}) {
                if (LOCAL_ENVIRONMENT.equalsIgnoreCase(attributes.getOrDefault(key, "").trim())) {
                    // Resolved by the metrics module from ADHAR_ENVIRONMENT / the namespace.
                    attributes.put(key, "${adhar.environment:}");
                }
            }
        }
        attributes.forEach((key, value) -> {
            properties.put(KIT + "resource.attributes[" + key + "]", value);
            properties.put("management.opentelemetry.resource-attributes[" + key + "]", value);
        });
        if (!attributes.containsKey("deployment.environment.name")) {
            // Resolved by the metrics module; an empty value is skipped when the resource is built.
            properties.put(KIT + "resource.attributes[deployment.environment.name]", "${adhar.environment:}");
        }

        String probability = samplingProbability(get("OTEL_TRACES_SAMPLER_ARG"));
        if (probability != null) {
            properties.put(KIT + "sampling.probability", probability);
        }
        properties.put("management.tracing.sampling.probability", probability != null ? probability : "1.0");
        OBSERVATION_DEFAULTS.forEach(properties::putIfAbsent);
        return properties;
    }

    /**
     * Whether this process runs in a pod: the kubelet sets {@code KUBERNETES_SERVICE_HOST}
     * in every container, the platform's manifests set {@code POD_NAMESPACE}, and the
     * service-account namespace file is mounted unless automounting is off.
     */
    boolean runningInKubernetes() {
        if (get("KUBERNETES_SERVICE_HOST") != null || get("POD_NAMESPACE") != null) {
            return true;
        }
        try {
            return namespaceFile != null && Files.isReadable(namespaceFile);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String tracesEndpoint(boolean grpc) {
        String signal = get("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT");
        if (signal != null) {
            return signal.trim();
        }
        String base = get("OTEL_EXPORTER_OTLP_ENDPOINT");
        if (base == null) {
            return null;
        }
        String trimmed = base.trim().replaceAll("/+$", "");
        return grpc ? trimmed : trimmed + "/v1/traces";
    }

    /** A ratio in [0, 1], or {@code null} for anything else (other samplers use other args). */
    static String samplingProbability(String samplerArg) {
        if (samplerArg == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(samplerArg.trim());
            return value >= 0.0 && value <= 1.0 ? String.valueOf(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code key1=value1,key2=value2}, values percent-decoded (W3C Baggage octets). */
    static Map<String, String> parseResourceAttributes(String raw) {
        Map<String, String> attributes = new LinkedHashMap<>();
        if (!StringUtils.hasText(raw)) {
            return attributes;
        }
        for (String pair : raw.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                attributes.put(key, URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8));
            }
        }
        return attributes;
    }

    private String get(String name) {
        String value = env.apply(name);
        return StringUtils.hasText(value) ? value : null;
    }

    private static String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
