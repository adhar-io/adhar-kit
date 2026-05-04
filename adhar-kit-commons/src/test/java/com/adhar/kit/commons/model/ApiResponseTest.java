package com.adhar.kit.commons.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void success_withData_shouldCreateSuccessResponse() {
        ApiResponse<Integer> response = ApiResponse.success(42);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(42);
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    void success_withDataAndMessage_shouldCreateSuccessResponse() {
        ApiResponse<Integer> response = ApiResponse.success(42, "Operation completed");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(42);
        assertThat(response.getMessage()).isEqualTo("Operation completed");
    }

    @Test
    void success_withMessageOnly_shouldCreateSuccessResponseWithNoData() {
        ApiResponse<Void> response = ApiResponse.success("Done");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Done");
        assertThat(response.getData()).isNull();
    }

    @Test
    void error_withErrorResponse_shouldCreateErrorResponse() {
        ErrorResponse errorResponse = ErrorResponse.of("ERR001", "Something went wrong");
        ApiResponse<Void> response = ApiResponse.error(errorResponse);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().getCode()).isEqualTo("ERR001");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void error_withMessage_shouldCreateErrorResponse() {
        ApiResponse<Void> response = ApiResponse.error("Something failed");
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Something failed");
    }

    @Test
    void error_withCodeAndMessage_shouldCreateErrorResponseWithErrorDetails() {
        ApiResponse<Void> response = ApiResponse.error("ERR001", "Not found");
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().getCode()).isEqualTo("ERR001");
        assertThat(response.getError().getMessage()).isEqualTo("Not found");
    }

    @Test
    void withRequestId_shouldSetRequestId() {
        ApiResponse<Integer> response = ApiResponse.<Integer>success(99).withRequestId("req-123");
        assertThat(response.getRequestId()).isEqualTo("req-123");
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void builder_shouldCreateResponse() {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .data("built")
                .message("msg")
                .build();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("built");
        assertThat(response.getMessage()).isEqualTo("msg");
    }

    @Test
    void noArgConstructor_shouldCreateEmptyResponse() {
        ApiResponse<String> response = new ApiResponse<>();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
    }
}
