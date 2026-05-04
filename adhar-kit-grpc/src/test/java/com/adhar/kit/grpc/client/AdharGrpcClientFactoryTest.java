package com.adhar.kit.grpc.client;

import com.adhar.kit.grpc.config.GrpcProperties;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AdharGrpcClientFactory.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class AdharGrpcClientFactoryTest {

    private AdharGrpcClientFactory factory;
    private GrpcProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GrpcProperties();
        factory = new AdharGrpcClientFactory(properties);
    }

    @AfterEach
    void tearDown() {
        factory.shutdown();
    }

    @Test
    void getChannelCount_initiallyZero() {
        assertThat(factory.getChannelCount()).isEqualTo(0);
    }

    @Test
    void getChannel_createsChannelWithDefaults() {
        ManagedChannel channel = factory.getChannel("test-service");

        assertThat(channel).isNotNull();
        assertThat(factory.getChannelCount()).isEqualTo(1);
    }

    @Test
    void getChannel_returnsSameChannelForSameName() {
        ManagedChannel first = factory.getChannel("test-service");
        ManagedChannel second = factory.getChannel("test-service");

        assertThat(first).isSameAs(second);
        assertThat(factory.getChannelCount()).isEqualTo(1);
    }

    @Test
    void getChannel_createsDifferentChannelsForDifferentNames() {
        ManagedChannel first = factory.getChannel("service-a");
        ManagedChannel second = factory.getChannel("service-b");

        assertThat(first).isNotSameAs(second);
        assertThat(factory.getChannelCount()).isEqualTo(2);
    }

    @Test
    void getChannel_usesConfiguredChannel() {
        GrpcProperties.ChannelConfig config = new GrpcProperties.ChannelConfig();
        config.setTarget("localhost:8888");
        config.setEnableRetry(false);
        config.setEnableLoadBalancing(false);
        properties.getClient().getChannels().put("configured-service", config);

        ManagedChannel channel = factory.getChannel("configured-service");

        assertThat(channel).isNotNull();
    }

    @Test
    void getChannel_withRetryEnabled() {
        GrpcProperties.ChannelConfig config = new GrpcProperties.ChannelConfig();
        config.setTarget("localhost:7777");
        config.setEnableRetry(true);
        config.setMaxRetryAttempts(5);
        properties.getClient().getChannels().put("retry-service", config);

        ManagedChannel channel = factory.getChannel("retry-service");

        assertThat(channel).isNotNull();
    }

    @Test
    void getChannel_withTlsEnabled() {
        GrpcProperties.ChannelConfig config = new GrpcProperties.ChannelConfig();
        config.setTarget("localhost:6666");
        config.setEnableTls(true);
        properties.getClient().getChannels().put("tls-service", config);

        ManagedChannel channel = factory.getChannel("tls-service");

        assertThat(channel).isNotNull();
    }

    @Test
    void shutdown_clearsAllChannels() {
        factory.getChannel("service-a");
        factory.getChannel("service-b");
        assertThat(factory.getChannelCount()).isEqualTo(2);

        factory.shutdown();

        assertThat(factory.getChannelCount()).isEqualTo(0);
    }

    @Test
    void shutdownChannel_removesSingleChannel() {
        factory.getChannel("service-a");
        factory.getChannel("service-b");

        factory.shutdownChannel("service-a");

        assertThat(factory.getChannelCount()).isEqualTo(1);
    }

    @Test
    void shutdownChannel_nonExistentDoesNothing() {
        factory.getChannel("service-a");

        factory.shutdownChannel("non-existent");

        assertThat(factory.getChannelCount()).isEqualTo(1);
    }

    @Test
    void getChannel_withTargetWithoutPort() {
        GrpcProperties.ChannelConfig config = new GrpcProperties.ChannelConfig();
        config.setTarget("somehost");
        properties.getClient().getChannels().put("no-port-service", config);

        ManagedChannel channel = factory.getChannel("no-port-service");

        assertThat(channel).isNotNull();
    }
}
