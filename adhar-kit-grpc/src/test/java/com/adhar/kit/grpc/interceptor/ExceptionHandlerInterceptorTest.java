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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ExceptionHandlerInterceptor}.
 *
 * <p>Exercises the wrapping {@code ServerCall.Listener} and the exception-to-{@link Status}
 * translation by driving the listener lifecycle methods and forcing the delegate to throw.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class ExceptionHandlerInterceptorTest {

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

    private ExceptionHandlerInterceptor interceptor;
    @SuppressWarnings("unchecked")
    private ServerCall<String, String> call = mock(ServerCall.class);
    @SuppressWarnings("unchecked")
    private ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
    @SuppressWarnings("unchecked")
    private ServerCall.Listener<String> delegate = mock(ServerCall.Listener.class);
    private Metadata headers;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        interceptor = new ExceptionHandlerInterceptor();
        call = mock(ServerCall.class);
        next = mock(ServerCallHandler.class);
        delegate = mock(ServerCall.Listener.class);
        headers = new Metadata();
        when(call.getMethodDescriptor()).thenReturn(METHOD);
        when(next.startCall(call, headers)).thenReturn(delegate);
    }

    private ServerCall.Listener<String> intercept() {
        return interceptor.interceptCall(call, headers, next);
    }

    private Status capturedStatus() {
        ArgumentCaptor<Status> captor = ArgumentCaptor.forClass(Status.class);
        verify(call).close(captor.capture(), any(Metadata.class));
        return captor.getValue();
    }

    @Test
    void onHalfClose_noException_delegatesWithoutClosing() {
        ServerCall.Listener<String> listener = intercept();

        listener.onHalfClose();

        verify(delegate).onHalfClose();
        verify(call, never()).close(any(Status.class), any(Metadata.class));
    }

    @Test
    void onHalfClose_throwsIllegalArgument_translatesToInvalidArgument() {
        doThrow(new IllegalArgumentException("bad arg")).when(delegate).onHalfClose();

        intercept().onHalfClose();

        Status status = capturedStatus();
        assertThat(status.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(status.getDescription()).isEqualTo("bad arg");
    }

    @Test
    void onMessage_throwsIllegalState_translatesToFailedPrecondition() {
        doThrow(new IllegalStateException("bad state")).when(delegate).onMessage(any());

        intercept().onMessage("req");

        assertThat(capturedStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void onMessage_noException_delegates() {
        intercept().onMessage("req");

        verify(delegate).onMessage("req");
    }

    @Test
    void onCancel_throwsNullPointer_translatesToInvalidArgument() {
        doThrow(new NullPointerException()).when(delegate).onCancel();

        intercept().onCancel();

        Status status = capturedStatus();
        assertThat(status.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(status.getDescription()).isEqualTo("Required field is null");
    }

    @Test
    void onCancel_noException_delegates() {
        intercept().onCancel();

        verify(delegate).onCancel();
    }

    @Test
    void securityException_translatesToPermissionDenied() {
        doThrow(new SecurityException("denied")).when(delegate).onHalfClose();

        intercept().onHalfClose();

        assertThat(capturedStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void unsupportedOperation_translatesToUnimplemented() {
        doThrow(new UnsupportedOperationException("nope")).when(delegate).onHalfClose();

        intercept().onHalfClose();

        assertThat(capturedStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
    }

    @Test
    void timeoutNamedException_translatesToDeadlineExceeded() {
        doThrow(new CustomTimeoutException()).when(delegate).onHalfClose();

        intercept().onHalfClose();

        assertThat(capturedStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
    }

    @Test
    void notFoundNamedException_translatesToNotFound() {
        doThrow(new ResourceNotFoundException()).when(delegate).onHalfClose();

        intercept().onHalfClose();

        assertThat(capturedStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void alreadyExistsNamedException_translatesToAlreadyExists() {
        doThrow(new EntityAlreadyExistsException()).when(delegate).onHalfClose();

        intercept().onHalfClose();

        assertThat(capturedStatus().getCode()).isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void statusRuntimeException_preservesOriginalStatus() {
        doThrow(Status.RESOURCE_EXHAUSTED.withDescription("limit").asRuntimeException())
                .when(delegate).onHalfClose();

        intercept().onHalfClose();

        assertThat(capturedStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
    }

    @Test
    void genericException_translatesToInternal() {
        doThrow(new RuntimeException("boom")).when(delegate).onHalfClose();

        intercept().onHalfClose();

        Status status = capturedStatus();
        assertThat(status.getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(status.getDescription()).contains("Internal server error");
    }

    private static class CustomTimeoutException extends RuntimeException {
    }

    private static class ResourceNotFoundException extends RuntimeException {
    }

    private static class EntityAlreadyExistsException extends RuntimeException {
    }
}
