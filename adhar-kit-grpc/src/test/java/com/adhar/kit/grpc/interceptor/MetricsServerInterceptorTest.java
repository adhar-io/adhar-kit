package com.adhar.kit.grpc.interceptor;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MetricsServerInterceptor}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class MetricsServerInterceptorTest {

    private static final MethodDescriptor.Marshaller<String> MARSHALLER =
            new MethodDescriptor.Marshaller<>() {
                @Override
                public InputStream stream(String value) {
                    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public String parse(InputStream stream) {
                    return "";
                }
            };

    private static final MethodDescriptor<String, String> METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("test.Service/Method")
            .setRequestMarshaller(MARSHALLER)
            .setResponseMarshaller(MARSHALLER)
            .build();

    private SimpleMeterRegistry registry;
    @SuppressWarnings("unchecked")
    private final ServerCall<String, String> call = mock(ServerCall.class);
    @SuppressWarnings("unchecked")
    private final ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
    @SuppressWarnings("unchecked")
    private final ServerCall.Listener<String> listener = mock(ServerCall.Listener.class);

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        when(call.getMethodDescriptor()).thenReturn(METHOD);
        when(next.startCall(any(), any())).thenReturn(listener);
    }

    @Test
    void inFlightGauge_isRegisteredAndTracksActiveCalls() {
        MetricsServerInterceptor interceptor = new MetricsServerInterceptor(registry);
        assertThat(registry.find("grpc.server.calls.inflight").gauge().value()).isEqualTo(0.0);

        Metadata headers = new Metadata();
        ServerCall.Listener<String> resultListener = interceptor.interceptCall(call, headers, next);
        assertThat(resultListener).isNotNull();
        assertThat(interceptor.getInFlightCount()).isEqualTo(1);
        assertThat(registry.find("grpc.server.calls.inflight").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void close_recordsCounterAndTimer_andDecrementsInFlight() {
        MetricsServerInterceptor interceptor = new MetricsServerInterceptor(registry);
        Metadata headers = new Metadata();
        interceptor.interceptCall(call, headers, next);

        ServerCall<String, String> wrappedCall = captureWrappedCall();
        wrappedCall.close(Status.OK, new Metadata());

        assertThat(interceptor.getInFlightCount()).isEqualTo(0);
        assertThat(registry.counter("grpc.server.calls", "method", METHOD.getFullMethodName(), "status", "OK")
                .count()).isEqualTo(1.0);
        assertThat(registry.timer("grpc.server.duration", "method", METHOD.getFullMethodName(), "status", "OK")
                .count()).isEqualTo(1L);
    }

    @Test
    void close_withErrorStatus_recordsErrorStatusTag() {
        MetricsServerInterceptor interceptor = new MetricsServerInterceptor(registry);
        Metadata headers = new Metadata();
        interceptor.interceptCall(call, headers, next);

        ServerCall<String, String> wrappedCall = captureWrappedCall();
        wrappedCall.close(Status.INTERNAL, new Metadata());

        assertThat(registry.counter("grpc.server.calls", "method", METHOD.getFullMethodName(), "status", "INTERNAL")
                .count()).isEqualTo(1.0);
    }

    @SuppressWarnings("unchecked")
    private ServerCall<String, String> captureWrappedCall() {
        ArgumentCaptor<ServerCall> captor = ArgumentCaptor.forClass(ServerCall.class);
        verify(next).startCall(captor.capture(), any());
        return captor.getValue();
    }
}
