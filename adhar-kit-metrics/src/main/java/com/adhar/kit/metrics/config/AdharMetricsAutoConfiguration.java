package com.adhar.kit.metrics.config;

import com.adhar.kit.metrics.aspect.EnhancedMetricsAspect;
import com.adhar.kit.metrics.auto.JvmMetricsCollector;
import com.adhar.kit.metrics.auto.MetricsInterceptor;
import com.adhar.kit.metrics.auto.PlatformMetrics;
import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import com.adhar.kit.metrics.slo.SloRecorder;
import com.adhar.kit.metrics.trace.AdharPrometheusSpanContext;
import com.adhar.kit.metrics.trace.OpenTelemetryTraceContext;
import com.adhar.kit.metrics.trace.TraceContext;
import com.adhar.kit.metrics.util.AdharMetrics;
import com.adhar.kit.metrics.util.CgroupMetricsPoller;
import com.adhar.kit.metrics.util.KubernetesMetricsUtils;
import com.adhar.kit.metrics.util.MetricsUtils;
import com.adhar.kit.metrics.util.TagCardinalityLimiter;
import com.adhar.kit.metrics.web.HttpMetricsFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.instrument.binder.system.DiskSpaceMetrics;
import io.micrometer.core.instrument.config.MeterFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for Adhar Metrics.
 * <p>
 * This class sets up the metrics infrastructure based on the properties defined in {@link AdharMetricsProperties}.
 * It configures both Prometheus and OpenTelemetry metrics and provides the necessary beans for automated instrumentation.
 * </p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AdharMetricsProperties.class)
@ConditionalOnProperty(prefix = "adhar.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdharMetricsAutoConfiguration {

    private final AdharMetricsProperties properties;
    private final Environment environment;

    /**
     * Constructor for AdharMetricsAutoConfiguration.
     *
     * @param properties The metrics properties
     * @param environment The Spring environment
     */
    @Autowired
    public AdharMetricsAutoConfiguration(AdharMetricsProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
        log.info("Initializing Adhar Metrics with Prometheus and OpenTelemetry support");
    }

    /**
     * Configures common tags for all meter registries directly.
     * This is the Spring Boot 4.0 compatible approach without MeterRegistryCustomizer.
     */
    @Bean
    @ConditionalOnMissingBean(name = "metricsCommonTagsPostProcessor")
    public MeterRegistryPostProcessor metricsCommonTagsPostProcessor() {
        return registry -> {
            // Add application name tag
            String applicationName = environment.getProperty("spring.application.name", "unknown-application");
            registry.config().commonTags("application", applicationName);

            // Add environment tag
            String activeProfiles = String.join(",", environment.getActiveProfiles());
            if (activeProfiles.isEmpty()) {
                activeProfiles = environment.getProperty("spring.profiles.active", "default");
            }
            registry.config().commonTags("environment", activeProfiles);

            // Add custom common tags from properties
            List<Tag> tags = new ArrayList<>();
            for (Map.Entry<String, String> entry : properties.getCommonTags().entrySet()) {
                tags.add(Tag.of(entry.getKey(), entry.getValue()));
            }

            if (!tags.isEmpty()) {
                registry.config().commonTags(tags);
            }

            log.debug("Configured meter registry with common tags: application={}, environment={}, customTags={}",
                    applicationName, activeProfiles, properties.getCommonTags());
        };
    }

    /**
     * Configures Prometheus-specific settings.
     */
    @Bean
    @ConditionalOnClass(name = "io.micrometer.prometheus.PrometheusMeterRegistry")
    @ConditionalOnProperty(prefix = "adhar.metrics.prometheus", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MeterRegistryPostProcessor prometheusMetricsPostProcessor() {
        return registry -> {
            if (registry.getClass().getName().contains("PrometheusMeterRegistry")) {
                // Prometheus metrics configured
                log.debug("Configured Prometheus metrics registry");
            }
        };
    }

    /**
     * Provides JVM memory metrics if enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.jvm", name = "memory", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        log.debug("Registering JVM memory metrics");
        return new JvmMemoryMetrics();
    }

    /**
     * Provides JVM GC metrics if enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.jvm", name = "gc", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public JvmGcMetrics jvmGcMetrics() {
        log.debug("Registering JVM GC metrics");
        return new JvmGcMetrics();
    }

    /**
     * Provides JVM thread metrics if enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.jvm", name = "threads", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public JvmThreadMetrics jvmThreadMetrics() {
        log.debug("Registering JVM thread metrics");
        return new JvmThreadMetrics();
    }

    /**
     * Provides JVM class loader metrics if enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.jvm", name = "classLoader", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public ClassLoaderMetrics classLoaderMetrics() {
        log.debug("Registering JVM class loader metrics");
        return new ClassLoaderMetrics();
    }

    /**
     * Provides processor (CPU) metrics if enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.system", name = "processor", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public ProcessorMetrics processorMetrics() {
        log.debug("Registering processor metrics");
        return new ProcessorMetrics();
    }

    /**
     * Provides file descriptor metrics if enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.system", name = "fileDescriptor", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public FileDescriptorMetrics fileDescriptorMetrics() {
        log.debug("Registering file descriptor metrics");
        return new FileDescriptorMetrics();
    }

    /**
     * Provides uptime metrics if enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.system", name = "uptime", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public UptimeMetrics uptimeMetrics() {
        log.debug("Registering uptime metrics");
        return new UptimeMetrics();
    }

    /**
     * Provides disk space metrics if enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.system", name = "diskSpace", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public DiskSpaceMetrics diskSpaceMetrics() {
        log.debug("Registering disk space metrics");
        return new DiskSpaceMetrics(new File("."));
    }

    /**
     * Creates the AdharMetrics utility bean for manual metrics management.
     */
    @Bean
    @ConditionalOnMissingBean
    public AdharMetrics adharMetrics(MeterRegistry meterRegistry, MetricsUtils metricsUtils,
                                   @Autowired(required = false) KubernetesMetricsUtils kubernetesMetricsUtils) {
        log.debug("Initializing AdharMetrics utility");
        AdharMetrics.initialize(meterRegistry, properties, metricsUtils, kubernetesMetricsUtils);
        return new AdharMetrics();
    }

    /**
     * Creates an enhanced metrics aspect with support for all annotation types.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.application", name = "methodTiming", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "enhancedMetricsAspect")
    public EnhancedMetricsAspect enhancedMetricsAspectBean(MeterRegistry meterRegistry) {
        log.debug("Registering enhanced metrics aspect with support for all annotations");
        return new EnhancedMetricsAspect(meterRegistry);
    }

    /**
     * Creates the metrics utility bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricsUtils metricsUtils(MeterRegistry meterRegistry) {
        log.debug("Registering metrics utils");
        return new MetricsUtils(meterRegistry, properties);
    }

    /**
     * Creates the Kubernetes metrics utility bean if Kubernetes support is enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.kubernetes", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public KubernetesMetricsUtils kubernetesMetricsUtils(MeterRegistry meterRegistry) {
        log.debug("Registering Kubernetes metrics utils");
        return new KubernetesMetricsUtils(meterRegistry, properties);
    }

    /**
     * Configures controller request size recording filter.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.web", name = "recordRequestSize", havingValue = "false")
    public MeterFilter disableRequestSizeFilter() {
        return MeterFilter.deny(id -> id.getName().equals("http.server.requests") &&
                                     id.getTags().stream().anyMatch(tag -> "request.size".equals(tag.getKey())));
    }

    /**
     * Configures controller response size recording filter.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.web", name = "recordResponseSize", havingValue = "false")
    public MeterFilter disableResponseSizeFilter() {
        return MeterFilter.deny(id -> id.getName().equals("http.server.requests") &&
                                     id.getTags().stream().anyMatch(tag -> "response.size".equals(tag.getKey())));
    }

    /**
     * Configures URI tag limiting filter for controller metrics.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MeterFilter uriTagLimitFilter() {
        return MeterFilter.maximumAllowableTags("http.server.requests", "uri",
                properties.getWeb().getMaxUriTags(), MeterFilter.deny());
    }

    /**
     * Creates the tag cardinality limiter used to bound the distinct values of
     * high-cardinality tags (e.g. the uri tag of the HttpMetricsFilter).
     * The maximum is driven by {@code adhar.metrics.web.maxUriTags} (default 100).
     */
    @Bean
    @ConditionalOnMissingBean
    public TagCardinalityLimiter tagCardinalityLimiter() {
        log.debug("Registering TagCardinalityLimiter with max cardinality {}", properties.getWeb().getMaxUriTags());
        return new TagCardinalityLimiter(properties.getWeb().getMaxUriTags());
    }

    /**
     * Creates the SLO recorder that tracks error budgets and burn rates for the
     * targets configured under {@code adhar.metrics.slo.targets.*}.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.slo", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SloRecorder sloRecorder(MeterRegistry meterRegistry) {
        log.debug("Registering SloRecorder with {} target(s)", properties.getSlo().getTargets().size());
        return new SloRecorder(meterRegistry, properties.getSlo());
    }

    /**
     * Registers the HTTP metrics filter when running in a servlet web application.
     * Nested so the servlet API is only required when actually present on the classpath.
     */
    @Configuration
    @ConditionalOnClass(name = {"jakarta.servlet.Filter", "org.springframework.web.filter.OncePerRequestFilter"})
    public static class HttpMetricsFilterConfiguration {

        /**
         * Creates the servlet filter that records per-request timers with the
         * actual response status and a cardinality-safe uri tag.
         */
        @Bean
        @ConditionalOnProperty(prefix = "adhar.metrics.web", name = "enabled", havingValue = "true", matchIfMissing = true)
        @ConditionalOnMissingBean
        public HttpMetricsFilter httpMetricsFilter(MeterRegistry meterRegistry,
                                                   TagCardinalityLimiter tagCardinalityLimiter,
                                                   ObjectProvider<SloRecorder> sloRecorder,
                                                   ObjectProvider<TraceContext> traceContext) {
            return new HttpMetricsFilter(meterRegistry, tagCardinalityLimiter,
                    sloRecorder.getIfAvailable(), traceContext.getIfAvailable());
        }
    }

    // -------------------------------------------------------------------------
    // Trace Correlation / Exemplars (optional: gated on the tracing libraries)
    // -------------------------------------------------------------------------

    /**
     * Provides a {@link TraceContext} backed by the OpenTelemetry Span API when it is on the
     * classpath. The bean lets {@link HttpMetricsFilter} publish the current trace/span id to
     * the SLF4J MDC (cardinality-safe log/metric correlation) and feeds the Prometheus
     * exemplar bridge below.
     */
    @Configuration
    @ConditionalOnClass(name = "io.opentelemetry.api.trace.Span")
    public static class OpenTelemetryTraceConfiguration {

        /**
         * Creates the OpenTelemetry-backed trace context.
         *
         * @return the trace context bean
         */
        @Bean
        @ConditionalOnMissingBean(TraceContext.class)
        public TraceContext openTelemetryTraceContext() {
            log.debug("Registering OpenTelemetry-backed TraceContext for metric/trace correlation");
            return new OpenTelemetryTraceContext();
        }
    }

    /**
     * Provides a Prometheus exemplar {@code SpanContext} bean when both the Prometheus client
     * and a {@link TraceContext} are available. Spring Boot's Prometheus auto-configuration
     * wires this bean into the {@code PrometheusMeterRegistry}, attaching exemplars (carrying
     * the current trace id) to counters and histograms -- including the timers recorded by
     * {@link HttpMetricsFilter} and the metrics aspect -- without inflating cardinality.
     */
    @Configuration
    @ConditionalOnClass(name = "io.prometheus.metrics.tracer.common.SpanContext")
    public static class PrometheusExemplarConfiguration {

        /**
         * Creates the Prometheus exemplar bridge over the current {@link TraceContext}.
         *
         * @param traceContext the trace context to read the current span from
         * @return the Prometheus {@code SpanContext} bean
         */
        @Bean
        @ConditionalOnMissingBean(io.prometheus.metrics.tracer.common.SpanContext.class)
        public io.prometheus.metrics.tracer.common.SpanContext adharPrometheusSpanContext(
                TraceContext traceContext) {
            log.debug("Registering Prometheus exemplar SpanContext bridge for trace correlation");
            return new AdharPrometheusSpanContext(traceContext);
        }
    }

    /**
     * Creates and starts the scheduled container (cgroup) resource poller. Enabled via
     * {@code adhar.metrics.kubernetes.resource-polling.enabled}; it additionally auto-detects
     * the cgroup filesystem at runtime and stays idle when none is present. The poller reads
     * both cgroup v1 and v2 layouts and needs no Kubernetes API access.
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "adhar.metrics.kubernetes.resource-polling", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public CgroupMetricsPoller cgroupMetricsPoller(MeterRegistry meterRegistry,
                                                   ObjectProvider<KubernetesMetricsUtils> kubernetesMetricsUtils) {
        KubernetesMetricsUtils utils = kubernetesMetricsUtils.getIfAvailable(
                () -> new KubernetesMetricsUtils(meterRegistry, properties));
        CgroupMetricsPoller poller = new CgroupMetricsPoller(utils,
                properties.getKubernetes().getResourcePolling().getIntervalSeconds());
        poller.start();
        log.debug("Registered CgroupMetricsPoller (interval={}s)",
                properties.getKubernetes().getResourcePolling().getIntervalSeconds());
        return poller;
    }

    // -------------------------------------------------------------------------
    // Auto-Metrics Beans
    // -------------------------------------------------------------------------

    /**
     * Creates the PlatformMetrics singleton bean that provides pre-built metric names
     * and auto-records common metrics across all Adhar Kit modules.
     * <p>
     * The bean is also accessible as a static singleton via {@link PlatformMetrics#getInstance()}.
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public PlatformMetrics platformMetrics(MeterRegistry meterRegistry) {
        log.debug("Registering PlatformMetrics auto-collection bean");
        return new PlatformMetrics(meterRegistry);
    }

    /**
     * Creates the MetricsInterceptor AOP aspect that auto-instruments methods
     * annotated with {@code @Measured} or {@code @MonitorPerformance}.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.metrics.application", name = "methodTiming", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MetricsInterceptor metricsInterceptor(MeterRegistry meterRegistry) {
        log.debug("Registering MetricsInterceptor for @Measured and @MonitorPerformance auto-instrumentation");
        return new MetricsInterceptor(meterRegistry);
    }

    /**
     * Creates the JvmMetricsCollector that registers Adhar-prefixed JVM metrics
     * and collects them on a 15-second schedule using MXBeans.
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "adhar.metrics.jvm", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public JvmMetricsCollector jvmMetricsCollector(MeterRegistry meterRegistry) {
        log.debug("Registering JvmMetricsCollector with 15-second collection interval");
        return new JvmMetricsCollector(meterRegistry);
    }

    /**
     * Functional interface for post-processing meter registries.
     */
    @FunctionalInterface
    public interface MeterRegistryPostProcessor {
        void process(MeterRegistry registry);
    }

    /**
     * Applies all MeterRegistryPostProcessor beans to the MeterRegistry.
     */
    @Autowired(required = false)
    public void configureMeterRegistry(MeterRegistry meterRegistry,
                                      List<MeterRegistryPostProcessor> postProcessors) {
        if (postProcessors != null && !postProcessors.isEmpty()) {
            for (MeterRegistryPostProcessor postProcessor : postProcessors) {
                postProcessor.process(meterRegistry);
            }
        }
    }
}
