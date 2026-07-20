package com.adhar.adharkit.logging.integration;

import com.adhar.adharkit.logging.config.AdharLoggingAutoConfiguration;
import com.adhar.adharkit.logging.encoder.MaskingJsonEncoder;
import com.adhar.adharkit.logging.filter.MdcLoggingFilter;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import com.adhar.adharkit.logging.util.AdharLogger;
import com.adhar.adharkit.logging.util.LoggingUtils;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Adhar Logging Starter.
 * These tests verify that all components work together correctly.
 */
class AdharLoggingIntegrationTest {

    // Provide a Jackson ObjectMapper (as Spring Boot's Jackson auto-configuration would in
    // a real application) so the logging beans that depend on it can be created.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class)
            .withConfiguration(AutoConfigurations.of(AdharLoggingAutoConfiguration.class));

    private static final Logger log = LoggerFactory.getLogger(AdharLoggingIntegrationTest.class);

    @Test
    void shouldLoadAutoConfiguration() {
        contextRunner
                .withPropertyValues("adhar.logging.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AdharLoggingAutoConfiguration.class);
                    assertThat(context).hasSingleBean(AdharLogger.class);
                    assertThat(context).hasSingleBean(MaskingJsonEncoder.class);
                });
    }

    @Test
    void shouldNotLoadAutoConfigurationWhenDisabled() {
        contextRunner
                .withPropertyValues("adhar.logging.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AdharLoggingAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(AdharLogger.class);
                    assertThat(context).doesNotHaveBean(MaskingJsonEncoder.class);
                });
    }

    @Test
    void shouldConfigureAdditionalMaskedKeys() {
        contextRunner
                .withPropertyValues(
                        "adhar.logging.enabled=true",
                        "adhar.logging.masking.additional-keys[0]=customKey1",
                        "adhar.logging.masking.additional-keys[1]=customKey2"
                )
                .run(context -> {
                    MaskingJsonEncoder encoder = context.getBean(MaskingJsonEncoder.class);
                    Set<String> maskedKeys = encoder.getMaskedKeys();
                    assertThat(maskedKeys).contains("customKey1", "customKey2");
                });
    }

    @Test
    void shouldConfigureMdcLoggingFilter() {
        contextRunner
                .withPropertyValues(
                        "adhar.logging.enabled=true",
                        "adhar.logging.mdc.enabled=true",
                        "adhar.logging.mdc.correlation-id-field=customCorrelationId"
                )
                .run(context -> {
                    AdharLoggingProperties properties = context.getBean(AdharLoggingProperties.class);
                    assertThat(properties.getMdc().isEnabled()).isTrue();
                    assertThat(properties.getMdc().getCorrelationIdField()).isEqualTo("customCorrelationId");
                });
    }

    @Test
    void shouldConfigureTracingProperties() {
        contextRunner
                .withPropertyValues(
                        "adhar.logging.enabled=true",
                        "adhar.logging.tracing.enabled=true",
                        "adhar.logging.tracing.trace-id-field=customTraceId",
                        "adhar.logging.tracing.span-id-field=customSpanId"
                )
                .run(context -> {
                    AdharLoggingProperties properties = context.getBean(AdharLoggingProperties.class);
                    assertThat(properties.getTracing().isEnabled()).isTrue();
                    assertThat(properties.getTracing().getTraceIdField()).isEqualTo("customTraceId");
                    assertThat(properties.getTracing().getSpanIdField()).isEqualTo("customSpanId");
                });
    }

    @Test
    void shouldProcessHttpRequestWithMdcFilter() throws Exception {
        // The MdcLoggingFilter is registered via a FilterRegistrationBean and only
        // in a web application context (@ConditionalOnWebApplication), so use a
        // WebApplicationContextRunner and unwrap the filter from its registration.
        new WebApplicationContextRunner()
                .withBean(ObjectMapper.class)
                .withConfiguration(AutoConfigurations.of(AdharLoggingAutoConfiguration.class))
                .withPropertyValues(
                        "adhar.logging.enabled=true",
                        "adhar.logging.mdc.enabled=true"
                )
                .run(context -> {
                    // Unwrap the filter from its FilterRegistrationBean (fetch by bean name:
                    // the context also registers the RestApiLoggingFilter registration)
                    @SuppressWarnings("unchecked")
                    FilterRegistrationBean<MdcLoggingFilter> registration =
                            (FilterRegistrationBean<MdcLoggingFilter>)
                                    context.getBean("mdcLoggingFilter", FilterRegistrationBean.class);
                    MdcLoggingFilter filter = registration.getFilter();
                    
                    // Create mock request and response
                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.addHeader("correlationId", "test-correlation-id");
                    request.addHeader("traceId", "test-trace-id");
                    request.addHeader("spanId", "test-span-id");
                    request.setMethod("GET");
                    request.setRequestURI("/api/test");
                    request.setRemoteAddr("127.0.0.1");
                    request.addHeader("User-Agent", "Test-Agent");
                    
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    MockFilterChain filterChain = new MockFilterChain();
                    
                    // Process the request
                    filter.doFilter(request, response, filterChain);
                    
                    // Verify response headers
                    assertThat(response.getHeader("correlationId")).isEqualTo("test-correlation-id");
                    assertThat(response.getHeader("traceId")).isEqualTo("test-trace-id");
                    assertThat(response.getHeader("spanId")).isEqualTo("test-span-id");
                    
                    // MDC should be cleared after request processing
                    assertThat(MDC.getCopyOfContextMap()).isNull();
                });
    }

    @Test
    void shouldMaskSensitiveDataInLogs() {
        contextRunner
                .withPropertyValues(
                        "adhar.logging.enabled=true",
                        "adhar.logging.masking.enabled=true"
                )
                .run(context -> {
                    // Wire the context's MaskingJsonEncoder into a logback appender so
                    // the captured output actually flows through the masking encoder
                    // (creating the bean alone does not reconfigure logback).
                    MaskingJsonEncoder encoder = context.getBean(MaskingJsonEncoder.class);
                    LoggingUtils loggingUtils = context.getBean(LoggingUtils.class);

                    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    encoder.setContext(loggerContext);
                    encoder.start();
                    OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
                    appender.setContext(loggerContext);
                    appender.setEncoder(encoder);
                    appender.setOutputStream(outputStream);
                    appender.start();

                    ch.qos.logback.classic.Logger maskingLogger =
                            loggerContext.getLogger("masking-it-logger");
                    maskingLogger.addAppender(appender);
                    maskingLogger.setLevel(Level.INFO);
                    maskingLogger.setAdditive(false);

                    try {
                        // Set correlation ID and other MDC values (included in JSON output)
                        loggingUtils.setCorrelationId("test-correlation-id");
                        loggingUtils.putMdc("testKey", "testValue");

                        // Log a message with sensitive data
                        maskingLogger.info("User authentication with password=secret123 and token=abc456");
                        appender.stop();

                        // Verify log output is masked and includes MDC context
                        String logOutput = outputStream.toString(StandardCharsets.UTF_8);
                        assertThat(logOutput).doesNotContain("secret123");
                        assertThat(logOutput).doesNotContain("abc456");
                        assertThat(logOutput).contains("password=********");
                        assertThat(logOutput).contains("token=********");
                        assertThat(logOutput).contains("test-correlation-id");
                        assertThat(logOutput).contains("testKey");
                        assertThat(logOutput).contains("testValue");
                    } finally {
                        maskingLogger.detachAndStopAllAppenders();
                        MDC.clear();
                    }
                });
    }

    @Test
    void shouldAddCustomMaskedKeys() {
        contextRunner
                .withPropertyValues(
                        "adhar.logging.enabled=true",
                        "adhar.logging.masking.enabled=true"
                )
                .run(context -> {
                    // Get the encoder and register custom masked keys
                    MaskingJsonEncoder encoder = context.getBean(MaskingJsonEncoder.class);
                    Set<String> additionalKeys = new HashSet<>();
                    additionalKeys.add("customSecret");
                    additionalKeys.add("sensitiveData");
                    encoder.addMaskedKeys(additionalKeys);

                    // Verify the keys were added
                    Set<String> maskedKeys = encoder.getMaskedKeys();
                    assertThat(maskedKeys).contains("customSecret", "sensitiveData");

                    // Wire the encoder into a logback appender to capture masked output
                    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    encoder.setContext(loggerContext);
                    encoder.start();
                    OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
                    appender.setContext(loggerContext);
                    appender.setEncoder(encoder);
                    appender.setOutputStream(outputStream);
                    appender.start();

                    ch.qos.logback.classic.Logger maskingLogger =
                            loggerContext.getLogger("custom-masking-it-logger");
                    maskingLogger.addAppender(appender);
                    maskingLogger.setLevel(Level.INFO);
                    maskingLogger.setAdditive(false);

                    try {
                        // Log a message with the custom sensitive data
                        maskingLogger.info("Custom data: customSecret=mySecret, sensitiveData=sensitive123");
                        appender.stop();

                        // Verify log output is masked for the custom keys
                        String logOutput = outputStream.toString(StandardCharsets.UTF_8);
                        assertThat(logOutput).doesNotContain("mySecret");
                        assertThat(logOutput).doesNotContain("sensitive123");
                        assertThat(logOutput).contains("customSecret=********");
                        assertThat(logOutput).contains("sensitiveData=********");
                    } finally {
                        maskingLogger.detachAndStopAllAppenders();
                    }
                });
    }

    /**
     * Test configuration for integration tests.
     */
    @Configuration
    static class TestConfig {
        
        @Bean
        public AdharLoggingProperties adharLoggingProperties() {
            return new AdharLoggingProperties();
        }
    }
}