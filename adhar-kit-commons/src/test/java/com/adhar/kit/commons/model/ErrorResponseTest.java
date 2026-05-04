package com.adhar.kit.commons.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void of_shouldCreateSimpleErrorResponse() {
        ErrorResponse error = ErrorResponse.of("ERR001", "Something went wrong");
        assertThat(error.getCode()).isEqualTo("ERR001");
        assertThat(error.getMessage()).isEqualTo("Something went wrong");
        assertThat(error.getTimestamp()).isNotNull();
    }

    @Test
    void ofValidationErrors_shouldCreateWithValidationErrors() {
        List<String> errors = List.of("Name is required", "Age must be positive");
        ErrorResponse error = ErrorResponse.ofValidationErrors("VALIDATION", "Validation failed", errors);
        assertThat(error.getCode()).isEqualTo("VALIDATION");
        assertThat(error.getMessage()).isEqualTo("Validation failed");
        assertThat(error.getValidationErrors()).containsExactly("Name is required", "Age must be positive");
        assertThat(error.getTimestamp()).isNotNull();
    }

    @Test
    void ofFieldErrors_shouldCreateWithFieldErrors() {
        Map<String, String> fieldErrors = Map.of("name", "is required", "email", "is invalid");
        ErrorResponse error = ErrorResponse.ofFieldErrors("FIELD_ERR", "Field errors", fieldErrors);
        assertThat(error.getCode()).isEqualTo("FIELD_ERR");
        assertThat(error.getFieldErrors()).containsEntry("name", "is required");
        assertThat(error.getFieldErrors()).containsEntry("email", "is invalid");
    }

    @Test
    void withRequestId_shouldSetRequestId() {
        ErrorResponse error = ErrorResponse.of("ERR", "msg").withRequestId("req-456");
        assertThat(error.getRequestId()).isEqualTo("req-456");
    }

    @Test
    void withPath_shouldSetPath() {
        ErrorResponse error = ErrorResponse.of("ERR", "msg").withPath("/api/users");
        assertThat(error.getPath()).isEqualTo("/api/users");
    }

    @Test
    void withDetails_shouldSetDetails() {
        ErrorResponse error = ErrorResponse.of("ERR", "msg").withDetails("Extra info");
        assertThat(error.getDetails()).isEqualTo("Extra info");
    }

    @Test
    void chainedWith_shouldSetAllFields() {
        ErrorResponse error = ErrorResponse.of("ERR", "msg")
                .withRequestId("req-1")
                .withPath("/api/test")
                .withDetails("details");
        assertThat(error.getRequestId()).isEqualTo("req-1");
        assertThat(error.getPath()).isEqualTo("/api/test");
        assertThat(error.getDetails()).isEqualTo("details");
    }

    @Test
    void builder_shouldCreateErrorResponse() {
        ErrorResponse error = ErrorResponse.builder()
                .code("ERR")
                .message("msg")
                .details("det")
                .path("/path")
                .requestId("rid")
                .build();
        assertThat(error.getCode()).isEqualTo("ERR");
        assertThat(error.getMessage()).isEqualTo("msg");
        assertThat(error.getDetails()).isEqualTo("det");
    }

    @Test
    void noArgConstructor_shouldCreateEmptyResponse() {
        ErrorResponse error = new ErrorResponse();
        assertThat(error.getCode()).isNull();
        assertThat(error.getMessage()).isNull();
    }
}
