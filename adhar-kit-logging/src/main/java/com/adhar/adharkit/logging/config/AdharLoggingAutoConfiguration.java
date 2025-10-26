package com.adhar.adharkit.logging.config;

import com.adhar.adharkit.logging.aspect.*;
import com.adhar.adharkit.logging.encoder.MaskingJsonEncoder;
import com.adhar.adharkit.logging.filter.MdcLoggingFilter;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import com.adhar.adharkit.logging.util.AdharLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;

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
}
