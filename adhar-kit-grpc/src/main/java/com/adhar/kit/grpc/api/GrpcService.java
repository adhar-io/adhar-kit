package com.adhar.kit.grpc.api;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Framework-agnostic gRPC Service API.
 *
 * <p>This interface provides a unified abstraction for gRPC service communication
 * across Spring Boot, Quarkus, and Micronaut frameworks.</p>
 *
 * <p><b>Supported Features:</b></p>
 * <ul>
 *   <li>Unary calls (request-response)</li>
 *   <li>Server streaming</li>
 *   <li>Client streaming</li>
 *   <li>Bidirectional streaming</li>
 *   <li>Interceptors and metadata</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * GrpcService grpc = GrpcFacade.getInstance();
 *
 * // Unary call
 * UserResponse response = grpc.call("user-service", "getUser",
 *     userRequest, UserResponse.class);
 *
 * // Async call
 * CompletableFuture<UserResponse> future = grpc.callAsync("user-service",
 *     "getUser", userRequest, UserResponse.class);
 *
 * // Server streaming
 * grpc.serverStream("order-service", "streamOrders", request,
 *     OrderResponse.class, order -> processOrder(order));
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see com.adhar.kit.grpc.GrpcFacade
 */
public interface GrpcService {

    /**
     * Makes a synchronous unary gRPC call.
     *
     * @param serviceName the gRPC service name
     * @param methodName the method name
     * @param request the request message
     * @param responseType the expected response type
     * @param <REQ> the request type
     * @param <RES> the response type
     * @return the response message
     */
    <REQ, RES> RES call(String serviceName, String methodName,
                        REQ request, Class<RES> responseType);

    /**
     * Makes an asynchronous unary gRPC call.
     *
     * @param serviceName the gRPC service name
     * @param methodName the method name
     * @param request the request message
     * @param responseType the expected response type
     * @param <REQ> the request type
     * @param <RES> the response type
     * @return CompletableFuture containing the response
     */
    <REQ, RES> CompletableFuture<RES> callAsync(String serviceName, String methodName,
                                                  REQ request, Class<RES> responseType);

    /**
     * Initiates a server streaming call.
     *
     * <p>The server sends a stream of responses for a single request.</p>
     *
     * @param serviceName the gRPC service name
     * @param methodName the method name
     * @param request the request message
     * @param responseType the expected response type
     * @param responseHandler handler for each response
     * @param <REQ> the request type
     * @param <RES> the response type
     */
    <REQ, RES> void serverStream(String serviceName, String methodName,
                                  REQ request, Class<RES> responseType,
                                  Consumer<RES> responseHandler);

    /**
     * Checks if the gRPC service is available.
     *
     * @param serviceName the service name to check
     * @return true if available, false otherwise
     */
    boolean isServiceAvailable(String serviceName);

    /**
     * Adds metadata to the next gRPC call.
     *
     * @param key metadata key
     * @param value metadata value
     */
    void addMetadata(String key, String value);

    /**
     * Sets a deadline for the next gRPC call.
     *
     * @param milliseconds deadline in milliseconds
     */
    void setDeadline(long milliseconds);

    /**
     * Gets the configured gRPC server port.
     *
     * @return server port
     */
    int getServerPort();

    /**
     * Checks if gRPC is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();
}

