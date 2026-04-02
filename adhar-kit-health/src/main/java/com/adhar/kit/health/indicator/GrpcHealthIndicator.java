package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * gRPC health indicator.
 *
 * <p>Checks gRPC service health using the standard gRPC Health Checking Protocol.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Implements gRPC Health Checking Protocol (grpc.health.v1)</li>
 *   <li>Checks health of registered gRPC services</li>
 *   <li>Reports individual service status</li>
 *   <li>Supports overall health aggregation</li>
 *   <li>Timeout handling</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * GrpcHealthIndicator indicator = new GrpcHealthIndicator(
 *     managedChannel,
 *     properties.getGrpc()
 * );
 * Health health = indicator.check();
 * }</pre>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   health:
 *     grpc:
 *       enabled: true
 *       timeout: 3000
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class GrpcHealthIndicator implements AdharHealthIndicator {

    private final Object managedChannel;
    private final AdharHealthProperties.GrpcConfig config;
    private final String[] serviceNames;

    /**
     * Creates gRPC health indicator for all services.
     *
     * @param managedChannel gRPC ManagedChannel instance
     * @param config grpc health configuration
     */
    public GrpcHealthIndicator(Object managedChannel,
                               AdharHealthProperties.GrpcConfig config) {
        this(managedChannel, config, new String[]{""});
    }

    /**
     * Creates gRPC health indicator for specific services.
     *
     * @param managedChannel gRPC ManagedChannel instance
     * @param config grpc health configuration
     * @param serviceNames names of services to check (empty string for overall health)
     */
    public GrpcHealthIndicator(Object managedChannel,
                               AdharHealthProperties.GrpcConfig config,
                               String[] serviceNames) {
        this.managedChannel = managedChannel;
        this.config = config;
        this.serviceNames = serviceNames;
    }

    @Override
    public Health check() {
        if (!config.isEnabled()) {
            return Health.unknown()
                .component(getName())
                .withDetail("status", "disabled")
                .build();
        }

        try {
            // Use reflection to avoid hard dependency on gRPC
            // Create HealthGrpc blocking stub
            Class<?> healthGrpcClass = Class.forName("io.grpc.health.v1.HealthGrpc");
            Method newBlockingStubMethod = healthGrpcClass.getMethod("newBlockingStub",
                Class.forName("io.grpc.Channel"));
            Object healthStub = newBlockingStubMethod.invoke(null, managedChannel);

            // Set deadline
            Method withDeadlineAfterMethod = healthStub.getClass().getMethod(
                "withDeadlineAfter", long.class, TimeUnit.class);
            healthStub = withDeadlineAfterMethod.invoke(healthStub, config.getTimeout(), TimeUnit.MILLISECONDS);

            // Build health check request
            Class<?> healthCheckRequestClass = Class.forName("io.grpc.health.v1.HealthCheckRequest");
            Method newBuilderMethod = healthCheckRequestClass.getMethod("newBuilder");
            Object requestBuilder = newBuilderMethod.invoke(null);

            Map<String, String> serviceStatuses = new HashMap<>();
            boolean allHealthy = true;

            for (String serviceName : serviceNames) {
                try {
                    // Set service name
                    Method setServiceMethod = requestBuilder.getClass().getMethod("setService", String.class);
                    setServiceMethod.invoke(requestBuilder, serviceName);

                    Method buildMethod = requestBuilder.getClass().getMethod("build");
                    Object request = buildMethod.invoke(requestBuilder);

                    // Call check method
                    Method checkMethod = healthStub.getClass().getMethod("check", healthCheckRequestClass);
                    Object response = checkMethod.invoke(healthStub, request);

                    // Get status
                    Method getStatusMethod = response.getClass().getMethod("getStatus");
                    Object status = getStatusMethod.invoke(response);
                    String statusName = status.toString();

                    String displayName = serviceName.isEmpty() ? "overall" : serviceName;
                    serviceStatuses.put(displayName, statusName);

                    if (!"SERVING".equals(statusName)) {
                        allHealthy = false;
                    }

                    // Clear for next iteration
                    Method clearMethod = requestBuilder.getClass().getMethod("clear");
                    clearMethod.invoke(requestBuilder);

                } catch (Exception e) {
                    String displayName = serviceName.isEmpty() ? "overall" : serviceName;
                    serviceStatuses.put(displayName, "ERROR: " + e.getMessage());
                    allHealthy = false;
                    log.debug("gRPC health check failed for service '{}': {}", serviceName, e.getMessage());
                }
            }

            Health.HealthBuilder builder = allHealthy ? Health.up() : Health.down();
            builder.component(getName())
                .withDetail("services", serviceStatuses);

            // Get channel state if possible
            try {
                Method getStateMethod = managedChannel.getClass().getMethod("getState", boolean.class);
                Object state = getStateMethod.invoke(managedChannel, false);
                builder.withDetail("channelState", state.toString());
            } catch (Exception e) {
                log.debug("Could not get gRPC channel state: {}", e.getMessage());
            }

            return builder.build();

        } catch (ClassNotFoundException e) {
            log.debug("gRPC health checking protocol not available: {}", e.getMessage());
            return Health.unknown()
                .component(getName())
                .withDetail("status", "gRPC health checking protocol not available")
                .build();
        } catch (Exception e) {
            log.error("gRPC health check failed", e);
            return Health.down(e)
                .component(getName())
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    @Override
    public String getName() {
        return "grpc";
    }
}
