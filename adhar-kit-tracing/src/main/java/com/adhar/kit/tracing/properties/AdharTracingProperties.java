package com.adhar.kit.tracing.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Adhar Tracing.
 * <p>
 * This class provides comprehensive configuration options for distributed tracing,
 * supporting OpenTelemetry, Zipkin, and Jaeger backends with flexible sampling and propagation settings.
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "adhar.tracing")
public class AdharTracingProperties {

    /**
     * Whether tracing is enabled globally.
     */
    private boolean enabled = true;

    /**
     * Common tags to be added to all spans.
     */
    private Map<String, String> tags = new HashMap<>();

    /**
     * Configuration properties for OpenTelemetry tracing.
     */
    private OpenTelemetryProperties openTelemetry = new OpenTelemetryProperties();

    /**
     * Configuration properties for Zipkin tracing.
     */
    private ZipkinProperties zipkin = new ZipkinProperties();

    /**
     * Configuration properties for Jaeger tracing.
     */
    private JaegerProperties jaeger = new JaegerProperties();

    /**
     * Configuration properties for controller tracing.
     */
    private WebTracingProperties web = new WebTracingProperties();

    /**
     * Configuration properties for database tracing.
     */
    private DatabaseTracingProperties database = new DatabaseTracingProperties();

    /**
     * Configuration properties for messaging tracing.
     */
    private MessagingTracingProperties messaging = new MessagingTracingProperties();

    /**
     * Configuration properties for sampling.
     */
    private SamplingProperties sampling = new SamplingProperties();

    /**
     * Configuration properties for propagation.
     */
    private PropagationProperties propagation = new PropagationProperties();

    /**
     * Configuration properties for baggage.
     */
    private BaggageProperties baggage = new BaggageProperties();

    /**
     * Configuration properties for resource attributes.
     */
    private ResourceProperties resource = new ResourceProperties();

    /**
     * Configuration properties for the span-to-metrics bridge.
     */
    private MetricsBridgeProperties metrics = new MetricsBridgeProperties();

    /**
     * Configuration properties for OpenTelemetry tracing.
     */
    @Data
    public static class OpenTelemetryProperties {

        /**
         * Whether OpenTelemetry tracing is enabled.
         */
        private boolean enabled = true;

        /**
         * OTLP endpoint URL.
         */
        private String endpoint = "http://localhost:4317";

        /**
         * Export timeout.
         */
        private String timeout = "PT30S";

        /**
         * Export interval.
         */
        private String interval = "PT30S";

        /**
         * Headers to include in exports.
         */
        private Map<String, String> headers = new HashMap<>();

        /**
         * Whether to use gRPC (true) or HTTP (false).
         */
        private boolean useGrpc = true;

        /**
         * Compression type (gzip, none).
         */
        private String compression = "gzip";
    }

    /**
     * Configuration properties for Zipkin tracing.
     */
    @Data
    public static class ZipkinProperties {

        /**
         * Whether Zipkin tracing is enabled.
         */
        private boolean enabled = false;

        /**
         * Zipkin base URL.
         */
        private String baseUrl = "http://localhost:9411";

        /**
         * API endpoint path.
         */
        private String apiPath = "/api/v2/spans";

        /**
         * Connect timeout.
         */
        private String connectTimeout = "PT10S";

        /**
         * Read timeout.
         */
        private String readTimeout = "PT10S";

        /**
         * Sender type (http, kafka).
         */
        private String senderType = "http";

        /**
         * Kafka bootstrap servers (when using kafka sender).
         */
        private String kafkaBootstrapServers = "localhost:9092";

        /**
         * Kafka topic (when using kafka sender).
         */
        private String kafkaTopic = "zipkin";
    }

    /**
     * Configuration properties for Jaeger tracing.
     */
    @Data
    public static class JaegerProperties {

        /**
         * Whether Jaeger tracing is enabled.
         */
        private boolean enabled = false;

        /**
         * Jaeger gRPC endpoint.
         */
        private String grpcEndpoint = "http://localhost:14250";

        /**
         * Jaeger HTTP endpoint.
         */
        private String httpEndpoint = "http://localhost:14268/api/traces";

        /**
         * Connection timeout.
         */
        private String timeout = "PT30S";

        /**
         * Whether to use gRPC (true) or HTTP (false).
         */
        private boolean useGrpc = true;
    }

    /**
     * Configuration properties for controller tracing.
     */
    @Data
    public static class WebTracingProperties {

        /**
         * Whether controller tracing is enabled.
         */
        private boolean enabled = true;

        /**
         * Whether to trace HTTP client requests.
         */
        private boolean traceHttpClients = true;

        /**
         * Whether to trace HTTP server requests.
         */
        private boolean traceHttpServers = true;

        /**
         * URL patterns to skip tracing.
         */
        private String[] skipPatterns = {"/actuator/**", "/health/**", "/info/**"};

        /**
         * Whether to include request headers in spans.
         */
        private boolean includeRequestHeaders = false;

        /**
         * Whether to include response headers in spans.
         */
        private boolean includeResponseHeaders = false;

        /**
         * Maximum number of URL tags to create.
         */
        private int maxUrlTags = 100;

        /**
         * Whether to include request/response body in spans.
         */
        private boolean includeRequestBody = false;

        /**
         * Whether to include response body in spans.
         */
        private boolean includeResponseBody = false;

        /**
         * Whether to register the {@code TraceContextMdcFilter} that injects the current
         * traceId/spanId into the SLF4J MDC for the duration of each HTTP request.
         */
        private boolean mdcEnabled = true;

        /**
         * Whether to register the {@code TracingServerSpanFilter} that creates a SERVER span
         * per incoming HTTP request.
         */
        private boolean serverSpansEnabled = true;

        /**
         * Whether to skip registering the server-span filter when a known framework
         * server-span instrumentation (see {@link #getInstrumentationDetectionClasses()})
         * is detected on the classpath, to avoid duplicate SERVER spans.
         */
        private boolean detectExistingInstrumentation = true;

        /**
         * Class names whose presence on the classpath indicates that a framework
         * server-span instrumentation is already active (e.g. the OpenTelemetry Java agent
         * or the OpenTelemetry Spring WebMVC library instrumentation).
         */
        private String[] instrumentationDetectionClasses = {
                "io.opentelemetry.javaagent.OpenTelemetryAgent",
                "io.opentelemetry.instrumentation.spring.webmvc.v6_0.SpringWebMvcTelemetry",
                "io.opentelemetry.instrumentation.spring.webmvc.v5_3.SpringWebMvcTelemetry"
        };
    }

    /**
     * Configuration properties for database tracing.
     */
    @Data
    public static class DatabaseTracingProperties {

        /**
         * Whether database tracing is enabled.
         */
        private boolean enabled = true;

        /**
         * Whether to include SQL statements in spans.
         */
        private boolean includeSqlStatements = true;

        /**
         * Whether to include SQL parameters in spans.
         */
        private boolean includeSqlParameters = false;

        /**
         * Maximum length of SQL statements to include.
         */
        private int maxSqlLength = 1000;

        /**
         * Whether to trace connection pool operations.
         */
        private boolean traceConnectionPool = true;
    }

    /**
     * Configuration properties for messaging tracing.
     */
    @Data
    public static class MessagingTracingProperties {

        /**
         * Whether messaging tracing is enabled.
         */
        private boolean enabled = true;

        /**
         * Whether to trace Kafka operations.
         */
        private boolean traceKafka = true;

        /**
         * Whether to trace RabbitMQ operations.
         */
        private boolean traceRabbitmq = true;

        /**
         * Whether to trace Redis operations.
         */
        private boolean traceRedis = true;

        /**
         * Whether to include message payloads in spans.
         */
        private boolean includePayloads = false;

        /**
         * Maximum payload size to include in spans.
         */
        private int maxPayloadSize = 1024;
    }

    /**
     * Configuration properties for sampling.
     */
    @Data
    public static class SamplingProperties {

        /**
         * Sampling mode: "ratio" (head-based, probability-driven) or "tail"
         * (buffer finished traces and keep them based on error/latency/rate policies).
         */
        private String mode = "ratio";

        /**
         * Default sampling probability (0.0 to 1.0). Used when {@code mode} is "ratio".
         */
        private double probability = 0.1;

        /**
         * Rate limiting samples per second.
         */
        private int rateLimit = 100;

        /**
         * Per-service sampling rules.
         */
        private Map<String, Double> perService = new HashMap<>();

        /**
         * Per-operation sampling rules.
         */
        private Map<String, Double> perOperation = new HashMap<>();

        /**
         * Tail-sampling configuration. Used when {@code mode} is "tail".
         */
        private TailSamplingProperties tail = new TailSamplingProperties();
    }

    /**
     * Configuration properties for tail sampling.
     */
    @Data
    public static class TailSamplingProperties {

        /**
         * How long to keep holding a finished root-span tree (waiting for late-finishing
         * child spans) before deciding whether to keep the trace, in milliseconds.
         * 0 decides immediately when the root span ends.
         */
        private long holdWindowMs = 2000;

        /**
         * Root-span latency threshold above which a trace is always kept, in milliseconds.
         */
        private long latencyThresholdMs = 1000;

        /**
         * Fraction (0.0 to 1.0) of traces that match neither the error nor the latency
         * policy that are still kept (rate-based head fraction).
         */
        private double keepRate = 0.1;

        /**
         * Maximum number of traces buffered while awaiting a decision. When exceeded,
         * the oldest buffered trace is decided (and flushed or dropped) immediately.
         */
        private int maxBufferedTraces = 1000;

        /**
         * Safety timeout after which a buffered trace is decided even if its root span
         * never finished, in milliseconds.
         */
        private long traceTimeoutMs = 30000;
    }

    /**
     * Configuration properties for propagation.
     */
    @Data
    public static class PropagationProperties {

        /**
         * Propagation type (tracecontext, b3, jaeger, ottrace).
         */
        private String type = "tracecontext";

        /**
         * Additional propagation formats.
         */
        private String[] additionalFormats = {};

        /**
         * Whether to produce B3 single header.
         */
        private boolean b3SingleHeader = false;
    }

    /**
     * Configuration properties for baggage.
     */
    @Data
    public static class BaggageProperties {

        /**
         * Whether baggage is enabled.
         */
        private boolean enabled = true;

        /**
         * Remote fields to propagate as baggage.
         */
        private String[] remoteFields = {};

        /**
         * Correlation fields to extract from baggage.
         */
        private String[] correlationFields = {};

        /**
         * Maximum number of baggage entries.
         */
        private int maxEntries = 100;

        /**
         * Maximum length of baggage values.
         */
        private int maxValueLength = 1000;
    }

    /**
     * Configuration properties for resource attributes.
     */
    @Data
    public static class ResourceProperties {

        /**
         * Service name.
         */
        private String serviceName = "${spring.application.name:unknown-service}";

        /**
         * Service version.
         */
        private String serviceVersion = "unknown";

        /**
         * Service namespace.
         */
        private String serviceNamespace = "default";

        /**
         * Service instance ID.
         */
        private String serviceInstanceId = "${random.uuid}";

        /**
         * Additional resource attributes.
         */
        private Map<String, String> attributes = new HashMap<>();

        /**
         * Whether to auto-detect resource attributes.
         */
        private boolean autoDetect = true;
    }

    /**
     * Configuration properties for the span-to-metrics (RED) bridge, which derives
     * rate/error/duration metrics from finished spans.
     */
    @Data
    public static class MetricsBridgeProperties {

        /**
         * Whether the span-to-metrics bridge is enabled.
         */
        private boolean enabled = true;

        /**
         * Prefix for the meter names emitted by the bridge (e.g. {@code span} yields
         * {@code span.duration}, {@code span.errors}).
         */
        private String meterPrefix = "span";

        /**
         * Whether to tag emitted metrics with the span kind (SERVER, CLIENT, ...).
         */
        private boolean includeSpanKind = true;

        /**
         * Whether to record an error metric/dimension when a span ends in an error status.
         */
        private boolean recordErrors = true;
    }
}
