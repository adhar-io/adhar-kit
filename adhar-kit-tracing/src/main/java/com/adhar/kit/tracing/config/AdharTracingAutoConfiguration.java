package com.adhar.kit.tracing.config;

import com.adhar.kit.tracing.async.TraceContextTaskDecorator;
import com.adhar.kit.tracing.aspect.TracingAspect;
import com.adhar.kit.tracing.metrics.SpanMetricsProcessor;
import com.adhar.kit.tracing.properties.AdharTracingProperties;
import com.adhar.kit.tracing.sampling.TailSamplingSpanProcessor;
import com.adhar.kit.tracing.util.AdharTracing;
import com.adhar.kit.tracing.web.TraceContextMdcFilter;
import com.adhar.kit.tracing.web.TracingServerSpanFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for Adhar Tracing.
 * <p>
 * This class sets up the distributed tracing infrastructure based on the properties
 * defined in {@link AdharTracingProperties}. It configures OpenTelemetry, Zipkin,
 * and Jaeger exporters with proper sampling and propagation settings.
 * </p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AdharTracingProperties.class)
@ConditionalOnProperty(prefix = "adhar.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(Tracer.class)
@RequiredArgsConstructor
public class AdharTracingAutoConfiguration {

    private final AdharTracingProperties properties;
    private final Environment environment;

    /**
     * Create the main OpenTelemetry SDK instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry openTelemetry(ObjectProvider<SpanMetricsProcessor> spanMetricsProcessorProvider) {
        log.info("Initializing Adhar Tracing with OpenTelemetry support");

        OpenTelemetrySdkBuilder builder = OpenTelemetrySdk.builder();

        // Configure tracer provider
        SdkTracerProvider tracerProvider = createTracerProvider(spanMetricsProcessorProvider.getIfAvailable());
        builder.setTracerProvider(tracerProvider);

        // Configure context propagators
        ContextPropagators propagators = createContextPropagators();
        builder.setPropagators(propagators);

        OpenTelemetry openTelemetry = builder.build();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down OpenTelemetry SDK");
            tracerProvider.close();
        }));

        return openTelemetry;
    }

    /**
     * Create the Micrometer Tracer bean.
     * <p>
     * The {@link OtelCurrentTraceContext} and {@link BaggageManager} are wired explicitly
     * (rather than left {@code null}/defaulted) so that {@link Tracer#withSpan(io.micrometer.tracing.Span)}
     * has a real current-trace-context scope to push/pop, and so that baggage
     * ({@link Tracer#createBaggageInScope(String, String)} et al., as used by
     * {@link AdharTracing}) is backed by a real, context-scoped implementation instead of
     * silently no-op'ing.
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();
        BaggageManager baggageManager = createBaggageManager(currentTraceContext);
        return new OtelTracer(openTelemetry.getTracer("adhar-kit-tracing"),
                             currentTraceContext,
                             event -> {},  // EventPublisher that does nothing
                             baggageManager);
    }

    /**
     * Create the {@link BaggageManager} used by the {@link Tracer}, honoring
     * {@link AdharTracingProperties.BaggageProperties#getRemoteFields()} and
     * {@link AdharTracingProperties.BaggageProperties#getCorrelationFields()}. Returns
     * {@link BaggageManager#NOOP} when baggage is disabled.
     */
    private BaggageManager createBaggageManager(OtelCurrentTraceContext currentTraceContext) {
        AdharTracingProperties.BaggageProperties baggageProperties = properties.getBaggage();
        if (!baggageProperties.isEnabled()) {
            log.info("Baggage support is disabled");
            return BaggageManager.NOOP;
        }

        List<String> remoteFields = Arrays.asList(baggageProperties.getRemoteFields());
        List<String> correlationFields = Arrays.asList(baggageProperties.getCorrelationFields());
        log.debug("Configuring baggage manager with remoteFields={}, correlationFields={}", remoteFields, correlationFields);
        return new OtelBaggageManager(currentTraceContext, remoteFields, correlationFields);
    }

    /**
     * Create the tracing aspect.
     */
    @Bean
    @ConditionalOnMissingBean
    public TracingAspect tracingAspect(Tracer tracer) {
        return new TracingAspect(tracer);
    }

    /**
     * Create the consolidated AdharTracing utility.
     */
    @Bean
    @ConditionalOnMissingBean
    public AdharTracing adharTracing(Tracer tracer) {
        return new AdharTracing(tracer, properties.getBaggage());
    }

    /**
     * Create the {@link TraceContextTaskDecorator}, which users can wire into their own
     * {@code @Async} executors (e.g. {@code ThreadPoolTaskExecutor#setTaskDecorator}) so that
     * submitted tasks continue to see the submitting thread's span as "current".
     */
    @Bean
    @ConditionalOnMissingBean
    public TraceContextTaskDecorator traceContextTaskDecorator(Tracer tracer) {
        return new TraceContextTaskDecorator(tracer);
    }

    /**
     * Create the tracer provider with exporters, sampling, optional tail sampling and the
     * optional span-to-metrics bridge.
     *
     * @param metricsProcessor the span-to-RED-metrics processor, or {@code null} if disabled
     *                         or no {@link MeterRegistry} is available
     */
    private SdkTracerProvider createTracerProvider(SpanMetricsProcessor metricsProcessor) {
        SdkTracerProviderBuilder builder = SdkTracerProvider.builder();

        // Configure resource attributes
        Resource resource = createResource();
        builder.setResource(resource);

        // Configure sampling
        Sampler sampler = createSampler();
        builder.setSampler(sampler);

        // Build the exporting processors.
        List<SpanProcessor> exportProcessors = new ArrayList<>();
        for (SpanExporter exporter : createExporters()) {
            exportProcessors.add(BatchSpanProcessor.builder(exporter)
                    .setMaxExportBatchSize(512)
                    .setScheduleDelay(Duration.ofSeconds(5))
                    .build());
        }

        // Wire tail sampling in front of the exporters when requested; otherwise export directly.
        if (isTailSamplingEnabled()) {
            SpanProcessor exportDelegate = SpanProcessor.composite(exportProcessors);
            builder.addSpanProcessor(new TailSamplingSpanProcessor(exportDelegate, properties.getSampling().getTail()));
            log.info("Tail-based sampling enabled (holdWindowMs={}, latencyThresholdMs={}, keepRate={})",
                    properties.getSampling().getTail().getHoldWindowMs(),
                    properties.getSampling().getTail().getLatencyThresholdMs(),
                    properties.getSampling().getTail().getKeepRate());
        } else {
            exportProcessors.forEach(builder::addSpanProcessor);
        }

        // The metrics bridge observes every recorded span, independent of the sampling decision.
        if (metricsProcessor != null) {
            builder.addSpanProcessor(metricsProcessor);
            log.info("Span-to-RED metrics bridge enabled");
        }

        return builder.build();
    }

    /**
     * Whether tail-based sampling is selected (mode == "tail").
     */
    private boolean isTailSamplingEnabled() {
        return "tail".equalsIgnoreCase(properties.getSampling().getMode());
    }

    /**
     * Create resource attributes for the service.
     */
    private Resource createResource() {
        io.opentelemetry.sdk.resources.ResourceBuilder resourceBuilder = Resource.getDefault().toBuilder();

        // Service information
        String serviceName = environment.resolvePlaceholders(properties.getResource().getServiceName());
        resourceBuilder.put(ResourceAttributes.SERVICE_NAME, serviceName);
        resourceBuilder.put(ResourceAttributes.SERVICE_VERSION, properties.getResource().getServiceVersion());
        resourceBuilder.put(ResourceAttributes.SERVICE_NAMESPACE, properties.getResource().getServiceNamespace());

        String instanceId = environment.resolvePlaceholders(properties.getResource().getServiceInstanceId());
        resourceBuilder.put(ResourceAttributes.SERVICE_INSTANCE_ID, instanceId);

        // Additional custom attributes
        for (Map.Entry<String, String> entry : properties.getResource().getAttributes().entrySet()) {
            String resolvedValue = environment.resolvePlaceholders(entry.getValue());
            resourceBuilder.put(entry.getKey(), resolvedValue);
        }

        // Common tags from properties
        for (Map.Entry<String, String> entry : properties.getTags().entrySet()) {
            resourceBuilder.put("adhar." + entry.getKey(), entry.getValue());
        }

        Resource resource = resourceBuilder.build();
        log.debug("Created resource with attributes: {}", resource.getAttributes());

        return resource;
    }

    /**
     * Create the sampler based on configuration.
     */
    private Sampler createSampler() {
        // In tail-sampling mode the tail processor makes the keep/drop decision, so the head
        // sampler must record every span.
        if (isTailSamplingEnabled()) {
            log.info("Tail-based sampling active; head sampler set to alwaysOn so all spans are recorded");
            return Sampler.alwaysOn();
        }

        double probability = properties.getSampling().getProbability();

        if (probability <= 0.0) {
            log.info("Tracing sampling disabled (probability: {})", probability);
            return Sampler.alwaysOff();
        } else if (probability >= 1.0) {
            log.info("Tracing sampling set to always on (probability: {})", probability);
            return Sampler.alwaysOn();
        } else {
            log.info("Tracing sampling set to probability: {}", probability);
            return Sampler.traceIdRatioBased(probability);
        }
    }

    /**
     * Create span exporters based on configuration.
     */
    private List<SpanExporter> createExporters() {
        List<SpanExporter> exporters = new ArrayList<>();

        // OpenTelemetry OTLP exporter
        if (properties.getOpenTelemetry().isEnabled()) {
            SpanExporter otlpExporter = createOtlpExporter();
            if (otlpExporter != null) {
                exporters.add(otlpExporter);
                log.info("Configured OTLP span exporter to: {}", properties.getOpenTelemetry().getEndpoint());
            }
        }

        // Zipkin exporter
        if (properties.getZipkin().isEnabled()) {
            SpanExporter zipkinExporter = createZipkinExporter();
            if (zipkinExporter != null) {
                exporters.add(zipkinExporter);
                log.info("Configured Zipkin span exporter to: {}", properties.getZipkin().getBaseUrl());
            }
        }

        // Jaeger exporter (via OTLP)
        if (properties.getJaeger().isEnabled()) {
            SpanExporter jaegerExporter = createJaegerExporter();
            if (jaegerExporter != null) {
                exporters.add(jaegerExporter);
                log.info("Configured Jaeger span exporter to: {}",
                        properties.getJaeger().isUseGrpc() ?
                        properties.getJaeger().getGrpcEndpoint() :
                        properties.getJaeger().getHttpEndpoint());
            }
        }

        if (exporters.isEmpty()) {
            log.warn("No tracing exporters configured. Traces will not be exported.");
        }

        return exporters;
    }

    /**
     * Create OTLP exporter.
     */
    private SpanExporter createOtlpExporter() {
        try {
            var otlpProps = properties.getOpenTelemetry();

            if (otlpProps.isUseGrpc()) {
                var builder = OtlpGrpcSpanExporter.builder()
                        .setEndpoint(otlpProps.getEndpoint())
                        .setTimeout(Duration.parse(otlpProps.getTimeout()));

                // Add headers
                if (!otlpProps.getHeaders().isEmpty()) {
                    otlpProps.getHeaders().forEach(builder::addHeader);
                }

                if ("gzip".equals(otlpProps.getCompression())) {
                    builder.setCompression("gzip");
                }

                return builder.build();
            } else {
                // HTTP protocol: use gRPC exporter as HTTP exporter is not available in newer versions
                var builder = OtlpGrpcSpanExporter.builder()
                        .setEndpoint(otlpProps.getEndpoint())
                        .setTimeout(Duration.parse(otlpProps.getTimeout()));

                // Add headers
                if (!otlpProps.getHeaders().isEmpty()) {
                    otlpProps.getHeaders().forEach(builder::addHeader);
                }

                if ("gzip".equals(otlpProps.getCompression())) {
                    builder.setCompression("gzip");
                }

                return builder.build();
            }
        } catch (Exception e) {
            log.error("Failed to create OTLP exporter", e);
            return null;
        }
    }

    /**
     * Create Zipkin exporter.
     */
    private SpanExporter createZipkinExporter() {
        try {
            var zipkinProps = properties.getZipkin();

            return ZipkinSpanExporter.builder()
                    .setEndpoint(zipkinProps.getBaseUrl() + zipkinProps.getApiPath())
                    .setReadTimeout(Duration.parse(zipkinProps.getReadTimeout()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create Zipkin exporter", e);
            return null;
        }
    }

    /**
     * Create Jaeger exporter (via OTLP).
     */
    private SpanExporter createJaegerExporter() {
        try {
            var jaegerProps = properties.getJaeger();

            if (jaegerProps.isUseGrpc()) {
                return OtlpGrpcSpanExporter.builder()
                        .setEndpoint(jaegerProps.getGrpcEndpoint())
                        .setTimeout(Duration.parse(jaegerProps.getTimeout()))
                        .build();
            } else {
                // Use gRPC exporter as HTTP exporter is not available in newer versions
                return OtlpGrpcSpanExporter.builder()
                        .setEndpoint(jaegerProps.getHttpEndpoint())
                        .setTimeout(Duration.parse(jaegerProps.getTimeout()))
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to create Jaeger exporter", e);
            return null;
        }
    }

    /**
     * Create context propagators based on configuration.
     */
    private ContextPropagators createContextPropagators() {
        List<TextMapPropagator> propagators = new ArrayList<>();

        String propagationType = properties.getPropagation().getType();

        // Add primary propagator
        switch (propagationType.toLowerCase()) {
            case "tracecontext":
            case "w3c":
                propagators.add(W3CTraceContextPropagator.getInstance());
                break;
            case "b3":
                if (properties.getPropagation().isB3SingleHeader()) {
                    propagators.add(B3Propagator.injectingSingleHeader());
                } else {
                    propagators.add(B3Propagator.injectingMultiHeaders());
                }
                break;
            case "jaeger":
                propagators.add(JaegerPropagator.getInstance());
                break;
            default:
                log.warn("Unknown propagation type: {}. Using W3C TraceContext", propagationType);
                propagators.add(W3CTraceContextPropagator.getInstance());
        }

        // Add additional propagators
        for (String additionalFormat : properties.getPropagation().getAdditionalFormats()) {
            switch (additionalFormat.toLowerCase()) {
                case "b3":
                    propagators.add(B3Propagator.injectingMultiHeaders());
                    break;
                case "jaeger":
                    propagators.add(JaegerPropagator.getInstance());
                    break;
                case "tracecontext":
                case "w3c":
                    propagators.add(W3CTraceContextPropagator.getInstance());
                    break;
                default:
                    log.warn("Unknown additional propagation format: {}", additionalFormat);
            }
        }

        TextMapPropagator composite = TextMapPropagator.composite(propagators.toArray(new TextMapPropagator[0]));

        log.debug("Configured propagators: {} (primary: {})",
                propagators.size(), propagationType);

        return ContextPropagators.create(composite);
    }

    /**
     * Registers the {@link TraceContextMdcFilter}, which injects the current traceId/spanId
     * into the SLF4J MDC for the duration of each HTTP request.
     * <p>
     * This is a separate, class-level {@code @ConditionalOnClass}-gated configuration (rather
     * than a plain {@code @Bean} method on the outer class) so that when Servlet/Spring MVC
     * classes are absent from the classpath (e.g. a WebFlux-only application), Spring skips
     * this class entirely <em>before</em> it ever needs to resolve
     * {@link TraceContextMdcFilter}/{@link FilterRegistrationBean} as method signature types -
     * avoiding a {@code NoClassDefFoundError} during {@code @Configuration} class processing.
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"jakarta.servlet.Filter", "org.springframework.web.filter.OncePerRequestFilter"})
    @ConditionalOnProperty(prefix = "adhar.tracing.web", name = "mdc-enabled", havingValue = "true", matchIfMissing = true)
    public static class TraceContextMdcFilterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public FilterRegistrationBean<TraceContextMdcFilter> traceContextMdcFilterRegistration(
                AdharTracing adharTracing, AdharTracingProperties properties) {
            TraceContextMdcFilter filter = new TraceContextMdcFilter(adharTracing, properties.getWeb().getSkipPatterns());
            FilterRegistrationBean<TraceContextMdcFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            registration.addUrlPatterns("/*");
            registration.setName("traceContextMdcFilter");
            return registration;
        }
    }

    /**
     * Registers the {@link TracingServerSpanFilter}, which creates a {@code SERVER} span per
     * incoming HTTP request, joining any incoming remote trace. It is ordered <em>before</em>
     * {@link TraceContextMdcFilter} (lower order value) so the SERVER span is the current span
     * when the MDC filter publishes the traceId/spanId.
     * <p>
     * Gated on {@code adhar.tracing.web.server-spans-enabled}. When
     * {@code detect-existing-instrumentation} is true and any configured
     * {@code instrumentation-detection-classes} is on the classpath, the filter is registered
     * but left <em>disabled</em> to avoid emitting duplicate SERVER spans alongside a framework
     * instrumentation (e.g. the OpenTelemetry Java agent).
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"jakarta.servlet.Filter", "org.springframework.web.filter.OncePerRequestFilter"})
    @ConditionalOnProperty(prefix = "adhar.tracing.web", name = "server-spans-enabled", havingValue = "true", matchIfMissing = true)
    public static class TracingServerSpanFilterConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "tracingServerSpanFilterRegistration")
        public FilterRegistrationBean<TracingServerSpanFilter> tracingServerSpanFilterRegistration(
                OpenTelemetry openTelemetry, AdharTracingProperties properties) {
            AdharTracingProperties.WebTracingProperties web = properties.getWeb();
            TracingServerSpanFilter filter = new TracingServerSpanFilter(openTelemetry, web.getSkipPatterns());
            FilterRegistrationBean<TracingServerSpanFilter> registration = new FilterRegistrationBean<>(filter);
            // Before the MDC filter (HIGHEST_PRECEDENCE + 10) so the SERVER span is current.
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
            registration.addUrlPatterns("/*");
            registration.setName("tracingServerSpanFilter");

            if (web.isDetectExistingInstrumentation()
                    && isInstrumentationPresent(web.getInstrumentationDetectionClasses())) {
                log.info("Detected existing server-span instrumentation on the classpath; "
                        + "disabling TracingServerSpanFilter to avoid duplicate SERVER spans");
                registration.setEnabled(false);
            }

            return registration;
        }

        /**
         * Whether any of the given class names is present on the classpath.
         */
        static boolean isInstrumentationPresent(String[] classNames) {
            if (classNames == null) {
                return false;
            }
            ClassLoader classLoader = TracingServerSpanFilterConfiguration.class.getClassLoader();
            for (String className : classNames) {
                if (StringUtils.hasText(className) && ClassUtils.isPresent(className.trim(), classLoader)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Registers the {@link SpanMetricsProcessor} (span-to-RED-metrics bridge) as a bean when a
     * {@link MeterRegistry} is available and {@code adhar.tracing.metrics.enabled} is set. The
     * processor is consumed by {@link #openTelemetry(ObjectProvider)} and attached to the SDK
     * tracer provider.
     * <p>
     * This lives in a {@code @ConditionalOnClass(MeterRegistry.class)}-gated nested class so
     * that Micrometer-core-free applications skip it entirely before the {@link MeterRegistry}
     * method-signature type ever needs to be resolved.
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "adhar.tracing.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static class SpanMetricsConfiguration {

        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        @ConditionalOnMissingBean
        public SpanMetricsProcessor spanMetricsProcessor(MeterRegistry meterRegistry, AdharTracingProperties properties) {
            return new SpanMetricsProcessor(meterRegistry, properties.getMetrics());
        }
    }
}
