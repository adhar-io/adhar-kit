package com.adhar.kit.metrics.env;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * OTLP PUSH of metrics, configured from the standard OpenTelemetry environment variables onto
 * Spring Boot's {@code management.otlp.metrics.export.*}, for applications that add
 * {@code io.micrometer:micrometer-registry-otlp} (design 0025 §5.13). Modelled on the tracing
 * module's {@code OtelEnvironmentPostProcessor}: Spring Boot reads none of the {@code OTEL_*}
 * variables itself.
 *
 * <p>Push is what a batch run pod needs: it can live for less than one 30-second scrape interval,
 * and a scrape-only service would never be seen. The registry publishes once more when the context
 * closes, so the last values of a run leave the pod with it.</p>
 *
 * <ul>
 *   <li>{@code OTEL_EXPORTER_OTLP_METRICS_ENDPOINT} (as-is), else {@code OTEL_EXPORTER_OTLP_ENDPOINT}
 *       + {@code /v1/metrics} → {@code management.otlp.metrics.export.url}</li>
 *   <li>Export is OFF without an endpoint (the registry would otherwise push to {@code localhost:4318}
 *       forever), with {@code OTEL_METRICS_EXPORTER} naming no {@code otlp} (e.g. {@code none}), and with
 *       protocol {@code grpc}: Micrometer's registry speaks {@code http/protobuf} only</li>
 *   <li>{@code OTEL_EXPORTER_OTLP_[METRICS_]HEADERS} → {@code ...headers[...]}</li>
 *   <li>{@code OTEL_METRIC_EXPORT_INTERVAL} (milliseconds) → {@code ...step}</li>
 * </ul>
 * Everything sits at the LOWEST precedence, so an application's own {@code management.otlp.*}
 * wins, and an application that names its own {@code url} is never switched off here. Off with
 * {@code adhar.metrics.enabled=false}.
 */
public class OtlpMetricsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Name of the contributed property source. */
    public static final String PROPERTY_SOURCE_NAME = "adharOtlpMetricsEnvironment";

    static final String EXPORT = "management.otlp.metrics.export.";

    private final Function<String, String> env;

    public OtlpMetricsEnvironmentPostProcessor() {
        this(System::getenv);
    }

    OtlpMetricsEnvironmentPostProcessor(Function<String, String> env) {
        this.env = env;
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("adhar.metrics.enabled", Boolean.class, true)) {
            return;
        }
        Map<String, Object> mapped = map();
        if (environment.containsProperty(EXPORT + "url")) {
            mapped.remove(EXPORT + "enabled");
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, mapped));
    }

    /** The property values implied by the process environment. */
    Map<String, Object> map() {
        Map<String, Object> properties = new LinkedHashMap<>();
        String protocol = firstText(get("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL"), get("OTEL_EXPORTER_OTLP_PROTOCOL"),
                "http/protobuf").trim();
        String url = metricsUrl();
        boolean exporterAllowsOtlp = exporterAllowsOtlp(get("OTEL_METRICS_EXPORTER"));
        boolean http = !"grpc".equalsIgnoreCase(protocol);

        if (url != null && http) {
            properties.put(EXPORT + "url", url);
        }
        properties.put(EXPORT + "enabled", String.valueOf(url != null && http && exporterAllowsOtlp));

        parseHeaders(firstText(get("OTEL_EXPORTER_OTLP_METRICS_HEADERS"), get("OTEL_EXPORTER_OTLP_HEADERS"), null))
                .forEach((key, value) -> properties.put(EXPORT + "headers[" + key + "]", value));

        String interval = get("OTEL_METRIC_EXPORT_INTERVAL");
        if (interval != null && interval.trim().matches("\\d+")) {
            properties.put(EXPORT + "step", interval.trim() + "ms");
        }
        return properties;
    }

    private String metricsUrl() {
        String signal = get("OTEL_EXPORTER_OTLP_METRICS_ENDPOINT");
        if (signal != null) {
            return signal.trim();
        }
        String base = get("OTEL_EXPORTER_OTLP_ENDPOINT");
        return base == null ? null : base.trim().replaceAll("/+$", "") + "/v1/metrics";
    }

    /** Unset means the SDK default, {@code otlp}; otherwise the comma-separated list must name it. */
    static boolean exporterAllowsOtlp(String exporters) {
        if (exporters == null) {
            return true;
        }
        return Arrays.stream(exporters.split(",")).map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch("otlp"::equals);
    }

    /** {@code key1=value1,key2=value2}, values percent-decoded, as the OpenTelemetry SDK reads them. */
    static Map<String, String> parseHeaders(String raw) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (!StringUtils.hasText(raw)) {
            return headers;
        }
        for (String pair : raw.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String value = URLDecoder.decode(pair.substring(eq + 1).trim(), StandardCharsets.UTF_8);
            if (!key.isEmpty()) {
                headers.put(key, value);
            }
        }
        return headers;
    }

    private String get(String name) {
        String value = env.apply(name);
        return StringUtils.hasText(value) ? value : null;
    }

    private static String firstText(String first, String second, String fallback) {
        return first != null ? first : second != null ? second : fallback;
    }
}
