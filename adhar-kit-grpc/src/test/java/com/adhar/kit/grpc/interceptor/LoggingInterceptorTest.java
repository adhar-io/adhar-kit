package com.adhar.kit.grpc.interceptor;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
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
 * Tests for {@link LoggingInterceptor}.
 *
 * <p>Verifies correlation/request id propagation, response header emission and the
 * wrapped {@code ServerCall.close} logging/MDC behaviour for both success and failure.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class LoggingInterceptorTest {

    private static final Metadata.Key<String> CORRELATION_ID_KEY =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> REQUEST_ID_KEY =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

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

    private LoggingInterceptor interceptor;
    private ServerCall<String, String> call;
    private ServerCallHandler<String, String> next;
    private ServerCall.Listener<String> delegateListener;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        interceptor = new LoggingInterceptor();
        call = mock(ServerCall.class);
        next = mock(ServerCallHandler.class);
        delegateListener = mock(ServerCall.Listener.class);
        when(call.getMethodDescriptor()).thenReturn(METHOD);
        when(next.startCall(any(), any())).thenReturn(delegateListener);
    }

    @SuppressWarnings("unchecked")
    private ServerCall<String, String> capturedWrappedCall() {
        ArgumentCaptor<ServerCall<String, String>> captor = ArgumentCaptor.forClass(ServerCall.class);
        verify(next).startCall(captor.capture(), any(Metadata.class));
        return captor.getValue();
    }

    @Test
    void interceptCall_generatesIdsWhenAbsent_andSendsResponseHeaders() {
        Metadata headers = new Metadata();

        ServerCall.Listener<String> result = interceptor.interceptCall(call, headers, next);

        assertThat(result).isSameAs(delegateListener);

        ArgumentCaptor<Metadata> headerCaptor = ArgumentCaptor.forClass(Metadata.class);
        verify(call).sendHeaders(headerCaptor.capture());
        Metadata responseHeaders = headerCaptor.getValue();
        assertThat(responseHeaders.get(CORRELATION_ID_KEY)).isNotBlank();
        assertThat(responseHeaders.get(REQUEST_ID_KEY)).isNotBlank();
    }

    @Test
    void interceptCall_usesProvidedIds() {
        Metadata headers = new Metadata();
        headers.put(CORRELATION_ID_KEY, "corr-123");
        headers.put(REQUEST_ID_KEY, "req-456");

        interceptor.interceptCall(call, headers, next);

        ArgumentCaptor<Metadata> headerCaptor = ArgumentCaptor.forClass(Metadata.class);
        verify(call).sendHeaders(headerCaptor.capture());
        assertThat(headerCaptor.getValue().get(CORRELATION_ID_KEY)).isEqualTo("corr-123");
        assertThat(headerCaptor.getValue().get(REQUEST_ID_KEY)).isEqualTo("req-456");
    }

    @Test
    void wrappedCall_closeOk_delegatesToOriginalCall() {
        Metadata headers = new Metadata();
        interceptor.interceptCall(call, headers, next);

        ServerCall<String, String> wrapped = capturedWrappedCall();
        Metadata trailers = new Metadata();
        wrapped.close(Status.OK, trailers);

        verify(call).close(eq(Status.OK), eq(trailers));
    }

    @Test
    void wrappedCall_closeError_delegatesToOriginalCall() {
        Metadata headers = new Metadata();
        interceptor.interceptCall(call, headers, next);

        ServerCall<String, String> wrapped = capturedWrappedCall();
        Status error = Status.INTERNAL.withDescription("boom");
        Metadata trailers = new Metadata();
        wrapped.close(error, trailers);

        verify(call).close(eq(error), eq(trailers));
    }
}
