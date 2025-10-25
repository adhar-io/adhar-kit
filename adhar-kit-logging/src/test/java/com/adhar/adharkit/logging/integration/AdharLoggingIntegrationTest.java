package com.adhar.adharkit.logging.integration;

import com.adhar.adharkit.logging.config.AdharLoggingAutoConfiguration;
import com.adhar.adharkit.logging.encoder.MaskingJsonEncoder;
import com.adhar.adharkit.logging.filter.MdcLoggingFilter;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import com.adhar.adharkit.logging.util.LoggingUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Adhar Logging Starter.
 * These tests verify that all components work together correctly.
 */
class AdharLoggingIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdharLoggingAutoConfiguration.class));

    private static final Logger log = LoggerFactory.getLogger(AdharLoggingIntegrationTest.class);

    @Test
    void shouldLoadAutoConfiguration() {
        contextRunner
                .withPropertyValues("adhar.logging.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AdharLoggingAutoConfiguration.class);
                    assertThat(context).hasSingleBean(LoggingUtils.class);
                    assertThat(context).hasSingleBean(MaskingJsonEncoder.class);
                });
    }

    @Test
    void shouldNotLoadAutoConfigurationWhenDisabled() {
        contextRunner
                .withPropertyValues("adhar.logging.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AdharLoggingAutoConfiguration.class);
                    assertThat(context).doesNotHaveBean(LoggingUtils.class);
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
        contextRunner
                .withPropertyValues(
                        "adhar.logging.enabled=true",
                        "adhar.logging.mdc.enabled=true"
                )
                .run(context -> {
                    // Get the filter from the context
                    MdcLoggingFilter filter = context.getBean(MdcLoggingFilter.class);
                    
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
                    // Capture System.out to verify log output
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    PrintStream originalOut = System.out;
                    System.setOut(new PrintStream(outputStream));
                    
                    try {
                        // Get the logging utils
                        LoggingUtils loggingUtils = context.getBean(LoggingUtils.class);
                        
                        // Set correlation ID and other MDC values
                        loggingUtils.setCorrelationId("test-correlation-id");
                        loggingUtils.putMdc("testKey", "testValue");
                        
                        // Log a message with sensitive data
                        log.info("User authentication with password=secret123 and token=abc456");
                        
                        // Verify log output
                        String logOutput = outputStream.toString();
                        assertThat(logOutput).doesNotContain("secret123");
                        assertThat(logOutput).doesNotContain("abc456");
                        assertThat(logOutput).contains("password=********");
                        assertThat(logOutput).contains("token=********");
                        assertThat(logOutput).contains("test-correlation-id");
                        assertThat(logOutput).contains("testKey");
                        assertThat(logOutput).contains("testValue");
                    } finally {
                        // Restore System.out
                        System.setOut(originalOut);
                        
                        // Clear MDC
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
                    // Get the encoder
                    MaskingJsonEncoder encoder = context.getBean(MaskingJsonEncoder.class);
                    
                    // Add custom masked keys
                    Set<String> additionalKeys = new HashSet<>();
                    additionalKeys.add("customSecret");
                    additionalKeys.add("sensitiveData");
                    encoder.addMaskedKeys(additionalKeys);
                    
                    // Verify the keys were added
                    Set<String> maskedKeys = encoder.getMaskedKeys();
                    assertThat(maskedKeys).contains("customSecret", "sensitiveData");
                    
                    // Capture System.out to verify log output
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    PrintStream originalOut = System.out;
                    System.setOut(new PrintStream(outputStream));
                    
                    try {
                        // Log a message with the custom sensitive data
                        log.info("Custom data: customSecret=mySecret, sensitiveData=sensitive123");
                        
                        // Verify log output
                        String logOutput = outputStream.toString();
                        assertThat(logOutput).doesNotContain("mySecret");
                        assertThat(logOutput).doesNotContain("sensitive123");
                        assertThat(logOutput).contains("customSecret=********");
                        assertThat(logOutput).contains("sensitiveData=********");
                    } finally {
                        // Restore System.out
                        System.setOut(originalOut);
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