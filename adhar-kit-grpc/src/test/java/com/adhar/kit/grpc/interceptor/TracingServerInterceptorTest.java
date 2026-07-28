package com.adhar.kit.grpc.interceptor;

import com.adhar.kit.grpc.testsupport.EchoServiceSupport;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link TracingServerInterceptor}: W3C {@code traceparent}
 * extraction, MDC correlation, gRPC context propagation and span recording.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class TracingServerInterceptorTest {

    private static final String INCOMING =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void noIncomingHeader_startsNewRootTrace_andSetsMdcAndContext() {
        CapturingObserver observer = new CapturingObserver();
        TracingServerInterceptor interceptor = new TracingServerInterceptor(observer);
        CapturingHandler handler = new CapturingHandler();

        interceptor.interceptCall(new RecordingServerCall(), new Metadata(), handler);

        assertThat(handler.contextInHandler).isNotNull();
        assertThat(handler.contextInHandler.getTraceId()).hasSize(32);
        assertThat(handler.mdcTraceId).isEqualTo(handler.contextInHandler.getTraceId());
        assertThat(handler.mdcSpanId).isEqualTo(handler.contextInHandler.getSpanId());
        assertThat(observer.startCount).isEqualTo(1);
        assertThat(observer.kind).isEqualTo(GrpcObserver.Kind.SERVER);
        assertThat(observer.methodName).isEqualTo(EchoServiceSupport.ECHO_METHOD.getFullMethodName());
    }

    @Test
    void incomingValidHeader_continuesTrace_withNewSpanId() {
        CapturingObserver observer = new CapturingObserver();
        TracingServerInterceptor interceptor = new TracingServerInterceptor(observer);
        CapturingHandler handler = new CapturingHandler();
        Metadata headers = new Metadata();
        headers.put(TracingServerInterceptor.TRACEPARENT_KEY, INCOMING);

        interceptor.interceptCall(new RecordingServerCall(), headers, handler);

        assertThat(handler.contextInHandler.getTraceId())
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(handler.contextInHandler.getSpanId())
                .isNotEqualTo("00f067aa0ba902b7");
        assertThat(observer.context.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void incomingInvalidHeader_startsNewRootTrace() {
        CapturingObserver observer = new CapturingObserver();
        TracingServerInterceptor interceptor = new TracingServerInterceptor(observer);
        CapturingHandler handler = new CapturingHandler();
        Metadata headers = new Metadata();
        headers.put(TracingServerInterceptor.TRACEPARENT_KEY, "not-a-valid-traceparent");

        interceptor.interceptCall(new RecordingServerCall(), headers, handler);

        assertThat(handler.contextInHandler).isNotNull();
        assertThat(handler.contextInHandler.getTraceId()).hasSize(32);
    }

    @Test
    void close_finishesSpan_andClearsMdc() {
        CapturingObserver observer = new CapturingObserver();
        TracingServerInterceptor interceptor = new TracingServerInterceptor(observer);
        CapturingHandler handler = new CapturingHandler();

        interceptor.interceptCall(new RecordingServerCall(), new Metadata(), handler);
        // MDC set during interceptCall; still present until the call closes.
        assertThat(MDC.get(TracingServerInterceptor.MDC_TRACE_ID)).isNotNull();

        handler.wrappedCall.close(Status.OK, new Metadata());

        assertThat(observer.finishCount).isEqualTo(1);
        assertThat(observer.finishedStatus.getCode()).isEqualTo(Status.Code.OK);
        assertThat(MDC.get(TracingServerInterceptor.MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(TracingServerInterceptor.MDC_SPAN_ID)).isNull();
    }

    @Test
    void nullObserver_defaultsToNoop_andDoesNotThrow() {
        TracingServerInterceptor interceptor = new TracingServerInterceptor(null);
        CapturingHandler handler = new CapturingHandler();

        assertThatCode(() -> {
            interceptor.interceptCall(new RecordingServerCall(), new Metadata(), handler);
            handler.wrappedCall.close(Status.OK, new Metadata());
        }).doesNotThrowAnyException();
    }

    private static final class RecordingServerCall extends ServerCall<String, String> {
        @Override
        public void request(int numMessages) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(String message) {
        }

        @Override
        public void close(Status status, Metadata trailers) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<String, String> getMethodDescriptor() {
            return EchoServiceSupport.ECHO_METHOD;
        }
    }

    private static final class CapturingHandler implements ServerCallHandler<String, String> {
        private ServerCall<String, String> wrappedCall;
        private TraceContext contextInHandler;
        private String mdcTraceId;
        private String mdcSpanId;

        @Override
        public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
            this.wrappedCall = call;
            this.contextInHandler = TracingServerInterceptor.TRACE_CONTEXT_KEY.get();
            this.mdcTraceId = MDC.get(TracingServerInterceptor.MDC_TRACE_ID);
            this.mdcSpanId = MDC.get(TracingServerInterceptor.MDC_SPAN_ID);
            return new ServerCall.Listener<>() {
            };
        }
    }

    private static final class CapturingObserver implements GrpcObserver {
        private String methodName;
        private Kind kind;
        private TraceContext context;
        private Status finishedStatus;
        private int startCount;
        private int finishCount;

        @Override
        public Span start(String methodName, Kind kind, TraceContext context) {
            this.methodName = methodName;
            this.kind = kind;
            this.context = context;
            this.startCount++;
            return status -> {
                this.finishedStatus = status;
                this.finishCount++;
            };
        }
    }
}
