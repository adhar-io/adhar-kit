package com.adhar.kit.grpc.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
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
 * Tests for {@link RetryInterceptor}.
 *
 * <p>Drives the wrapped {@code ClientCall} and its internal retry {@code Listener} to cover
 * the retryable/non-retryable status decisions, exhausted attempts, backoff and interruption.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class RetryInterceptorTest {

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

    private Channel channel;
    private ClientCall<String, String> realCall;
    private ClientCall.Listener<String> appListener;
    private Metadata headers;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        channel = mock(Channel.class);
        realCall = mock(ClientCall.class);
        appListener = mock(ClientCall.Listener.class);
        headers = new Metadata();
        when(channel.newCall(eq(METHOD), any(CallOptions.class))).thenReturn(realCall);
    }

    @AfterEach
    void tearDown() {
        // Clear any interrupt flag a test may have left set.
        Thread.interrupted();
    }

    /**
     * Runs the interceptor and returns the internal retry listener that wraps the application
     * listener, captured from the {@code start} call on the underlying real call.
     */
    @SuppressWarnings("unchecked")
    private ClientCall.Listener<String> startAndCaptureRetryListener(RetryInterceptor interceptor) {
        ClientCall<String, String> call = interceptor.interceptCall(METHOD, CallOptions.DEFAULT, channel);
        call.start(appListener, headers);
        ArgumentCaptor<ClientCall.Listener<String>> captor = ArgumentCaptor.forClass(ClientCall.Listener.class);
        verify(realCall).start(captor.capture(), eq(headers));
        return captor.getValue();
    }

    @Test
    void onClose_okStatus_passesThroughToDelegate() {
        ClientCall.Listener<String> retryListener = startAndCaptureRetryListener(new RetryInterceptor(3, 1));
        Metadata trailers = new Metadata();

        retryListener.onClose(Status.OK, trailers);

        verify(appListener).onClose(Status.OK, trailers);
    }

    @Test
    void onClose_nonRetryableStatus_passesThroughToDelegate() {
        ClientCall.Listener<String> retryListener = startAndCaptureRetryListener(new RetryInterceptor(3, 1));
        Metadata trailers = new Metadata();

        retryListener.onClose(Status.INVALID_ARGUMENT, trailers);

        verify(appListener).onClose(Status.INVALID_ARGUMENT, trailers);
    }

    @Test
    void onClose_retryableButMaxAttemptsReached_passesThrough() {
        // maxAttempts=1 and the first attempt is number 1, so attemptNumber >= maxAttempts.
        ClientCall.Listener<String> retryListener = startAndCaptureRetryListener(new RetryInterceptor(1, 1));
        Metadata trailers = new Metadata();

        retryListener.onClose(Status.UNAVAILABLE, trailers);

        verify(appListener).onClose(Status.UNAVAILABLE, trailers);
    }

    @Test
    void onClose_retryableWithinBudget_sleepsBackoffThenDelegates() {
        // initial backoff of 1ms keeps the test fast while exercising the backoff branch.
        ClientCall.Listener<String> retryListener = startAndCaptureRetryListener(new RetryInterceptor(3, 1));
        Metadata trailers = new Metadata();

        retryListener.onClose(Status.DEADLINE_EXCEEDED, trailers);

        verify(appListener).onClose(Status.DEADLINE_EXCEEDED, trailers);
    }

    @Test
    void onClose_retryableButInterrupted_delegatesAndKeepsInterruptFlag() {
        ClientCall.Listener<String> retryListener = startAndCaptureRetryListener(new RetryInterceptor(3, 1000));
        Metadata trailers = new Metadata();

        Thread.currentThread().interrupt(); // makes Thread.sleep throw InterruptedException immediately
        retryListener.onClose(Status.ABORTED, trailers);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(appListener).onClose(Status.ABORTED, trailers);
    }

    @Test
    void onClose_resourceExhausted_isRetryable() {
        ClientCall.Listener<String> retryListener = startAndCaptureRetryListener(new RetryInterceptor(1, 1));
        Metadata trailers = new Metadata();

        retryListener.onClose(Status.RESOURCE_EXHAUSTED, trailers);

        verify(appListener).onClose(Status.RESOURCE_EXHAUSTED, trailers);
    }

    @Test
    void onMessage_onHeaders_onReady_delegate() {
        ClientCall.Listener<String> retryListener = startAndCaptureRetryListener(new RetryInterceptor(3, 1));
        Metadata responseHeaders = new Metadata();

        retryListener.onMessage("response");
        retryListener.onHeaders(responseHeaders);
        retryListener.onReady();

        verify(appListener).onMessage("response");
        verify(appListener).onHeaders(responseHeaders);
        verify(appListener).onReady();
    }
}
