package com.adhar.kit.grpc.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link MetricsClientInterceptor}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class MetricsClientInterceptorTest {

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
    private Channel channel;
    @SuppressWarnings("unchecked")
    private final ClientCall<String, String> realCall = mock(ClientCall.class);
    @SuppressWarnings("unchecked")
    private final ClientCall.Listener<String> appListener = mock(ClientCall.Listener.class);

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        channel = mock(Channel.class);
        when(channel.newCall(eq(METHOD), any(CallOptions.class))).thenReturn(realCall);
    }

    @Test
    void onClose_recordsCounterAndTimer() {
        MetricsClientInterceptor interceptor = new MetricsClientInterceptor(registry);
        ClientCall<String, String> call = interceptor.interceptCall(METHOD, CallOptions.DEFAULT, channel);

        Metadata headers = new Metadata();
        call.start(appListener, headers);

        ClientCall.Listener<String> wrappedListener = captureListener();
        wrappedListener.onClose(Status.OK, new Metadata());

        assertThat(registry.counter("grpc.client.calls", "method", METHOD.getFullMethodName(), "status", "OK")
                .count()).isEqualTo(1.0);
        assertThat(registry.timer("grpc.client.duration", "method", METHOD.getFullMethodName(), "status", "OK")
                .count()).isEqualTo(1L);
    }

    @Test
    void onClose_withErrorStatus_recordsErrorStatusTag() {
        MetricsClientInterceptor interceptor = new MetricsClientInterceptor(registry);
        ClientCall<String, String> call = interceptor.interceptCall(METHOD, CallOptions.DEFAULT, channel);

        Metadata headers = new Metadata();
        call.start(appListener, headers);

        ClientCall.Listener<String> wrappedListener = captureListener();
        wrappedListener.onClose(Status.UNAVAILABLE, new Metadata());

        assertThat(registry.counter("grpc.client.calls", "method", METHOD.getFullMethodName(), "status", "UNAVAILABLE")
                .count()).isEqualTo(1.0);
    }

    @SuppressWarnings("unchecked")
    private ClientCall.Listener<String> captureListener() {
        ArgumentCaptor<ClientCall.Listener> captor = ArgumentCaptor.forClass(ClientCall.Listener.class);
        verify(realCall).start(captor.capture(), any());
        return captor.getValue();
    }
}
