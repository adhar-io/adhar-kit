package com.adhar.kit.grpc.interceptor;

import com.adhar.kit.grpc.testsupport.EchoServiceSupport;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TracingClientInterceptor}: parent-context resolution
 * (gRPC context, MDC, or new root), W3C {@code traceparent} injection and span
 * recording on completion.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class TracingClientInterceptorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void injectsTraceparentHeader_whenNoParentContext() {
        RecordingChannel channel = new RecordingChannel();
        CapturingObserver observer = new CapturingObserver();
        TracingClientInterceptor interceptor = new TracingClientInterceptor(observer);

        ClientCall<String, String> call = interceptor.interceptCall(
                EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, channel);
        Metadata headers = new Metadata();
        call.start(new ClientCall.Listener<>() {
        }, headers);

        String traceparent = headers.get(TracingServerInterceptor.TRACEPARENT_KEY);
        assertThat(traceparent).isNotNull();
        assertThat(TraceContext.parse(traceparent)).isNotNull();
        assertThat(observer.kind).isEqualTo(GrpcObserver.Kind.CLIENT);
        assertThat(observer.methodName).isEqualTo(EchoServiceSupport.ECHO_METHOD.getFullMethodName());
    }

    @Test
    void continuesTrace_fromGrpcContext_asChildSpan() {
        TraceContext parent = TraceContext.parse(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        RecordingChannel channel = new RecordingChannel();
        TracingClientInterceptor interceptor = new TracingClientInterceptor(GrpcObserver.NOOP);
        Metadata headers = new Metadata();

        Context context = Context.current()
                .withValue(TracingServerInterceptor.TRACE_CONTEXT_KEY, parent);
        context.run(() -> {
            ClientCall<String, String> call = interceptor.interceptCall(
                    EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, channel);
            call.start(new ClientCall.Listener<>() {
            }, headers);
        });

        TraceContext propagated = TraceContext.parse(headers.get(TracingServerInterceptor.TRACEPARENT_KEY));
        assertThat(propagated).isNotNull();
        assertThat(propagated.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(propagated.getSpanId()).isNotEqualTo("00f067aa0ba902b7");
    }

    @Test
    void continuesTrace_fromMdc_whenNoGrpcContext() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        MDC.put(TracingServerInterceptor.MDC_TRACE_ID, traceId);
        RecordingChannel channel = new RecordingChannel();
        TracingClientInterceptor interceptor = new TracingClientInterceptor(GrpcObserver.NOOP);
        Metadata headers = new Metadata();

        ClientCall<String, String> call = interceptor.interceptCall(
                EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, channel);
        call.start(new ClientCall.Listener<>() {
        }, headers);

        TraceContext propagated = TraceContext.parse(headers.get(TracingServerInterceptor.TRACEPARENT_KEY));
        assertThat(propagated).isNotNull();
        assertThat(propagated.getTraceId()).isEqualTo(traceId);
    }

    @Test
    void ignoresInvalidMdcTraceId_andStartsNewRoot() {
        MDC.put(TracingServerInterceptor.MDC_TRACE_ID, "too-short");
        RecordingChannel channel = new RecordingChannel();
        TracingClientInterceptor interceptor = new TracingClientInterceptor(GrpcObserver.NOOP);
        Metadata headers = new Metadata();

        ClientCall<String, String> call = interceptor.interceptCall(
                EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, channel);
        call.start(new ClientCall.Listener<>() {
        }, headers);

        assertThat(TraceContext.parse(headers.get(TracingServerInterceptor.TRACEPARENT_KEY))).isNotNull();
    }

    @Test
    void finishesSpan_onClose() {
        RecordingChannel channel = new RecordingChannel();
        CapturingObserver observer = new CapturingObserver();
        TracingClientInterceptor interceptor = new TracingClientInterceptor(observer);

        ClientCall<String, String> call = interceptor.interceptCall(
                EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, channel);
        call.start(new ClientCall.Listener<>() {
        }, new Metadata());
        // The interceptor wraps the response listener; drive onClose through the delegate call.
        channel.lastCall.capturedListener.onClose(Status.OK, new Metadata());

        assertThat(observer.finishCount).isEqualTo(1);
        assertThat(observer.finishedStatus.getCode()).isEqualTo(Status.Code.OK);
    }

    @Test
    void overwritesPreexistingTraceparentHeader() {
        RecordingChannel channel = new RecordingChannel();
        TracingClientInterceptor interceptor = new TracingClientInterceptor(GrpcObserver.NOOP);
        Metadata headers = new Metadata();
        headers.put(TracingServerInterceptor.TRACEPARENT_KEY, "stale-value");

        ClientCall<String, String> call = interceptor.interceptCall(
                EchoServiceSupport.ECHO_METHOD, CallOptions.DEFAULT, channel);
        call.start(new ClientCall.Listener<>() {
        }, headers);

        assertThat(headers.getAll(TracingServerInterceptor.TRACEPARENT_KEY)).hasSize(1);
        assertThat(headers.get(TracingServerInterceptor.TRACEPARENT_KEY)).isNotEqualTo("stale-value");
    }

    private static final class RecordingChannel extends Channel {
        private RecordingClientCall lastCall;

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
            RecordingClientCall call = new RecordingClientCall();
            this.lastCall = call;
            @SuppressWarnings("unchecked")
            ClientCall<ReqT, RespT> typed = (ClientCall<ReqT, RespT>) call;
            return typed;
        }

        @Override
        public String authority() {
            return "test-authority";
        }
    }

    private static final class RecordingClientCall extends ClientCall<String, String> {
        private Listener<String> capturedListener;

        @Override
        public void start(Listener<String> responseListener, Metadata headers) {
            this.capturedListener = responseListener;
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
        }

        @Override
        public void halfClose() {
        }

        @Override
        public void sendMessage(String message) {
        }
    }

    private static final class CapturingObserver implements GrpcObserver {
        private String methodName;
        private Kind kind;
        private Status finishedStatus;
        private int finishCount;

        @Override
        public Span start(String methodName, Kind kind, TraceContext context) {
            this.methodName = methodName;
            this.kind = kind;
            return status -> {
                this.finishedStatus = status;
                this.finishCount++;
            };
        }
    }
}
