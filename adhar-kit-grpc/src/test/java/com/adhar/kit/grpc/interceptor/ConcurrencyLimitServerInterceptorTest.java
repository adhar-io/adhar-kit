package com.adhar.kit.grpc.interceptor;

import com.adhar.kit.grpc.testsupport.EchoServiceSupport;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConcurrencyLimitServerInterceptor}: global and
 * per-service limiting, {@code RESOURCE_EXHAUSTED} rejection, and permit
 * release on completion/cancellation.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class ConcurrencyLimitServerInterceptorTest {

    private static final String SERVICE = "test.EchoService";

    @Test
    void globalLimit_rejectsSecondConcurrentCall_withResourceExhausted() {
        ConcurrencyLimitServerInterceptor interceptor =
                new ConcurrencyLimitServerInterceptor(1, null);

        RecordingServerCall first = new RecordingServerCall();
        interceptor.interceptCall(first, new Metadata(), new NoopHandler());
        assertThat(interceptor.availableGlobalPermits()).isZero();
        assertThat(first.closedStatus).isNull();

        RecordingServerCall second = new RecordingServerCall();
        interceptor.interceptCall(second, new Metadata(), new NoopHandler());
        assertThat(second.closedStatus).isNotNull();
        assertThat(second.closedStatus.getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(second.closedStatus.getDescription()).contains("global");
    }

    @Test
    void permitReleased_onComplete() {
        ConcurrencyLimitServerInterceptor interceptor =
                new ConcurrencyLimitServerInterceptor(1, null);

        ServerCall.Listener<String> listener =
                interceptor.interceptCall(new RecordingServerCall(), new Metadata(), new NoopHandler());
        assertThat(interceptor.availableGlobalPermits()).isZero();

        listener.onComplete();
        assertThat(interceptor.availableGlobalPermits()).isEqualTo(1);
    }

    @Test
    void permitReleased_onCancel() {
        ConcurrencyLimitServerInterceptor interceptor =
                new ConcurrencyLimitServerInterceptor(1, null);

        ServerCall.Listener<String> listener =
                interceptor.interceptCall(new RecordingServerCall(), new Metadata(), new NoopHandler());
        assertThat(interceptor.availableGlobalPermits()).isZero();

        listener.onCancel();
        assertThat(interceptor.availableGlobalPermits()).isEqualTo(1);
    }

    @Test
    void release_isIdempotent_acrossCompleteThenCancel() {
        ConcurrencyLimitServerInterceptor interceptor =
                new ConcurrencyLimitServerInterceptor(1, null);

        ServerCall.Listener<String> listener =
                interceptor.interceptCall(new RecordingServerCall(), new Metadata(), new NoopHandler());

        listener.onComplete();
        listener.onCancel();
        // Released exactly once -> back to the original single permit, not two.
        assertThat(interceptor.availableGlobalPermits()).isEqualTo(1);
    }

    @Test
    void perServiceLimit_rejectsSecondCallToSameService() {
        ConcurrencyLimitServerInterceptor interceptor =
                new ConcurrencyLimitServerInterceptor(0, Map.of(SERVICE, 1));

        interceptor.interceptCall(new RecordingServerCall(), new Metadata(), new NoopHandler());
        assertThat(interceptor.availableServicePermits(SERVICE)).isZero();

        RecordingServerCall second = new RecordingServerCall();
        interceptor.interceptCall(second, new Metadata(), new NoopHandler());
        assertThat(second.closedStatus.getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(second.closedStatus.getDescription()).contains("per-service");
    }

    @Test
    void servicePermitReleased_whenGlobalLimitExhausted() {
        ConcurrencyLimitServerInterceptor interceptor =
                new ConcurrencyLimitServerInterceptor(1, Map.of(SERVICE, 5));

        // First call takes the single global permit and one service permit.
        interceptor.interceptCall(new RecordingServerCall(), new Metadata(), new NoopHandler());
        assertThat(interceptor.availableServicePermits(SERVICE)).isEqualTo(4);
        assertThat(interceptor.availableGlobalPermits()).isZero();

        // Second call acquires a service permit but is rejected by the global limit;
        // its service permit must be returned, leaving 4 available (the first call's held).
        RecordingServerCall second = new RecordingServerCall();
        interceptor.interceptCall(second, new Metadata(), new NoopHandler());
        assertThat(second.closedStatus.getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(interceptor.availableServicePermits(SERVICE)).isEqualTo(4);
    }

    @Test
    void noLimits_allowsCallsAndReportsUnlimitedPermits() {
        ConcurrencyLimitServerInterceptor interceptor =
                new ConcurrencyLimitServerInterceptor(0, null);

        RecordingServerCall call = new RecordingServerCall();
        interceptor.interceptCall(call, new Metadata(), new NoopHandler());

        assertThat(call.closedStatus).isNull();
        assertThat(interceptor.availableGlobalPermits()).isEqualTo(Integer.MAX_VALUE);
        assertThat(interceptor.availableServicePermits(SERVICE)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void nonPositiveServiceLimit_isIgnored() {
        ConcurrencyLimitServerInterceptor interceptor =
                new ConcurrencyLimitServerInterceptor(0, Map.of(SERVICE, 0));

        RecordingServerCall call = new RecordingServerCall();
        interceptor.interceptCall(call, new Metadata(), new NoopHandler());

        assertThat(call.closedStatus).isNull();
        assertThat(interceptor.availableServicePermits(SERVICE)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void startCallFailure_releasesPermit() {
        ConcurrencyLimitServerInterceptor interceptor =
                new ConcurrencyLimitServerInterceptor(1, null);

        try {
            interceptor.interceptCall(new RecordingServerCall(), new Metadata(),
                    (call, headers) -> {
                        throw new IllegalStateException("handler blew up");
                    });
        } catch (IllegalStateException expected) {
            // ignored
        }

        assertThat(interceptor.availableGlobalPermits()).isEqualTo(1);
    }

    private static final class RecordingServerCall extends ServerCall<String, String> {
        private Status closedStatus;

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
            this.closedStatus = status;
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

    private static final class NoopHandler implements ServerCallHandler<String, String> {
        @Override
        public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
            return new ServerCall.Listener<>() {
            };
        }
    }
}
