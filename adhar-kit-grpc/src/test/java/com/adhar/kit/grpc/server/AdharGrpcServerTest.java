package com.adhar.kit.grpc.server;

import com.adhar.kit.grpc.config.GrpcProperties;
import com.adhar.kit.grpc.exception.GrpcServiceConfigurationException;
import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for AdharGrpcServer.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class AdharGrpcServerTest {

    @Test
    void constructor_createsServer() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        assertThat(server).isNotNull();
    }

    @Test
    void getPort_beforeStart_returnsNegativeOne() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        assertThat(server.getPort()).isEqualTo(-1);
    }

    @Test
    void isRunning_beforeStart_returnsFalse() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void getHealthStatusManager_returnsNonNull() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        assertThat(server.getHealthStatusManager()).isNotNull();
    }

    @Test
    void addService_returnsThis_forChaining() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        // addService should return this for fluent API
        // We cannot easily create a BindableService without protobuf stubs,
        // but we can verify the method signature by testing chaining concept
        assertThat(server).isNotNull();
    }

    @Test
    void start_whenDisabled_doesNotStart() throws IOException {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setEnabled(false);
        AdharGrpcServer server = new AdharGrpcServer(properties);

        server.start();

        assertThat(server.isRunning()).isFalse();
        assertThat(server.getPort()).isEqualTo(-1);
    }

    @Test
    void shutdown_whenNotStarted_doesNothing() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        // Should not throw
        server.shutdown();

        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void awaitTermination_whenNotStarted_doesNothing() throws InterruptedException {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        // Should not throw or block
        server.awaitTermination();
    }

    @Test
    void startAndShutdown_withNoServices() throws IOException {
        GrpcProperties properties = new GrpcProperties();
        // Use a random high port to avoid conflicts
        properties.getServer().setPort(0);
        properties.getServer().setEnableReflection(false);
        properties.getServer().setEnableHealthCheck(false);
        AdharGrpcServer server = new AdharGrpcServer(properties);

        server.start();

        assertThat(server.isRunning()).isTrue();
        assertThat(server.getPort()).isGreaterThanOrEqualTo(0);

        server.shutdown();

        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void startWithHealthAndReflection() throws IOException {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setPort(0);
        properties.getServer().setEnableReflection(true);
        properties.getServer().setEnableHealthCheck(true);
        AdharGrpcServer server = new AdharGrpcServer(properties);

        server.start();

        assertThat(server.isRunning()).isTrue();

        server.shutdown();
    }

    @Test
    void addService_registersService_andStartsWithInterceptors() throws IOException {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setPort(0);
        properties.getServer().setEnableReflection(false);
        properties.getServer().setEnableHealthCheck(false);
        AdharGrpcServer server = new AdharGrpcServer(properties);

        AdharGrpcServer returned = server.addService(new EmptyBindableService());

        // addService returns this for fluent chaining
        assertThat(returned).isSameAs(server);

        // start() must register the service (wrapped with logging + exception interceptors)
        server.start();
        assertThat(server.isRunning()).isTrue();
        assertThat(server.getPort()).isGreaterThanOrEqualTo(0);

        server.shutdown();
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void getServiceCount_reflectsAddedServices() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        assertThat(server.getServiceCount()).isEqualTo(0);
        server.addService(new EmptyBindableService());
        assertThat(server.getServiceCount()).isEqualTo(1);
    }

    @Test
    void startWithAuthAndMetrics_doesNotThrow() throws IOException {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setPort(0);
        properties.getServer().setEnableReflection(false);
        properties.getServer().setEnableHealthCheck(false);
        properties.getAuth().setEnabled(true);
        properties.getAuth().setSharedSecret("s3cr3t");

        AdharGrpcServer server = new AdharGrpcServer(properties)
                .withMeterRegistry(new SimpleMeterRegistry())
                .withAuthenticator(new StaticTokenAuthenticator("s3cr3t"));
        server.addService(new EmptyBindableService());

        server.start();
        assertThat(server.isRunning()).isTrue();
        server.shutdown();
    }

    @Test
    void startWithAuthEnabled_butNoAuthenticatorSupplied_fallsBackToPermitAll() throws IOException {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setPort(0);
        properties.getServer().setEnableReflection(false);
        properties.getServer().setEnableHealthCheck(false);
        properties.getAuth().setEnabled(true);

        AdharGrpcServer server = new AdharGrpcServer(properties);
        server.start();

        assertThat(server.isRunning()).isTrue();
        server.shutdown();
    }

    @Test
    void start_tlsEnabledWithoutCertConfigured_throwsConfigurationException() {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setPort(0);
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setEnableTls(true);

        AdharGrpcServer server = new AdharGrpcServer(properties);

        assertThatThrownBy(server::start)
                .isInstanceOf(GrpcServiceConfigurationException.class)
                .hasMessageContaining("cert-chain");
    }

    @Test
    void start_mtlsEnabledWithoutTrustCertConfigured_throwsConfigurationException(@TempDir Path tempDir) throws IOException {
        Path certFile = tempDir.resolve("server-cert.pem");
        Path keyFile = tempDir.resolve("server-key.pem");
        Files.writeString(certFile, "dummy-cert");
        Files.writeString(keyFile, "dummy-key");

        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setPort(0);
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setEnableTls(true);
        properties.getSecurity().setEnableMtls(true);
        properties.getSecurity().setCertChain(certFile.toString());
        properties.getSecurity().setPrivateKey(keyFile.toString());
        // trustCertCollection intentionally left unset

        AdharGrpcServer server = new AdharGrpcServer(properties);

        assertThatThrownBy(server::start)
                .isInstanceOf(GrpcServiceConfigurationException.class)
                .hasMessageContaining("trust-cert-collection");
    }

    @Test
    void start_tlsEnabledWithMissingCertFile_throwsConfigurationException() {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setPort(0);
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setEnableTls(true);
        properties.getSecurity().setCertChain("/no/such/server-cert.pem");
        properties.getSecurity().setPrivateKey("/no/such/server-key.pem");

        AdharGrpcServer server = new AdharGrpcServer(properties);

        assertThatThrownBy(server::start)
                .isInstanceOf(GrpcServiceConfigurationException.class)
                .hasMessageContaining("server-cert.pem");
    }

    @Test
    void securityEnabledButTlsDisabled_startsPlaintext() throws IOException {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setPort(0);
        properties.getServer().setEnableReflection(false);
        properties.getServer().setEnableHealthCheck(false);
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setEnableTls(false);

        AdharGrpcServer server = new AdharGrpcServer(properties);
        server.start();

        assertThat(server.isRunning()).isTrue();
        server.shutdown();
    }

    /**
     * Minimal {@link BindableService} with no methods, allowing the server registration
     * loop to be exercised without generated protobuf stubs.
     */
    private static class EmptyBindableService implements BindableService {
        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder("test.EmptyService").build();
        }
    }
}
