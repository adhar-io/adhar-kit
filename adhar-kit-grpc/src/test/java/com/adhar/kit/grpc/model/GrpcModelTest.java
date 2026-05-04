package com.adhar.kit.grpc.model;

import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GrpcErrorResponse and GrpcServiceHealth models.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class GrpcModelTest {

    @Test
    void errorResponse_builder() {
        GrpcErrorResponse error = GrpcErrorResponse.builder()
                .status(Status.Code.INVALID_ARGUMENT)
                .message("Invalid order ID")
                .service("order-service")
                .method("getOrder")
                .correlationId("corr-123")
                .requestId("req-456")
                .build();

        assertThat(error.getStatus()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(error.getMessage()).isEqualTo("Invalid order ID");
        assertThat(error.getService()).isEqualTo("order-service");
        assertThat(error.getMethod()).isEqualTo("getOrder");
        assertThat(error.getCorrelationId()).isEqualTo("corr-123");
        assertThat(error.getRequestId()).isEqualTo("req-456");
        assertThat(error.getTimestamp()).isNotNull();
        assertThat(error.getDetails()).isNotNull().isEmpty();
    }

    @Test
    void errorResponse_detail_addsToMap() {
        GrpcErrorResponse error = GrpcErrorResponse.builder()
                .status(Status.Code.INTERNAL)
                .message("Error")
                .build();

        GrpcErrorResponse result = error.detail("field", "orderId");

        assertThat(result).isSameAs(error);
        assertThat(error.getDetails()).containsEntry("field", "orderId");
    }

    @Test
    void errorResponse_noArgsConstructor() {
        GrpcErrorResponse error = new GrpcErrorResponse();

        assertThat(error.getStatus()).isNull();
        assertThat(error.getMessage()).isNull();
    }

    @Test
    void serviceHealth_builder() {
        GrpcServiceHealth health = GrpcServiceHealth.builder()
                .serviceName("order-service")
                .status(GrpcServiceHealth.ServingStatus.SERVING)
                .host("localhost")
                .port(9090)
                .healthy(true)
                .info("All good")
                .build();

        assertThat(health.getServiceName()).isEqualTo("order-service");
        assertThat(health.getStatus()).isEqualTo(GrpcServiceHealth.ServingStatus.SERVING);
        assertThat(health.getHost()).isEqualTo("localhost");
        assertThat(health.getPort()).isEqualTo(9090);
        assertThat(health.isHealthy()).isTrue();
        assertThat(health.getInfo()).isEqualTo("All good");
    }

    @Test
    void serviceHealth_servingStatusValues() {
        assertThat(GrpcServiceHealth.ServingStatus.values()).hasSize(3);
        assertThat(GrpcServiceHealth.ServingStatus.valueOf("SERVING"))
                .isEqualTo(GrpcServiceHealth.ServingStatus.SERVING);
        assertThat(GrpcServiceHealth.ServingStatus.valueOf("NOT_SERVING"))
                .isEqualTo(GrpcServiceHealth.ServingStatus.NOT_SERVING);
        assertThat(GrpcServiceHealth.ServingStatus.valueOf("UNKNOWN"))
                .isEqualTo(GrpcServiceHealth.ServingStatus.UNKNOWN);
    }

    @Test
    void serviceHealth_noArgsConstructor() {
        GrpcServiceHealth health = new GrpcServiceHealth();

        assertThat(health.getServiceName()).isNull();
        assertThat(health.getPort()).isEqualTo(0);
        assertThat(health.isHealthy()).isFalse();
    }
}
