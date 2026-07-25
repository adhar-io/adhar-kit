package com.adhar.kit.grpc.interceptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Deadline;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * Tests for {@link DeadlineClientInterceptor}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class DeadlineClientInterceptorTest {

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
    @SuppressWarnings("unchecked")
    private final ClientCall<String, String> realCall = mock(ClientCall.class);

    @BeforeEach
    void setUp() {
        channel = mock(Channel.class);
        when(channel.newCall(eq(METHOD), any(CallOptions.class))).thenReturn(realCall);
    }

    @Test
    void appliesDefaultDeadline_whenCallerSetNone() {
        DeadlineClientInterceptor interceptor = new DeadlineClientInterceptor(5000);

        interceptor.interceptCall(METHOD, CallOptions.DEFAULT, channel);

        CallOptions captured = captureCallOptions();
        assertThat(captured.getDeadline()).isNotNull();
        // Allow generous slack for test execution time.
        assertThat(captured.getDeadline().timeRemaining(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isLessThanOrEqualTo(5000)
                .isGreaterThan(0);
    }

    @Test
    void doesNotOverrideExistingDeadline() {
        DeadlineClientInterceptor interceptor = new DeadlineClientInterceptor(50);
        Deadline explicitDeadline = Deadline.after(1, java.util.concurrent.TimeUnit.HOURS);
        CallOptions optionsWithDeadline = CallOptions.DEFAULT.withDeadline(explicitDeadline);

        interceptor.interceptCall(METHOD, optionsWithDeadline, channel);

        CallOptions captured = captureCallOptions();
        assertThat(captured.getDeadline()).isSameAs(explicitDeadline);
    }

    @Test
    void zeroOrNegativeDefaultTimeout_disablesDefaulting() {
        DeadlineClientInterceptor interceptor = new DeadlineClientInterceptor(0);

        interceptor.interceptCall(METHOD, CallOptions.DEFAULT, channel);

        CallOptions captured = captureCallOptions();
        assertThat(captured.getDeadline()).isNull();
    }

    /**
     * Captures the {@link CallOptions} passed to {@code channel.newCall}.
     */
    private CallOptions captureCallOptions() {
        org.mockito.ArgumentCaptor<CallOptions> captor = org.mockito.ArgumentCaptor.forClass(CallOptions.class);
        verify(channel).newCall(eq(METHOD), captor.capture());
        return captor.getValue();
    }
}
