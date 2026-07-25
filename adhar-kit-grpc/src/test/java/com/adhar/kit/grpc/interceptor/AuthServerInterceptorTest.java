package com.adhar.kit.grpc.interceptor;

import com.adhar.kit.grpc.server.GrpcAuthenticator;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AuthServerInterceptor}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class AuthServerInterceptorTest {

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

    @Test
    @SuppressWarnings("unchecked")
    void allowsAuthenticatedCall_andPropagatesPrincipalInContext() {
        GrpcAuthenticator authenticator = mock(GrpcAuthenticator.class);
        when(authenticator.authenticate(any())).thenReturn(GrpcAuthenticator.AuthResult.success("bob"));
        AuthServerInterceptor interceptor = new AuthServerInterceptor(authenticator);

        ServerCall<String, String> call = mock(ServerCall.class);
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
        ServerCall.Listener<String> expectedListener = mock(ServerCall.Listener.class);
        String[] principalSeenByService = new String[1];
        when(next.startCall(eq(call), any())).thenAnswer(invocation -> {
            principalSeenByService[0] = AuthServerInterceptor.PRINCIPAL_CONTEXT_KEY.get();
            return expectedListener;
        });

        Metadata headers = new Metadata();
        ServerCall.Listener<String> resultListener = interceptor.interceptCall(call, headers, next);

        // Contexts.interceptCall wraps the listener returned by next.startCall in a
        // context-reattaching listener, so it won't be the same instance - but it must
        // still delegate through to it (verified below via onMessage propagation).
        assertThat(resultListener).isNotNull();
        assertThat(principalSeenByService[0]).isEqualTo("bob");
        verify(next).startCall(eq(call), eq(headers));
        verify(call, never()).close(any(), any());

        resultListener.onMessage("ping");
        verify(expectedListener).onMessage("ping");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deniesUnauthenticatedCall_withUnauthenticatedStatus() {
        GrpcAuthenticator authenticator = mock(GrpcAuthenticator.class);
        when(authenticator.authenticate(any())).thenReturn(GrpcAuthenticator.AuthResult.failure("bad token"));
        AuthServerInterceptor interceptor = new AuthServerInterceptor(authenticator);

        ServerCall<String, String> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn(METHOD);
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
        Metadata headers = new Metadata();

        ServerCall.Listener<String> resultListener = interceptor.interceptCall(call, headers, next);

        assertThat(resultListener).isNotNull();
        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(call).close(statusCaptor.capture(), any());
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(statusCaptor.getValue().getDescription()).isEqualTo("bad token");
        verify(next, never()).startCall(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deniesUnauthenticatedCall_usesDefaultMessage_whenNoneProvided() {
        GrpcAuthenticator authenticator = mock(GrpcAuthenticator.class);
        when(authenticator.authenticate(any())).thenReturn(new GrpcAuthenticator.AuthResult(false, null, null));
        AuthServerInterceptor interceptor = new AuthServerInterceptor(authenticator);

        ServerCall<String, String> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn(METHOD);
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);

        interceptor.interceptCall(call, new Metadata(), next);

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(call).close(statusCaptor.capture(), any());
        assertThat(statusCaptor.getValue().getDescription()).isEqualTo("Authentication failed");
    }
}
