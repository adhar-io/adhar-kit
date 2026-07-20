package com.adhar.adharkit.logging.config;

import com.adhar.adharkit.logging.aspect.*;
import com.adhar.adharkit.logging.audit.AuditEventLogger;
import com.adhar.adharkit.logging.batch.BatchJobLogger;
import com.adhar.adharkit.logging.encoder.MaskingJsonEncoder;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventSink;
import com.adhar.adharkit.logging.event.BusinessEventLogger;
import com.adhar.adharkit.logging.event.Slf4jAppLogEventSink;
import com.adhar.adharkit.logging.filter.MdcLoggingFilter;
import com.adhar.adharkit.logging.filter.RestApiLoggingFilter;
import com.adhar.adharkit.logging.interceptor.RestApiLoggingInterceptor;
import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.performance.PerformanceLogger;
import com.adhar.adharkit.logging.processor.LoggerInjectionBeanPostProcessor;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import com.adhar.adharkit.logging.util.AdharLogger;
import com.adhar.adharkit.logging.util.LoggingUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for Adhar logging framework.
 * <p>
 * This class provides automatic configuration for the Adhar logging system when included in a Spring Boot application.
 * It sets up the necessary beans for standardized enterprise logging, including:
 * <ul>
 *   <li>AdharLogger - Consolidated logging utility for microservices</li>
 *   <li>Sensitive data masking in logs</li>
 *   <li>MDC (Mapped Diagnostic Context) support for correlation IDs and user information</li>
 *   <li>Distributed tracing integration (traceId, spanId)</li>
 *   <li>Configurable log formatting and appenders</li>
 *   <li>AOP-based logging aspects for methods annotated with logging annotations</li>
 * </ul>
 * <p>
 * The auto-configuration can be enabled or disabled using the {@code adhar.logging.enabled} property.
 * By default, it is enabled.
 * <p>
 * Usage example in application.yml:
 * <pre>
 * adhar:
 *   logging:
 *     enabled: true
 *     aspects:
 *       enabled: true
 *     masking:
 *       enabled: true
 *       additional-keys:
 *         - customSecret
 *         - apiKey
 *     mdc:
 *       enabled: true
 *       correlation-id-field: correlationId
 *     tracing:
 *       enabled: true
 *       trace-id-field: traceId
 *       span-id-field: spanId
 * </pre>
 * 
 * @see com.adhar.adharkit.logging.properties.AdharLoggingProperties
 * @see com.adhar.adharkit.logging.util.AdharLogger
 * @see com.adhar.adharkit.logging.encoder.MaskingJsonEncoder
 * @see com.adhar.adharkit.logging.filter.MdcLoggingFilter
 */
@Configuration
@EnableConfigurationProperties(AdharLoggingProperties.class)
@EnableAspectJAutoProxy
@ConditionalOnProperty(prefix = "adhar.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdharLoggingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AdharLoggingAutoConfiguration.class);

    private final AdharLoggingProperties properties;

    /**
     * Constructor for AdharLoggingAutoConfiguration.
     * <p>
     * Initializes the auto-configuration with the provided properties.
     * During initialization, it checks if the Janino library is available on the classpath.
     * Janino is used for conditional processing in logback configuration files, which enables
     * more advanced logging configurations like conditional appenders.
     *
     * @param properties the AdharLoggingProperties containing configuration settings
     */
    public AdharLoggingAutoConfiguration(AdharLoggingProperties properties) {
        this.properties = properties;
        log.info("Initializing Adhar Logging Auto Configuration with aspects: {}, masking: {}, tracing: {}",
            properties.getAspects().isEnabled(),
            properties.getMasking().isEnabled(),
            properties.getTracing().isEnabled());

        // Check if Janino is available for conditional processing in logback
        try {
            Class.forName("org.codehaus.janino.ExpressionEvaluator");
            properties.setJaninoAvailable(true);
            log.debug("Janino is available for conditional processing in logback");
        } catch (ClassNotFoundException e) {
            properties.setJaninoAvailable(false);
            log.debug("Janino is not available for conditional processing in logback");
        }
    }

    /**
     * Creates a MaskingJsonEncoder bean if masking is enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.logging.masking", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MaskingJsonEncoder maskingJsonEncoder() {
        log.debug("Creating MaskingJsonEncoder bean");
        MaskingJsonEncoder encoder = new MaskingJsonEncoder();

        // Add any additional keys to mask
        if (!properties.getMasking().getAdditionalKeys().isEmpty()) {
            encoder.addMaskedKeys(properties.getMasking().getAdditionalKeys());
            log.debug("Added {} additional keys to mask", properties.getMasking().getAdditionalKeys().size());
        }

        // Configure JSON encoder properties
        if (properties.getJsonEncoder() != null) {
            // Custom fields
            if (properties.getJsonEncoder().getCustomFields() != null && !properties.getJsonEncoder().getCustomFields().isEmpty()) {
                StringBuilder customFieldsJson = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<String, String> entry : properties.getJsonEncoder().getCustomFields().entrySet()) {
                    if (!first) {
                        customFieldsJson.append(",");
                    }
                    customFieldsJson.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                    first = false;
                }
                customFieldsJson.append("}");
                encoder.setCustomFields(customFieldsJson.toString());
                log.debug("Set custom fields for JSON encoder: {}", customFieldsJson);
            }

            // Include MDC
            encoder.setIncludeMdc(properties.getJsonEncoder().isIncludeMdc());
            log.debug("Set includeMdc to {} for JSON encoder", properties.getJsonEncoder().isIncludeMdc());
        }

        return encoder;
    }

    /**
     * Creates an AdharLogger bean with Tracer if available.
     */
    @Bean
    @ConditionalOnBean(Tracer.class)
    @ConditionalOnMissingBean
    public AdharLogger adharLoggerWithTracer(Tracer tracer, ObjectMapper objectMapper) {
        log.info("Creating AdharLogger bean with Tracer support");
        return new AdharLogger(properties, tracer, objectMapper);
    }

    /**
     * Creates an AdharLogger bean without Tracer.
     */
    @Bean
    @ConditionalOnMissingBean({Tracer.class, AdharLogger.class})
    public AdharLogger adharLogger(ObjectMapper objectMapper) {
        log.info("Creating AdharLogger bean without Tracer");
        return new AdharLogger(properties, null, objectMapper);
    }

    /**
     * Creates a {@link LoggingUtils} bean for programmatic access to correlation
     * IDs, MDC management and tracing helpers. Uses the {@link Tracer} when one is
     * available on the classpath/context.
     */
    @Bean
    @ConditionalOnMissingBean
    public LoggingUtils loggingUtils(ObjectProvider<Tracer> tracerProvider) {
        Tracer tracer = tracerProvider.getIfAvailable();
        log.debug("Creating LoggingUtils bean (tracer present: {})", tracer != null);
        return tracer != null
                ? new LoggingUtils(properties, tracer)
                : new LoggingUtils(properties);
    }

    /**
     * Creates a MdcLoggingFilter bean for controller applications if MDC logging is enabled.
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "adhar.logging.mdc", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilter(AdharLogger adharLogger) {
        log.debug("Creating MdcLoggingFilter bean");
        MdcLoggingFilter filter = new MdcLoggingFilter(properties, adharLogger);
        FilterRegistrationBean<MdcLoggingFilter> registrationBean = new FilterRegistrationBean<>(filter);
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    /**
     * Creates logging aspect beans if AOP-based logging is enabled.
     */
    @Bean
    @ConditionalOnProperty(prefix = "adhar.logging.aspects", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public LoggableAspect loggableAspect(ObjectMapper objectMapper) {
        log.debug("Creating LoggableAspect bean");
        return new LoggableAspect(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "adhar.logging.aspects", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public LogExecutionTimeAspect logExecutionTimeAspect(ObjectMapper objectMapper) {
        log.debug("Creating LogExecutionTimeAspect bean");
        return new LogExecutionTimeAspect(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "adhar.logging.aspects", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public LogExceptionsAspect logExceptionsAspect(ObjectMapper objectMapper) {
        log.debug("Creating LogExceptionsAspect bean");
        return new LogExceptionsAspect(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "adhar.logging.aspects", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public AuditAspect auditAspect(ObjectMapper objectMapper) {
        log.debug("Creating AuditAspect bean");
        return new AuditAspect(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "adhar.logging.aspects", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public LogMetricsAspect logMetricsAspect(ObjectMapper objectMapper) {
        log.debug("Creating LogMetricsAspect bean");
        return new LogMetricsAspect(objectMapper);
    }

    // ==================== AppLogEvent pipeline ====================

    /**
     * Creates the central data masker used by the event pipeline, API logging and audit logging.
     */
    @Bean
    @ConditionalOnMissingBean
    public LogDataMasker logDataMasker() {
        log.debug("Creating LogDataMasker bean (strategy: {})", properties.getMasking().getStrategy());
        return new LogDataMasker(properties.getMasking());
    }

    /**
     * Creates the default SLF4J sink that writes AppLogEvents as JSON to per-type loggers.
     */
    @Bean
    @ConditionalOnMissingBean(AppLogEventSink.class)
    public Slf4jAppLogEventSink slf4jAppLogEventSink(ObjectMapper objectMapper) {
        log.debug("Creating Slf4jAppLogEventSink bean (prefix: {})", properties.getEvents().getLoggerPrefix());
        return new Slf4jAppLogEventSink(properties, objectMapper);
    }

    /**
     * Creates the AppLogEvent publisher that enriches, masks and dispatches events to all sinks.
     */
    @Bean
    @ConditionalOnMissingBean
    public AppLogEventPublisher appLogEventPublisher(LogDataMasker masker, List<AppLogEventSink> sinks) {
        log.info("Creating AppLogEventPublisher bean with {} sink(s)", sinks.size());
        return new AppLogEventPublisher(properties, masker, sinks);
    }

    /**
     * Creates the business event / operation tracking API.
     */
    @Bean
    @ConditionalOnMissingBean
    public BusinessEventLogger businessEventLogger(AppLogEventPublisher publisher) {
        log.debug("Creating BusinessEventLogger bean");
        return new BusinessEventLogger(publisher);
    }

    /**
     * Creates the programmatic audit trail API.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.logging.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AuditEventLogger auditEventLogger(AppLogEventPublisher publisher, LogDataMasker masker) {
        log.debug("Creating AuditEventLogger bean");
        return new AuditEventLogger(properties, publisher, masker);
    }

    /**
     * Creates the batch job logging API.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.logging.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
    public BatchJobLogger batchJobLogger(AppLogEventPublisher publisher) {
        log.debug("Creating BatchJobLogger bean");
        return new BatchJobLogger(properties, publisher);
    }

    /**
     * Creates the performance logging API (timers, aggregated stats, slow-operation detection).
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.logging.performance", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PerformanceLogger performanceLogger(AppLogEventPublisher publisher) {
        log.debug("Creating PerformanceLogger bean");
        return new PerformanceLogger(properties, publisher);
    }

    /**
     * Creates the BeanPostProcessor that injects loggers into @InjectLogger fields.
     */
    @Bean
    @ConditionalOnMissingBean
    public LoggerInjectionBeanPostProcessor loggerInjectionBeanPostProcessor(
            ObjectProvider<AdharLogger> adharLoggerProvider) {
        log.debug("Creating LoggerInjectionBeanPostProcessor bean");
        return new LoggerInjectionBeanPostProcessor(adharLoggerProvider);
    }

    // ==================== New event-driven aspects ====================

    @Bean
    @ConditionalOnProperty(prefix = "adhar.logging.aspects", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public LogOperationAspect logOperationAspect(AppLogEventPublisher publisher, LogDataMasker masker) {
        log.debug("Creating LogOperationAspect bean");
        return new LogOperationAspect(publisher, masker);
    }

    @Bean
    @ConditionalOnProperty(prefix = "adhar.logging.aspects", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public BusinessEventAspect businessEventAspect(AppLogEventPublisher publisher, LogDataMasker masker) {
        log.debug("Creating BusinessEventAspect bean");
        return new BusinessEventAspect(publisher, masker);
    }

    @Bean
    @ConditionalOnProperty(prefix = "adhar.logging.aspects", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public TrackPerformanceAspect trackPerformanceAspect(PerformanceLogger performanceLogger) {
        log.debug("Creating TrackPerformanceAspect bean");
        return new TrackPerformanceAspect(performanceLogger);
    }

    @Bean
    @ConditionalOnProperty(prefix = "adhar.logging.aspects", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public LogBatchJobAspect logBatchJobAspect(BatchJobLogger batchJobLogger) {
        log.debug("Creating LogBatchJobAspect bean");
        return new LogBatchJobAspect(batchJobLogger);
    }

    // ==================== REST API logging (web applications only) ====================

    /**
     * Registers the REST API logging filter right after the MDC filter so API events inherit the
     * full MDC context (correlation ID, user, tracing).
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "adhar.logging.rest-api", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<RestApiLoggingFilter> restApiLoggingFilter(
            AppLogEventPublisher publisher, LogDataMasker masker) {
        log.debug("Creating RestApiLoggingFilter bean");
        RestApiLoggingFilter filter = new RestApiLoggingFilter(properties, publisher, masker);
        FilterRegistrationBean<RestApiLoggingFilter> registrationBean = new FilterRegistrationBean<>(filter);
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registrationBean;
    }

    /**
     * Registers the handler interceptor that tracks controller execution when Spring MVC is on
     * the classpath.
     */
    @Configuration
    @ConditionalOnWebApplication
    @ConditionalOnClass(WebMvcConfigurer.class)
    @ConditionalOnProperty(prefix = "adhar.logging.rest-api", name = "interceptor-enabled", havingValue = "true", matchIfMissing = true)
    static class RestApiInterceptorConfiguration implements WebMvcConfigurer {

        private final AppLogEventPublisher publisher;

        RestApiInterceptorConfiguration(AppLogEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new RestApiLoggingInterceptor(publisher));
        }
    }
}
