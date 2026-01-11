package com.adhar.kit.grpc.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a gRPC service implementation.
 *
 * <p>Automatically registers the service with the gRPC server.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @GrpcService
 * public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {
 *
 *     @Override
 *     public void createOrder(CreateOrderRequest request,
 *                            StreamObserver<CreateOrderResponse> responseObserver) {
 *         // Implementation
 *         Order order = orderService.create(request);
 *         CreateOrderResponse response = CreateOrderResponse.newBuilder()
 *             .setOrder(order)
 *             .build();
 *         responseObserver.onNext(response);
 *         responseObserver.onCompleted();
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GrpcService {

    /**
     * Service name (optional, defaults to class name).
     */
    String value() default "";

    /**
     * Enable interceptors.
     */
    boolean enableInterceptors() default true;

    /**
     * Custom interceptor classes.
     */
    Class<?>[] interceptors() default {};
}

