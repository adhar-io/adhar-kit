package com.adhar.kit.commons.exception;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTest {

    // ==================== AdharException ====================

    @Test
    void adharException_withMessage_shouldSetMessage() {
        AdharException ex = new AdharException("something failed");
        assertThat(ex.getMessage()).isEqualTo("something failed");
        assertThat(ex.getErrorCode()).isNull();
        assertThat(ex.getArgs()).isNull();
    }

    @Test
    void adharException_withMessageAndCause_shouldSetBoth() {
        Throwable cause = new RuntimeException("root cause");
        AdharException ex = new AdharException("failed", cause);
        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void adharException_withCodeAndMessage_shouldSetErrorCode() {
        AdharException ex = new AdharException("ERR001", "something failed");
        assertThat(ex.getErrorCode()).isEqualTo("ERR001");
        assertThat(ex.getMessage()).isEqualTo("something failed");
    }

    @Test
    void adharException_withCodeMessageAndCause_shouldSetAll() {
        Throwable cause = new RuntimeException("root");
        AdharException ex = new AdharException("ERR001", "failed", cause);
        assertThat(ex.getErrorCode()).isEqualTo("ERR001");
        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void adharException_withArgs_shouldStoreArgs() {
        AdharException ex = new AdharException("ERR001", "failed", "arg1", "arg2");
        assertThat(ex.getArgs()).containsExactly("arg1", "arg2");
    }

    @Test
    void adharException_withCauseAndArgs_shouldStoreAll() {
        Throwable cause = new RuntimeException("root");
        AdharException ex = new AdharException("ERR001", "failed", cause, "arg1");
        assertThat(ex.getErrorCode()).isEqualTo("ERR001");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getArgs()).containsExactly("arg1");
    }

    // ==================== BusinessException ====================

    @Test
    void businessException_withCodeAndMessage_shouldCreate() {
        BusinessException ex = new BusinessException("BIZ001", "Business rule violated");
        assertThat(ex.getErrorCode()).isEqualTo("BIZ001");
        assertThat(ex.getMessage()).isEqualTo("Business rule violated");
        assertThat(ex.getBusinessRule()).isNull();
    }

    @Test
    void businessException_withDetails_shouldCreate() {
        BusinessException ex = new BusinessException("BIZ001", "msg", "details");
        assertThat(ex.getErrorCode()).isEqualTo("BIZ001");
        assertThat(ex.getMessage()).isEqualTo("msg");
    }

    @Test
    void businessException_withBusinessRule_shouldCreate() {
        BusinessException ex = new BusinessException("BIZ001", "msg", "details", "rule1");
        assertThat(ex.getBusinessRule()).isEqualTo("rule1");
    }

    @Test
    void businessException_withCause_shouldSetCause() {
        Throwable cause = new RuntimeException("root");
        BusinessException ex = new BusinessException("BIZ001", "msg", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void businessException_insufficientFunds_shouldCreateCorrectly() {
        BusinessException ex = BusinessException.insufficientFunds("ACC-123", "100.00");
        assertThat(ex.getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(ex.getMessage()).contains("Insufficient funds");
        assertThat(ex.getBusinessRule()).isNotNull();
    }

    @Test
    void businessException_duplicateEntry_shouldCreateCorrectly() {
        BusinessException ex = BusinessException.duplicateEntry("User", "john@example.com");
        assertThat(ex.getErrorCode()).isEqualTo("DUPLICATE_ENTRY");
        assertThat(ex.getMessage()).contains("User already exists");
    }

    @Test
    void businessException_invalidStateTransition_shouldCreateCorrectly() {
        BusinessException ex = BusinessException.invalidStateTransition("PENDING", "SHIPPED", "Not paid");
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_STATE_TRANSITION");
        assertThat(ex.getMessage()).contains("PENDING").contains("SHIPPED");
    }

    // ==================== ResourceNotFoundException ====================

    @Test
    void resourceNotFoundException_withMessage_shouldUseDefaultCode() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");
        assertThat(ex.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("User not found");
    }

    @Test
    void resourceNotFoundException_withResourceTypeAndId_shouldFormatMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User", 123);
        assertThat(ex.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("User not found with id: 123");
    }

    @Test
    void resourceNotFoundException_withCause_shouldSetCause() {
        Throwable cause = new RuntimeException("db error");
        ResourceNotFoundException ex = new ResourceNotFoundException("not found", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void resourceNotFoundException_withCustomCode_shouldUseCustomCode() {
        ResourceNotFoundException ex = new ResourceNotFoundException("CUSTOM_404", "custom msg");
        assertThat(ex.getErrorCode()).isEqualTo("CUSTOM_404");
    }

    @Test
    void resourceNotFoundException_withCustomCodeAndCause_shouldSetBoth() {
        Throwable cause = new RuntimeException("root");
        ResourceNotFoundException ex = new ResourceNotFoundException("CUSTOM", "msg", cause);
        assertThat(ex.getErrorCode()).isEqualTo("CUSTOM");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    // ==================== ServiceException ====================

    @Test
    void serviceException_withMessage_shouldUseDefaultCode() {
        ServiceException ex = new ServiceException("service failed");
        assertThat(ex.getErrorCode()).isEqualTo("SERVICE_ERROR");
        assertThat(ex.getMessage()).isEqualTo("service failed");
    }

    @Test
    void serviceException_withCause_shouldSetCause() {
        Throwable cause = new RuntimeException("root");
        ServiceException ex = new ServiceException("failed", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void serviceException_withCustomCode_shouldUseCustomCode() {
        ServiceException ex = new ServiceException("SVC001", "custom error");
        assertThat(ex.getErrorCode()).isEqualTo("SVC001");
    }

    @Test
    void serviceException_withArgs_shouldStoreArgs() {
        ServiceException ex = new ServiceException("SVC001", "error", "a1", "a2");
        assertThat(ex.getArgs()).containsExactly("a1", "a2");
    }

    @Test
    void serviceException_withCauseAndArgs_shouldStoreAll() {
        Throwable cause = new RuntimeException("root");
        ServiceException ex = new ServiceException("SVC001", "error", cause, "a1");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getArgs()).containsExactly("a1");
    }

    // ==================== ValidationException ====================

    @Test
    void validationException_withMessage_shouldUseDefaultCode() {
        ValidationException ex = new ValidationException("validation failed");
        assertThat(ex.getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(ex.getMessage()).isEqualTo("validation failed");
        assertThat(ex.getValidationErrors()).isNull();
        assertThat(ex.getFieldErrors()).isNull();
    }

    @Test
    void validationException_withValidationErrors_shouldStoreErrors() {
        List<String> errors = List.of("name required", "age invalid");
        ValidationException ex = new ValidationException("failed", errors);
        assertThat(ex.getValidationErrors()).containsExactly("name required", "age invalid");
    }

    @Test
    void validationException_withFieldErrors_shouldStoreErrors() {
        Map<String, String> fieldErrors = Map.of("name", "required");
        ValidationException ex = new ValidationException("failed", fieldErrors);
        assertThat(ex.getFieldErrors()).containsEntry("name", "required");
    }

    @Test
    void validationException_withCustomCode_shouldUseCustomCode() {
        ValidationException ex = new ValidationException("CUSTOM_VAL", "msg");
        assertThat(ex.getErrorCode()).isEqualTo("CUSTOM_VAL");
    }

    @Test
    void validationException_withCustomCodeAndValidationErrors_shouldStoreAll() {
        List<String> errors = List.of("err1");
        ValidationException ex = new ValidationException("CUSTOM", "msg", errors);
        assertThat(ex.getErrorCode()).isEqualTo("CUSTOM");
        assertThat(ex.getValidationErrors()).containsExactly("err1");
    }

    @Test
    void validationException_withCustomCodeAndFieldErrors_shouldStoreAll() {
        Map<String, String> fieldErrors = Map.of("f", "e");
        ValidationException ex = new ValidationException("CUSTOM", "msg", fieldErrors);
        assertThat(ex.getErrorCode()).isEqualTo("CUSTOM");
        assertThat(ex.getFieldErrors()).containsEntry("f", "e");
    }

    // ==================== IntegrationException ====================

    @Test
    void integrationException_withCodeAndMessage_shouldCreate() {
        IntegrationException ex = new IntegrationException("INT001", "integration failed");
        assertThat(ex.getErrorCode()).isEqualTo("INT001");
        assertThat(ex.getSystemName()).isNull();
        assertThat(ex.getEndpoint()).isNull();
        assertThat(ex.getRetryCount()).isZero();
    }

    @Test
    void integrationException_withSystemName_shouldSetSystemName() {
        Throwable cause = new RuntimeException("conn error");
        IntegrationException ex = new IntegrationException("INT001", "failed", "PaymentGW", cause);
        assertThat(ex.getSystemName()).isEqualTo("PaymentGW");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void integrationException_withFullDetails_shouldSetAll() {
        Throwable cause = new RuntimeException("err");
        IntegrationException ex = new IntegrationException("INT001", "failed", "GW", "/api/pay", 3, cause);
        assertThat(ex.getSystemName()).isEqualTo("GW");
        assertThat(ex.getEndpoint()).isEqualTo("/api/pay");
        assertThat(ex.getRetryCount()).isEqualTo(3);
    }

    @Test
    void integrationException_timeout_shouldCreateCorrectly() {
        IntegrationException ex = IntegrationException.timeout("PaymentGW", "/api/charge", 5000);
        assertThat(ex.getErrorCode()).isEqualTo("INTEGRATION_TIMEOUT");
        assertThat(ex.getMessage()).contains("PaymentGW").contains("5000");
        assertThat(ex.getSystemName()).isEqualTo("PaymentGW");
        assertThat(ex.getEndpoint()).isEqualTo("/api/charge");
    }

    @Test
    void integrationException_connectionFailed_shouldCreateCorrectly() {
        Throwable cause = new RuntimeException("conn refused");
        IntegrationException ex = IntegrationException.connectionFailed("Redis", "localhost:6379", cause);
        assertThat(ex.getErrorCode()).isEqualTo("CONNECTION_FAILED");
        assertThat(ex.getSystemName()).isEqualTo("Redis");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void integrationException_serviceUnavailable_shouldCreateCorrectly() {
        IntegrationException ex = IntegrationException.serviceUnavailable("Kafka");
        assertThat(ex.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(ex.getMessage()).contains("Kafka");
        assertThat(ex.getSystemName()).isEqualTo("Kafka");
    }

    // ==================== Inheritance checks ====================

    @Test
    void allExceptions_shouldExtendAdharException() {
        assertThat(new BusinessException("C", "M")).isInstanceOf(AdharException.class);
        assertThat(new ResourceNotFoundException("M")).isInstanceOf(AdharException.class);
        assertThat(new ServiceException("M")).isInstanceOf(AdharException.class);
        assertThat(new ValidationException("M")).isInstanceOf(AdharException.class);
        assertThat(new IntegrationException("C", "M")).isInstanceOf(AdharException.class);
    }

    @Test
    void allExceptions_shouldExtendRuntimeException() {
        assertThat(new AdharException("msg")).isInstanceOf(RuntimeException.class);
    }
}
