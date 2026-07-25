package com.adhar.kit.grpc.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GrpcProperties.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class GrpcPropertiesTest {

    @Test
    void testDefaultServerConfiguration() {
        GrpcProperties properties = new GrpcProperties();
        GrpcProperties.ServerConfig server = properties.getServer();

        assertTrue(server.isEnabled());
        assertEquals(9090, server.getPort());
        assertEquals("0.0.0.0", server.getAddress());
        assertEquals(4 * 1024 * 1024, server.getMaxInboundMessageSize());
        assertTrue(server.isEnableReflection());
        assertTrue(server.isEnableHealthCheck());
    }

    @Test
    void testDefaultClientConfiguration() {
        GrpcProperties properties = new GrpcProperties();
        GrpcProperties.ClientConfig client = properties.getClient();

        assertEquals("localhost:9090", client.getDefaultTarget());
        assertNotNull(client.getChannels());
        assertNotNull(client.getDefaults());
    }

    @Test
    void testDefaultChannelConfiguration() {
        GrpcProperties properties = new GrpcProperties();
        GrpcProperties.ChannelConfig channel = properties.getClient().getDefaults();

        assertTrue(channel.isEnableLoadBalancing());
        assertEquals("round_robin", channel.getLoadBalancingPolicy());
        assertTrue(channel.isEnableRetry());
        assertEquals(3, channel.getMaxRetryAttempts());
        assertEquals(60000, channel.getDefaultTimeout());
    }

    @Test
    void testServerConfiguration() {
        GrpcProperties properties = new GrpcProperties();
        GrpcProperties.ServerConfig server = properties.getServer();

        server.setPort(8080);
        server.setAddress("127.0.0.1");
        server.setMaxInboundMessageSize(10 * 1024 * 1024);
        server.setEnableReflection(false);

        assertEquals(8080, server.getPort());
        assertEquals("127.0.0.1", server.getAddress());
        assertEquals(10 * 1024 * 1024, server.getMaxInboundMessageSize());
        assertFalse(server.isEnableReflection());
    }

    @Test
    void testClientChannels() {
        GrpcProperties properties = new GrpcProperties();
        GrpcProperties.ChannelConfig channelConfig = new GrpcProperties.ChannelConfig();
        channelConfig.setTarget("localhost:9091");
        channelConfig.setEnableRetry(false);
        channelConfig.setMaxRetryAttempts(5);

        properties.getClient().getChannels().put("test-service", channelConfig);

        GrpcProperties.ChannelConfig retrieved =
            properties.getClient().getChannels().get("test-service");

        assertNotNull(retrieved);
        assertEquals("localhost:9091", retrieved.getTarget());
        assertFalse(retrieved.isEnableRetry());
        assertEquals(5, retrieved.getMaxRetryAttempts());
    }

    @Test
    void testSecurityConfiguration() {
        GrpcProperties properties = new GrpcProperties();
        GrpcProperties.SecurityConfig security = properties.getSecurity();

        assertFalse(security.isEnabled());
        assertFalse(security.isEnableTls());
        assertFalse(security.isEnableMtls());
        assertEquals("NONE", security.getClientAuth());
    }

    @Test
    void testObservabilityConfiguration() {
        GrpcProperties properties = new GrpcProperties();
        GrpcProperties.ObservabilityConfig observability = properties.getObservability();

        assertTrue(observability.isEnableMetrics());
        assertTrue(observability.isEnableTracing());
        assertTrue(observability.isEnableLogging());
        assertEquals("BASIC", observability.getLogLevel());
    }

    @Test
    void testDefaultAuthConfiguration() {
        GrpcProperties properties = new GrpcProperties();

        assertFalse(properties.getAuth().isEnabled());
        assertNull(properties.getAuth().getSharedSecret());
        assertTrue(properties.isEnableServiceRegistrar());
        assertTrue(properties.isEnableClientInjection());
    }

    @Test
    void testDefaultRetryBackoffConfiguration() {
        GrpcProperties.ChannelConfig channel = new GrpcProperties().getClient().getDefaults();

        assertEquals(1000, channel.getInitialBackoffMillis());
        assertEquals(10000, channel.getMaxBackoffMillis());
        assertEquals(2.0, channel.getBackoffMultiplier());
        assertTrue(channel.getRetryableStatusCodes().contains("UNAVAILABLE"));
    }
}

