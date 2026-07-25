package com.adhar.kit.grpc.interceptor;

import com.adhar.kit.grpc.server.GrpcAuthenticator;
import com.adhar.kit.grpc.server.PermitAllGrpcAuthenticator;
import com.adhar.kit.grpc.server.StaticTokenAuthenticator;
import com.adhar.kit.grpc.testsupport.EchoServiceSupport;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tests exercising {@link DeadlineClientInterceptor},
 * {@link MetricsServerInterceptor}, {@link MetricsClientInterceptor} and
 * {@link AuthServerInterceptor} over a real client-server round trip using
 * grpc-testing's in-process transport (no sockets, no TLS handshake).
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class InProcessInterceptorIntegrationTest {

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    private void startServer(String name, GrpcAuthenticator authenticator, SimpleMeterRegistry serverRegistry) throws Exception {
        // Per ServerInterceptors.intercept(List)'s contract, the LAST entry is outermost.
        // Metrics goes last so it observes calls closed early by AuthServerInterceptor
        // (see the matching comment/order in AdharGrpcServer.start()).
        List<io.grpc.ServerInterceptor> interceptors = List.of(
                new AuthServerInterceptor(authenticator),
                new MetricsServerInterceptor(serverRegistry));

        server = InProcessServerBuilder.forName(name)
                .addService(ServerInterceptors.intercept(EchoServiceSupport.echoService(), interceptors))
                .build()
                .start();
    }

    private ManagedChannel buildChannel(String name, long defaultTimeoutMillis, SimpleMeterRegistry clientRegistry, String bearerToken) {
        io.grpc.ManagedChannelBuilder<?> channelBuilder = InProcessChannelBuilder.forName(name)
                .usePlaintext()
                .intercept(new DeadlineClientInterceptor(defaultTimeoutMillis))
                .intercept(new MetricsClientInterceptor(clientRegistry));

        if (bearerToken != null) {
            channelBuilder = channelBuilder.intercept(new BearerTokenClientInterceptor(bearerToken));
        }

        return channelBuilder.build();
    }

    @Test
    void authenticatedCall_succeeds_andRecordsMetricsOnBothSides() throws Exception {
        String name = "adhar-grpc-" + UUID.randomUUID();
        SimpleMeterRegistry serverRegistry = new SimpleMeterRegistry();
        SimpleMeterRegistry clientRegistry = new SimpleMeterRegistry();
        startServer(name, new StaticTokenAuthenticator("s3cr3t"), serverRegistry);
        channel = buildChannel(name, 5000, clientRegistry, "s3cr3t");

        String response = ClientCalls.blockingUnaryCall(
                channel, EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, "hello");

        assertThat(response).isEqualTo("echo:hello");
        assertThat(serverRegistry.counter("grpc.server.calls",
                "method", EchoServiceSupport.ECHO_METHOD.getFullMethodName(), "status", "OK").count())
                .isEqualTo(1.0);
        assertThat(clientRegistry.counter("grpc.client.calls",
                "method", EchoServiceSupport.ECHO_METHOD.getFullMethodName(), "status", "OK").count())
                .isEqualTo(1.0);
        assertThat(serverRegistry.find("grpc.server.calls.inflight").gauge().value()).isEqualTo(0.0);
    }

    @Test
    void unauthenticatedCall_isRejected_andRecordedAsErrorStatus() throws Exception {
        String name = "adhar-grpc-" + UUID.randomUUID();
        SimpleMeterRegistry serverRegistry = new SimpleMeterRegistry();
        SimpleMeterRegistry clientRegistry = new SimpleMeterRegistry();
        startServer(name, new StaticTokenAuthenticator("s3cr3t"), serverRegistry);
        // No bearer token attached.
        channel = buildChannel(name, 5000, clientRegistry, null);

        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(
                channel, EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, "hello"))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.UNAUTHENTICATED));

        assertThat(serverRegistry.counter("grpc.server.calls",
                "method", EchoServiceSupport.ECHO_METHOD.getFullMethodName(), "status", "UNAUTHENTICATED").count())
                .isEqualTo(1.0);
    }

    @Test
    void permitAllAuthenticator_allowsCallWithoutCredentials() throws Exception {
        String name = "adhar-grpc-" + UUID.randomUUID();
        SimpleMeterRegistry serverRegistry = new SimpleMeterRegistry();
        startServer(name, new PermitAllGrpcAuthenticator(), serverRegistry);
        channel = buildChannel(name, 5000, new SimpleMeterRegistry(), null);

        String response = ClientCalls.blockingUnaryCall(
                channel, EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, "hi");

        assertThat(response).isEqualTo("echo:hi");
    }

    @Test
    void defaultDeadline_isApplied_whenCallerSetsNone_andServerIsSlow() throws Exception {
        String name = "adhar-grpc-" + UUID.randomUUID();
        startServer(name, new PermitAllGrpcAuthenticator(), new SimpleMeterRegistry());
        // Default timeout of 100ms; server sleeps 1s -> must fail with DEADLINE_EXCEEDED.
        channel = buildChannel(name, 100, new SimpleMeterRegistry(), null);

        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(
                channel, EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, "slow:1000"))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.DEADLINE_EXCEEDED));
    }

    @Test
    void explicitDeadline_isNotOverridden_byShorterDefault() throws Exception {
        String name = "adhar-grpc-" + UUID.randomUUID();
        startServer(name, new PermitAllGrpcAuthenticator(), new SimpleMeterRegistry());
        // Default timeout of just 1ms would fail a slow call, but the caller sets an explicit
        // generous deadline which DeadlineClientInterceptor must not shrink.
        channel = buildChannel(name, 1, new SimpleMeterRegistry(), null);

        String response = ClientCalls.blockingUnaryCall(
                channel, EchoServiceSupport.ECHO_METHOD,
                CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS), "slow:200");

        assertThat(response).isEqualTo("echo:slow:200");
    }

    /**
     * Test-only client interceptor that attaches a Bearer token header,
     * standing in for what a real application would do (e.g. via a
     * call-credentials or a dedicated header-propagating interceptor).
     */
    private record BearerTokenClientInterceptor(String token) implements ClientInterceptor {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    headers.put(com.adhar.kit.grpc.util.GrpcUtils.AUTHORIZATION_KEY, "Bearer " + token);
                    super.start(responseListener, headers);
                }
            };
        }
    }
}
