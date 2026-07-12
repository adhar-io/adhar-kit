package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GrpcHealthIndicator}.
 *
 * <p>gRPC interaction is performed reflectively against the standard health
 * checking protocol. A fake {@link Channel} drives the request/response and error
 * handling without any network transport.</p>
 */
class GrpcHealthIndicatorTest {

    private AdharHealthProperties.GrpcConfig config() {
        return new AdharHealthProperties.GrpcConfig();
    }

    @Test
    void getName_returnsGrpc() {
        GrpcHealthIndicator indicator = new GrpcHealthIndicator(new FakeChannel(), config());
        assertThat(indicator.getName()).isEqualTo("grpc");
    }

    @Test
    void check_whenDisabled_returnsUnknown() {
        AdharHealthProperties.GrpcConfig config = config();
        config.setEnabled(false);
        GrpcHealthIndicator indicator = new GrpcHealthIndicator(new FakeChannel(), config);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("status", "disabled");
    }

    @Test
    @SuppressWarnings("unchecked")
    void check_whenRpcFails_returnsDownWithServiceStatuses() {
        GrpcHealthIndicator indicator = new GrpcHealthIndicator(new FakeChannel(), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        Map<String, String> services = (Map<String, String>) health.getDetails().get("services");
        assertThat(services).containsKey("overall");
        assertThat(services.get("overall")).startsWith("ERROR");
        assertThat(health.getDetails()).containsEntry("channelState", "READY");
    }

    @Test
    void check_whenChannelIsNotGrpcChannel_returnsDown() {
        // A plain object is not assignable to io.grpc.Channel, so newBlockingStub fails
        // with an IllegalArgumentException handled by the generic error branch.
        GrpcHealthIndicator indicator = new GrpcHealthIndicator(new Object(), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void check_whenServiceServing_returnsUp() {
        GrpcHealthIndicator indicator = new GrpcHealthIndicator(new ServingChannel(), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        Map<String, String> services = (Map<String, String>) health.getDetails().get("services");
        assertThat(services).containsEntry("overall", "SERVING");
        assertThat(health.getDetails()).containsEntry("channelState", "READY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void check_namedServiceServing_returnsUp() {
        GrpcHealthIndicator indicator = new GrpcHealthIndicator(
            new ServingChannel(HealthCheckResponse.ServingStatus.SERVING), config(), new String[]{"svc.A"});

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        Map<String, String> services = (Map<String, String>) health.getDetails().get("services");
        assertThat(services).containsEntry("svc.A", "SERVING");
    }

    @Test
    void check_whenServiceNotServing_returnsDown() {
        GrpcHealthIndicator indicator = new GrpcHealthIndicator(
            new ServingChannel(HealthCheckResponse.ServingStatus.NOT_SERVING), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
    }

    @Test
    void check_whenChannelHasNoGetState_returnsUpWithoutChannelStateDetail() {
        GrpcHealthIndicator indicator = new GrpcHealthIndicator(new NoStateServingChannel(), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails()).doesNotContainKey("channelState");
    }

    @Test
    void check_withNamedServices_returnsDown() {
        GrpcHealthIndicator indicator =
            new GrpcHealthIndicator(new FakeChannel(), config(), new String[]{"svc.A", "svc.B"});

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
    }

    /** Fake channel that has no working transport; every call fails. */
    public static class FakeChannel extends Channel {
        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            throw new IllegalStateException("no transport available");
        }

        @Override
        public String authority() {
            return "fake-authority";
        }

        public Object getState(boolean requestConnection) {
            return "READY";
        }
    }

    /** Fake channel that synchronously answers every health check with a fixed status. */
    public static class ServingChannel extends Channel {
        private final HealthCheckResponse.ServingStatus status;

        public ServingChannel() {
            this(HealthCheckResponse.ServingStatus.SERVING);
        }

        public ServingChannel(HealthCheckResponse.ServingStatus status) {
            this.status = status;
        }

        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            return new ServingCall<>(status);
        }

        @Override
        public String authority() {
            return "serving-authority";
        }

        public Object getState(boolean requestConnection) {
            return "READY";
        }
    }

    /** Serving channel that exposes no getState(boolean) method, exercising that catch branch. */
    public static class NoStateServingChannel extends Channel {
        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            return new ServingCall<>(HealthCheckResponse.ServingStatus.SERVING);
        }

        @Override
        public String authority() {
            return "no-state-authority";
        }
    }

    /** Minimal ClientCall that delivers a fixed serving status on half-close. */
    public static class ServingCall<RequestT, ResponseT> extends ClientCall<RequestT, ResponseT> {
        private final HealthCheckResponse.ServingStatus status;
        private Listener<ResponseT> listener;

        public ServingCall(HealthCheckResponse.ServingStatus status) {
            this.status = status;
        }

        @Override
        public void start(Listener<ResponseT> responseListener, Metadata headers) {
            this.listener = responseListener;
        }

        @Override
        public void request(int numMessages) {
            // no-op
        }

        @Override
        public void cancel(String message, Throwable cause) {
            // no-op
        }

        @Override
        @SuppressWarnings("unchecked")
        public void halfClose() {
            HealthCheckResponse response = HealthCheckResponse.newBuilder()
                .setStatus(status)
                .build();
            listener.onMessage((ResponseT) response);
            listener.onClose(Status.OK, new Metadata());
        }

        @Override
        public void sendMessage(RequestT message) {
            // no-op
        }
    }
}
