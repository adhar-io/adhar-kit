package com.adhar.kit.grpc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * gRPC service health status model.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * GrpcServiceHealth health = GrpcServiceHealth.builder()
 *     .serviceName("order-service")
 *     .status(ServingStatus.SERVING)
 *     .host("localhost")
 *     .port(9090)
 *     .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrpcServiceHealth {

    /**
     * Service name.
     */
    private String serviceName;

    /**
     * Service status.
     */
    private ServingStatus status;

    /**
     * Host address.
     */
    private String host;

    /**
     * Port number.
     */
    private int port;

    /**
     * Is service healthy.
     */
    private boolean healthy;

    /**
     * Additional information.
     */
    private String info;

    /**
     * Serving status enum.
     */
    public enum ServingStatus {
        SERVING,
        NOT_SERVING,
        UNKNOWN
    }
}

