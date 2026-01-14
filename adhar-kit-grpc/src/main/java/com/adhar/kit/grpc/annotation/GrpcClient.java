package com.adhar.kit.grpc.annotation;

import java.lang.annotation.*;

/**
 * Injects a gRPC client stub.
 *
 * <p>Automatically creates and injects a gRPC client stub for the specified service.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Service
 * public class OrderProcessingService {
 *
 *     @GrpcClient("order-service")
 *     private OrderServiceGrpc.OrderServiceBlockingStub orderClient;
 *
 *     @GrpcClient(value = "inventory-service", enableRetry = true)
 *     private InventoryServiceGrpc.InventoryServiceFutureStub inventoryClient;
 *
 *     public void processOrder(String orderId) {
 *         GetOrderRequest request = GetOrderRequest.newBuilder()
 *             .setOrderId(orderId)
 *             .build();
 *         GetOrderResponse response = orderClient.getOrder(request);
 *         // Process order
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GrpcClient {

    /**
     * Service name (channel name from configuration).
     */
    String value();

    /**
     * Timeout in milliseconds (default: use channel default).
     */
    long timeout() default -1;

    /**
     * Enable retry.
     */
    boolean enableRetry() default true;

    /**
     * Maximum retry attempts.
     */
    int maxRetries() default 3;
}

