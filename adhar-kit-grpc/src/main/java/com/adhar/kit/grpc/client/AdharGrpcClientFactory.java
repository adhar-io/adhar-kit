package com.adhar.kit.grpc.client;

import com.adhar.kit.grpc.config.GrpcProperties;
import com.adhar.kit.grpc.interceptor.RetryInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Adhar gRPC client factory for creating managed channels.
 *
 * <p>Provides enterprise-grade gRPC client features:</p>
 * <ul>
 *   <li>Named channel management</li>
 *   <li>Load balancing configuration</li>
 *   <li>Automatic retry with backoff</li>
 *   <li>Connection pooling</li>
 *   <li>Keep-alive settings</li>
 *   <li>TLS/mTLS support</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Configure channels
 * GrpcProperties properties = new GrpcProperties();
 * GrpcProperties.ChannelConfig orderChannel = new GrpcProperties.ChannelConfig();
 * orderChannel.setTarget("localhost:9090");
 * orderChannel.setEnableRetry(true);
 * properties.getClient().getChannels().put("order-service", orderChannel);
 *
 * // Create client factory
 * AdharGrpcClientFactory factory = new AdharGrpcClientFactory(properties);
 *
 * // Get channel and create stub
 * ManagedChannel channel = factory.getChannel("order-service");
 * OrderServiceGrpc.OrderServiceBlockingStub stub =
 *     OrderServiceGrpc.newBlockingStub(channel);
 *
 * // Use the stub
 * GetOrderResponse response = stub.getOrder(
 *     GetOrderRequest.newBuilder().setOrderId("123").build()
 * );
 *
 * // Shutdown
 * factory.shutdown();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AdharGrpcClientFactory {

    private final GrpcProperties properties;
    private final Map<String, ManagedChannel> channels = new HashMap<>();

    /**
     * Creates client factory with properties.
     *
     * @param properties gRPC properties
     */
    public AdharGrpcClientFactory(GrpcProperties properties) {
        this.properties = properties;
    }

    /**
     * Gets or creates a managed channel.
     *
     * @param channelName channel name from configuration
     * @return managed channel
     */
    public synchronized ManagedChannel getChannel(String channelName) {
        return channels.computeIfAbsent(channelName, this::createChannel);
    }

    /**
     * Creates a new managed channel.
     *
     * @param channelName channel name
     * @return managed channel
     */
    private ManagedChannel createChannel(String channelName) {
        GrpcProperties.ChannelConfig config = properties.getClient().getChannels().get(channelName);

        if (config == null) {
            config = properties.getClient().getDefaults();
            log.warn("No channel configuration found for '{}', using defaults", channelName);
        }

        if (config.getTarget() == null || config.getTarget().isEmpty()) {
            config.setTarget(properties.getClient().getDefaultTarget());
        }

        log.info("Creating gRPC channel: name={}, target={}", channelName, config.getTarget());

        // Parse target (host:port)
        String[] parts = config.getTarget().split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9090;

        ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress(host, port);

        // Configure channel settings
        channelBuilder
            .maxInboundMessageSize(config.getMaxInboundMessageSize())
            .keepAliveTime(config.getKeepAliveTime(), TimeUnit.SECONDS)
            .keepAliveTimeout(config.getKeepAliveTimeout(), TimeUnit.SECONDS)
            .keepAliveWithoutCalls(config.isKeepAliveWithoutCalls())
            .userAgent(config.getUserAgent());

        // Load balancing
        if (config.isEnableLoadBalancing()) {
            channelBuilder.defaultLoadBalancingPolicy(config.getLoadBalancingPolicy());
        }

        // TLS
        if (!config.isEnableTls()) {
            channelBuilder.usePlaintext();
        }

        // Retry interceptor
        if (config.isEnableRetry()) {
            RetryInterceptor retryInterceptor = new RetryInterceptor(
                config.getMaxRetryAttempts(),
                1000 // 1 second initial backoff
            );
            channelBuilder.intercept(retryInterceptor);
            log.debug("Retry interceptor enabled for channel '{}'", channelName);
        }

        ManagedChannel channel = channelBuilder.build();

        log.info("gRPC channel created: name={}, target={}", channelName, config.getTarget());

        return channel;
    }

    /**
     * Shuts down all channels gracefully.
     */
    public void shutdown() {
        log.info("Shutting down {} gRPC channels", channels.size());

        for (Map.Entry<String, ManagedChannel> entry : channels.entrySet()) {
            String name = entry.getKey();
            ManagedChannel channel = entry.getValue();

            try {
                channel.shutdown();
                if (!channel.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Channel '{}' did not terminate gracefully, forcing shutdown", name);
                    channel.shutdownNow();
                }
                log.info("Channel '{}' shut down", name);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
                log.error("Interrupted while shutting down channel '{}'", name, e);
            }
        }

        channels.clear();
    }

    /**
     * Shuts down a specific channel.
     *
     * @param channelName channel name
     */
    public void shutdownChannel(String channelName) {
        ManagedChannel channel = channels.remove(channelName);
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(30, TimeUnit.SECONDS);
                log.info("Channel '{}' shut down", channelName);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
    }

    /**
     * Gets the number of active channels.
     *
     * @return number of channels
     */
    public int getChannelCount() {
        return channels.size();
    }
}

