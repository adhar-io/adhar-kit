package com.adhar.adharkit.logging.encoder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MaskingJsonEncoder}.
 */
class MaskingJsonEncoderTest {

    private MaskingJsonEncoder encoder;
    private ByteArrayOutputStream outputStream;
    private OutputStreamAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // Create a new encoder for each test
        encoder = new MaskingJsonEncoder();
        
        // Set up a ByteArrayOutputStream to capture the encoded output
        outputStream = new ByteArrayOutputStream();
        
        // Create an appender that uses our encoder and writes to our output stream
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(outputStream);
        appender.start();
        
        // Create a logger that uses our appender
        logger = context.getLogger(MaskingJsonEncoderTest.class);
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        
        // Clear MDC before each test
        MDC.clear();
    }

    @Test
    void shouldMaskPasswordInLogMessage() {
        // Given
        String message = "User login failed with password=secret123";
        
        // When
        logger.info(message);
        appender.stop();
        
        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).doesNotContain("secret123");
        assertThat(output).contains("password=********");
    }

    @Test
    void shouldMaskMultipleSensitiveFieldsInLogMessage() {
        // Given
        String message = "API call with apiKey=abc123 and password=secret456";
        
        // When
        logger.info(message);
        appender.stop();
        
        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).doesNotContain("abc123");
        assertThat(output).doesNotContain("secret456");
        assertThat(output).contains("apiKey=********");
        assertThat(output).contains("password=********");
    }

    @Test
    void shouldMaskSensitiveDataWithDifferentSeparators() {
        // Given
        String message = "Credentials: password:secret123, token=abc456, apiKey: xyz789";
        
        // When
        logger.info(message);
        appender.stop();
        
        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).doesNotContain("secret123");
        assertThat(output).doesNotContain("abc456");
        assertThat(output).doesNotContain("xyz789");
        assertThat(output).contains("password:********");
        assertThat(output).contains("token=********");
        assertThat(output).contains("apiKey: ********");
    }

    @Test
    void shouldMaskSensitiveDataWithQuotes() {
        // Given
        String message = "User credentials: password=\"secret123\", token='abc456'";
        
        // When
        logger.info(message);
        appender.stop();
        
        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).doesNotContain("secret123");
        assertThat(output).doesNotContain("abc456");
        assertThat(output).contains("password=********");
        assertThat(output).contains("token=********");
    }

    @Test
    void shouldNotMaskNonSensitiveData() {
        // Given
        String message = "User profile: username=john.doe, email=john.doe@example.com";
        
        // When
        logger.info(message);
        appender.stop();
        
        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("username=john.doe");
        assertThat(output).contains("email=john.doe@example.com");
    }

    @Test
    void shouldMaskAdditionalKeysWhenConfigured() {
        // Given
        Set<String> additionalKeys = new HashSet<>(Arrays.asList("email", "phone"));
        encoder.addMaskedKeys(additionalKeys);
        String message = "User data: email=john.doe@example.com, phone=1234567890";
        
        // When
        logger.info(message);
        appender.stop();
        
        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).doesNotContain("john.doe@example.com");
        assertThat(output).doesNotContain("1234567890");
        assertThat(output).contains("email=********");
        assertThat(output).contains("phone=********");
    }

    @Test
    void shouldIncludeMdcValuesInOutput() {
        // Given
        MDC.put("correlationId", "test-correlation-id");
        MDC.put("userId", "test-user-id");
        String message = "Test message with MDC values";
        
        // When
        logger.info(message);
        appender.stop();
        
        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("correlationId");
        assertThat(output).contains("test-correlation-id");
        assertThat(output).contains("userId");
        assertThat(output).contains("test-user-id");
    }

    @Test
    void shouldMaskSensitiveDataInMdcValues() {
        // Given
        MDC.put("password", "secret123");
        MDC.put("token", "abc456");
        String message = "Test message with sensitive MDC values";
        
        // When
        logger.info(message);
        appender.stop();
        
        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).doesNotContain("secret123");
        assertThat(output).doesNotContain("abc456");
    }

    @Test
    void shouldHandleNullMessageGracefully() {
        // Given
        String message = null;
        
        // When
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setMessage(message);
        event.setLoggerName(logger.getName());
        appender.doAppend(event);
        appender.stop();
        
        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).isNotEmpty(); // Should not throw exception
    }

    @Test
    void shouldHandleExceptionsInLogMessages() {
        // Given
        Exception exception = new RuntimeException("Test exception with password=secret123");
        
        // When
        logger.error("Error occurred", exception);
        appender.stop();
        
        // Then
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Error occurred");
        assertThat(output).contains("Test exception");
        assertThat(output).doesNotContain("secret123");
        assertThat(output).contains("password=********");
    }
}