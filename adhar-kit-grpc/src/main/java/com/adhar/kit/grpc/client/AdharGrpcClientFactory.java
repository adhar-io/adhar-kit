package com.adhar.kit.grpc.client;

import com.adhar.kit.grpc.config.GrpcProperties;
import com.adhar.kit.grpc.exception.GrpcServiceConfigurationException;
import com.adhar.kit.grpc.interceptor.DeadlineClientInterceptor;
import com.adhar.kit.grpc.interceptor.MetricsClientInterceptor;
import com.adhar.kit.grpc.util.GrpcUtils;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.TlsChannelCredentials;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
 *   <li>Automatic retry with backoff (grpc-java's built-in retry mechanism)</li>
 *   <li>Default per-call deadlines</li>
 *   <li>Connection pooling</li>
 *   <li>Keep-alive settings</li>
 *   <li>TLS/mTLS support</li>
 *   <li>Optional Micrometer metrics</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 *
 * <p><b>Why grpc-java's built-in retry instead of a hand-rolled interceptor:</b>
 * grpc-java ships a transparent retry implementation (enabled via
 * {@link ManagedChannelBuilder#enableRetry()} plus a retry policy supplied
 * through {@link ManagedChannelBuilder#defaultServiceConfig(Map)}) that
 * correctly buffers and replays the request, respects the server's
 * {@code pushback} hints, and is exercised by grpc-java's own test suite. A
 * client interceptor cannot safely retry a unary call on its own without
 * re-implementing exactly that machinery - the request message would need to
 * be captured and replayed, streaming calls would need to be reset, and
 * hedging/pushback semantics would need to be reimplemented from scratch.
 * This module previously shipped such an interceptor
 * ({@code RetryInterceptor}) which slept on failure but never actually
 * retried (see its history); it has been removed in favor of the supported
 * mechanism below.</p>
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
    private volatile MeterRegistry meterRegistry;

    /**
     * Creates client factory with properties.
     *
     * @param properties gRPC properties
     */
    public AdharGrpcClientFactory(GrpcProperties properties) {
        this.properties = properties;
    }

    /**
     * Supplies a Micrometer registry so that channels created afterwards are
     * instrumented with {@link MetricsClientInterceptor}. Optional; if never
     * called, no client-side metrics are recorded.
     *
     * @param meterRegistry Micrometer registry
     * @return this factory for chaining
     */
    public AdharGrpcClientFactory withMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        return this;
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

        ManagedChannelBuilder<?> channelBuilder;
        if (config.isEnableTls()) {
            channelBuilder = Grpc.newChannelBuilderForAddress(host, port, buildClientTlsCredentials());
            log.debug("TLS enabled for channel '{}'", channelName);
        } else {
            channelBuilder = ManagedChannelBuilder.forAddress(host, port).usePlaintext();
        }

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

        // Apply the configured default timeout as a deadline when the caller didn't set one
        channelBuilder.intercept(new DeadlineClientInterceptor(config.getDefaultTimeout()));

        // Optional Micrometer instrumentation
        MeterRegistry registry = this.meterRegistry;
        if (registry != null && properties.getObservability().isEnableMetrics()) {
            channelBuilder.intercept(new MetricsClientInterceptor(registry));
        }

        // Built-in gRPC retry - see class Javadoc for why this replaces the old RetryInterceptor
        if (config.isEnableRetry()) {
            int maxAttempts = Math.max(2, config.getMaxRetryAttempts());
            channelBuilder
                .enableRetry()
                .maxRetryAttempts(maxAttempts)
                .defaultServiceConfig(buildRetryServiceConfig(config, maxAttempts));
            log.debug("Built-in gRPC retry enabled for channel '{}': maxAttempts={}", channelName, maxAttempts);
        } else {
            channelBuilder.disableRetry();
        }

        ManagedChannel channel = channelBuilder.build();

        log.info("gRPC channel created: name={}, target={}", channelName, config.getTarget());

        return channel;
    }

    /**
     * Builds a gRPC service config map describing a retry policy from the
     * given channel configuration. The {@code name} entry is left empty so
     * the policy applies to every method on every service invoked through
     * this channel.
     *
     * @param config      channel configuration
     * @param maxAttempts already-clamped max attempts (must be &gt;= 2)
     * @return service config map suitable for {@link ManagedChannelBuilder#defaultServiceConfig(Map)}
     */
    static Map<String, Object> buildRetryServiceConfig(GrpcProperties.ChannelConfig config, int maxAttempts) {
        Map<String, Object> retryPolicy = new HashMap<>();
        retryPolicy.put("maxAttempts", (double) maxAttempts);
        retryPolicy.put("initialBackoff", toSecondsString(config.getInitialBackoffMillis()));
        retryPolicy.put("maxBackoff", toSecondsString(config.getMaxBackoffMillis()));
        retryPolicy.put("backoffMultiplier", config.getBackoffMultiplier());
        retryPolicy.put("retryableStatusCodes", new ArrayList<>(config.getRetryableStatusCodes()));

        // An empty "name" entry matches every service/method on the channel.
        Map<String, Object> methodConfig = new HashMap<>();
        methodConfig.put("name", Collections.singletonList(new HashMap<>()));
        methodConfig.put("retryPolicy", retryPolicy);

        Map<String, Object> serviceConfig = new HashMap<>();
        serviceConfig.put("methodConfig", Collections.singletonList(methodConfig));
        return serviceConfig;
    }

    private static String toSecondsString(long millis) {
        return (millis / 1000.0) + "s";
    }

    /**
     * Builds client-side TLS credentials from {@link GrpcProperties.SecurityConfig}:
     * a trust store when {@code trust-cert-collection} is set, and a client
     * certificate/key pair when mTLS is enabled.
     *
     * @return channel credentials for TLS/mTLS
     * @throws GrpcServiceConfigurationException if mTLS is enabled but the
     *                                            client cert/key are not configured, or a configured file is missing
     */
    private ChannelCredentials buildClientTlsCredentials() {
        GrpcProperties.SecurityConfig security = properties.getSecurity();
        TlsChannelCredentials.Builder builder = TlsChannelCredentials.newBuilder();

        try {
            if (security.getTrustCertCollection() != null && !security.getTrustCertCollection().isBlank()) {
                builder.trustManager(GrpcUtils.resolveCertFile(security.getTrustCertCollection()));
            }

            if (security.isEnableMtls()) {
                if (security.getCertChain() == null || security.getCertChain().isBlank()
                        || security.getPrivateKey() == null || security.getPrivateKey().isBlank()) {
                    throw new GrpcServiceConfigurationException(
                            "mTLS is enabled but security.cert-chain / security.private-key are not configured");
                }
                builder.keyManager(
                        GrpcUtils.resolveCertFile(security.getCertChain()),
                        GrpcUtils.resolveCertFile(security.getPrivateKey()));
            }
        } catch (IOException e) {
            throw new GrpcServiceConfigurationException("Failed to load TLS credentials for gRPC client", e);
        }

        return builder.build();
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
