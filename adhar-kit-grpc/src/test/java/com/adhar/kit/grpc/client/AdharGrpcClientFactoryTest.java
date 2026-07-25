package com.adhar.kit.grpc.client;

import com.adhar.kit.grpc.config.GrpcProperties;
import com.adhar.kit.grpc.exception.GrpcServiceConfigurationException;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // ------------------------------------------------------------------
    // Built-in gRPC retry (replaces the old RetryInterceptor)
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void buildRetryServiceConfig_containsExpectedRetryPolicy() {
        GrpcProperties.ChannelConfig config = new GrpcProperties.ChannelConfig();
        config.setInitialBackoffMillis(500);
        config.setMaxBackoffMillis(4000);
        config.setBackoffMultiplier(1.5);

        Map<String, Object> serviceConfig = AdharGrpcClientFactory.buildRetryServiceConfig(config, 4);

        List<Map<String, Object>> methodConfigs = (List<Map<String, Object>>) serviceConfig.get("methodConfig");
        assertThat(methodConfigs).hasSize(1);
        Map<String, Object> retryPolicy = (Map<String, Object>) methodConfigs.get(0).get("retryPolicy");

        assertThat(retryPolicy.get("maxAttempts")).isEqualTo(4.0);
        assertThat(retryPolicy.get("initialBackoff")).isEqualTo("0.5s");
        assertThat(retryPolicy.get("maxBackoff")).isEqualTo("4.0s");
        assertThat(retryPolicy.get("backoffMultiplier")).isEqualTo(1.5);
        assertThat((List<String>) retryPolicy.get("retryableStatusCodes"))
                .contains("UNAVAILABLE", "DEADLINE_EXCEEDED", "RESOURCE_EXHAUSTED", "ABORTED");
    }

    @Test
    void getChannel_withRetryEnabled_belowMinimum_clampsToTwoAttempts() {
        GrpcProperties.ChannelConfig config = new GrpcProperties.ChannelConfig();
        config.setTarget("localhost:5555");
        config.setEnableRetry(true);
        config.setMaxRetryAttempts(1); // below grpc-java's minimum of 2
        properties.getClient().getChannels().put("clamped-service", config);

        // Must not throw when grpc-java validates the service config's retryPolicy.maxAttempts.
        ManagedChannel channel = factory.getChannel("clamped-service");

        assertThat(channel).isNotNull();
    }

    @Test
    void getChannel_withRetryDisabled_createsChannelWithoutError() {
        GrpcProperties.ChannelConfig config = new GrpcProperties.ChannelConfig();
        config.setTarget("localhost:5556");
        config.setEnableRetry(false);
        properties.getClient().getChannels().put("no-retry-service", config);

        ManagedChannel channel = factory.getChannel("no-retry-service");

        assertThat(channel).isNotNull();
    }

    // ------------------------------------------------------------------
    // TLS / mTLS credential construction and validation errors
    // ------------------------------------------------------------------

    @Test
    void getChannel_tlsWithMissingTrustCertFile_throwsConfigurationException() {
        properties.getSecurity().setTrustCertCollection("/no/such/trust.pem");
        GrpcProperties.ChannelConfig config = new GrpcProperties.ChannelConfig();
        config.setTarget("localhost:6001");
        config.setEnableTls(true);
        properties.getClient().getChannels().put("tls-missing-trust", config);

        assertThatThrownBy(() -> factory.getChannel("tls-missing-trust"))
                .isInstanceOf(GrpcServiceConfigurationException.class)
                .hasMessageContaining("trust.pem");
    }

    @Test
    void getChannel_mtlsWithoutCertOrKeyConfigured_throwsConfigurationException() {
        properties.getSecurity().setEnableMtls(true);
        GrpcProperties.ChannelConfig config = new GrpcProperties.ChannelConfig();
        config.setTarget("localhost:6002");
        config.setEnableTls(true);
        properties.getClient().getChannels().put("mtls-missing-cert", config);

        assertThatThrownBy(() -> factory.getChannel("mtls-missing-cert"))
                .isInstanceOf(GrpcServiceConfigurationException.class)
                .hasMessageContaining("mTLS");
    }

    @Test
    void getChannel_mtlsWithMissingKeyFile_throwsConfigurationException(@TempDir Path tempDir) throws IOException {
        Path certFile = tempDir.resolve("client-cert.pem");
        Files.writeString(certFile, "dummy");
        properties.getSecurity().setEnableMtls(true);
        properties.getSecurity().setCertChain(certFile.toString());
        properties.getSecurity().setPrivateKey("/no/such/client-key.pem");
        GrpcProperties.ChannelConfig config = new GrpcProperties.ChannelConfig();
        config.setTarget("localhost:6003");
        config.setEnableTls(true);
        properties.getClient().getChannels().put("mtls-missing-key", config);

        assertThatThrownBy(() -> factory.getChannel("mtls-missing-key"))
                .isInstanceOf(GrpcServiceConfigurationException.class)
                .hasMessageContaining("client-key.pem");
    }
}
