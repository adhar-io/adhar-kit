package com.adhar.kit.batch.telemetry;

import com.adhar.kit.batch.config.BatchAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The kit's batch telemetry (design 0025 §5.13), next to Spring Boot's own batch observations:
 * {@link BatchProgressMetrics} (what Spring Batch's meters lack), {@link BatchMdc} (the run in every
 * log line) and, when Micrometer Tracing is present, {@link ItemObservationTracingFilter} (no span
 * per item). Active under the same conditions as {@link BatchAutoConfiguration}.
 *
 * @since 0.1.0
 */
@AutoConfiguration(after = BatchAutoConfiguration.class, afterName = {
        "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration",
        "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration"})
@ConditionalOnClass(Job.class)
@ConditionalOnBooleanProperty(name = "adhar.batch.enabled", matchIfMissing = true)
@ConditionalOnBean(Job.class)
public class BatchTelemetryAutoConfiguration {

    /**
     * @param meterRegistry the application's meter registry
     * @return the kit's gauges and counter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    public BatchProgressMetrics batchProgressMetrics(MeterRegistry meterRegistry) {
        return new BatchProgressMetrics(meterRegistry);
    }

    /**
     * @return the observer putting the run into the logging MDC
     */
    @Bean
    @ConditionalOnMissingBean
    public BatchMdc batchMdc() {
        return new BatchMdc();
    }

    /** Only with Micrometer Tracing on the classpath. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.handler.TracingObservationHandler")
    static class Tracing {

        /**
         * @return the post-processor keeping per-item observations out of the traces
         */
        @Bean
        @ConditionalOnBooleanProperty(name = "adhar.batch.telemetry.trace-items", havingValue = false, matchIfMissing = true)
        static ItemObservationTracingFilter itemObservationTracingFilter() {
            return new ItemObservationTracingFilter();
        }
    }
}
