package com.adhar.kit.grpc.exception;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GrpcException and GrpcServiceConfigurationException.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class GrpcExceptionTest {

    @Test
    void grpcException_withStatusAndMessage() {
        GrpcException ex = new GrpcException(Status.NOT_FOUND, "Resource not found");

        assertThat(ex.getStatus()).isEqualTo(Status.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void grpcException_withStatusMessageAndCause() {
        Throwable cause = new RuntimeException("root cause");
        GrpcException ex = new GrpcException(Status.INTERNAL, "Server error", cause);

        assertThat(ex.getStatus()).isEqualTo(Status.INTERNAL);
        assertThat(ex.getMessage()).isEqualTo("Server error");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void toStatusRuntimeException_returnsCorrectStatus() {
        GrpcException ex = new GrpcException(Status.PERMISSION_DENIED, "Access denied");

        StatusRuntimeException sre = ex.toStatusRuntimeException();

        assertThat(sre).isNotNull();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(sre.getStatus().getDescription()).isEqualTo("Access denied");
    }

    @Test
    void serviceConfigurationException_usesInternalStatus() {
        GrpcServiceConfigurationException ex =
                new GrpcServiceConfigurationException("Bad config");

        assertThat(ex.getStatus()).isEqualTo(Status.INTERNAL);
        assertThat(ex.getMessage()).isEqualTo("Bad config");
    }

    @Test
    void serviceConfigurationException_withCause() {
        Throwable cause = new IllegalStateException("missing property");
        GrpcServiceConfigurationException ex =
                new GrpcServiceConfigurationException("Bad config", cause);

        assertThat(ex.getStatus()).isEqualTo(Status.INTERNAL);
        assertThat(ex.getMessage()).isEqualTo("Bad config");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
