package com.adhar.kit.grpc.interceptor;

import com.adhar.kit.grpc.testsupport.EchoServiceSupport;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tests over grpc's in-process transport exercising
 * {@link TracingClientInterceptor}/{@link TracingServerInterceptor} trace
 * propagation and {@link ConcurrencyLimitServerInterceptor} load shedding
 * across a real client-server round trip (no sockets, no Docker).
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class InProcessTracingConcurrencyTest {

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

    @Test
    void traceContext_isPropagatedFromClientToServer_andSpansRecorded() throws Exception {
        String name = "adhar-grpc-" + UUID.randomUUID();
        AtomicReference<String> receivedTraceparent = new AtomicReference<>();
        AtomicReference<TraceContext> serverContext = new AtomicReference<>();

        // Runs inside TracingServerInterceptor so it sees both the raw header and the context.
        ServerInterceptor capture = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> io.grpc.ServerCall.Listener<ReqT> interceptCall(
                    io.grpc.ServerCall<ReqT, RespT> call, io.grpc.Metadata headers,
                    io.grpc.ServerCallHandler<ReqT, RespT> next) {
                receivedTraceparent.set(headers.get(TracingServerInterceptor.TRACEPARENT_KEY));
                serverContext.set(TracingServerInterceptor.TRACE_CONTEXT_KEY.get());
                return next.startCall(call, headers);
            }
        };

        CollectingObserver serverObserver = new CollectingObserver();
        CollectingObserver clientObserver = new CollectingObserver();

        // Last entry is outermost, so TracingServerInterceptor wraps the capture interceptor.
        List<ServerInterceptor> interceptors = List.of(capture, new TracingServerInterceptor(serverObserver));
        server = InProcessServerBuilder.forName(name)
                .addService(ServerInterceptors.intercept(EchoServiceSupport.echoService(), interceptors))
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(name).usePlaintext()
                .intercept(new TracingClientInterceptor(clientObserver))
                .build();

        String response = ClientCalls.blockingUnaryCall(
                channel, EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, "hi");

        assertThat(response).isEqualTo("echo:hi");

        TraceContext sent = TraceContext.parse(receivedTraceparent.get());
        assertThat(sent).isNotNull();
        assertThat(serverContext.get()).isNotNull();
        // Server continues the same trace but starts its own child span.
        assertThat(serverContext.get().getTraceId()).isEqualTo(sent.getTraceId());
        assertThat(serverContext.get().getSpanId()).isNotEqualTo(sent.getSpanId());

        assertThat(clientObserver.kinds).containsExactly(GrpcObserver.Kind.CLIENT);
        assertThat(serverObserver.kinds).containsExactly(GrpcObserver.Kind.SERVER);
        assertThat(clientObserver.finishedCodes).containsExactly(Status.Code.OK);
        assertThat(serverObserver.finishedCodes).containsExactly(Status.Code.OK);
    }

    @Test
    void concurrencyLimit_shedsExcessCall_withResourceExhausted() throws Exception {
        String name = "adhar-grpc-" + UUID.randomUUID();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);

        // A service whose single in-flight call blocks until released, so the second
        // call deterministically collides with the global limit of 1.
        ServerServiceDefinition blockingService = ServerServiceDefinition.builder("test.EchoService")
                .addMethod(EchoServiceSupport.ECHO_METHOD, ServerCalls.asyncUnaryCall((request, obs) -> {
                    started.countDown();
                    try {
                        proceed.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    obs.onNext("echo:" + request);
                    obs.onCompleted();
                }))
                .build();

        ConcurrencyLimitServerInterceptor limiter =
                new ConcurrencyLimitServerInterceptor(1, Map.of());
        server = InProcessServerBuilder.forName(name)
                .addService(ServerInterceptors.intercept(blockingService, limiter))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).usePlaintext().build();

        AtomicReference<String> firstResponse = new AtomicReference<>();
        Thread firstCall = new Thread(() -> firstResponse.set(ClientCalls.blockingUnaryCall(
                channel, EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, "first")));
        firstCall.start();

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        // Second call arrives while the first is in-flight -> shed with RESOURCE_EXHAUSTED.
        assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(
                channel, EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, "second"))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.RESOURCE_EXHAUSTED));

        proceed.countDown();
        firstCall.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(firstResponse.get()).isEqualTo("echo:first");

        // Permit returned after the first call completed.
        assertThat(limiter.availableGlobalPermits()).isEqualTo(1);
    }

    private static final class CollectingObserver implements GrpcObserver {
        private final List<Kind> kinds = new CopyOnWriteArrayList<>();
        private final List<Status.Code> finishedCodes = new CopyOnWriteArrayList<>();

        @Override
        public Span start(String methodName, Kind kind, TraceContext context) {
            kinds.add(kind);
            return status -> finishedCodes.add(status.getCode());
        }
    }
}
