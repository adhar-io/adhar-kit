package com.adhar.kit.dapr;

import io.dapr.exceptions.DaprException;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Translation of every kind of failure into {@link DaprError}.
 */
class DaprErrorTest {

    @Test
    void sdkErrorsKeepDaprsCodeAndMessage() {
        DaprError error = DaprError.from("Failed to get state",
                new DaprException("NOT_FOUND", "key k not found", (Throwable) null));

        assertThat(error.getStatus()).isEqualTo(404);
        assertThat(error.isNotFound()).isTrue();
        assertThat(error.getErrorCode()).isEqualTo("NOT_FOUND");
        assertThat(error.getDaprMessage()).isEqualTo("key k not found");
        assertThat(error.getMessage()).isEqualTo("Failed to get state: key k not found");
        assertThat(error).isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void grpcStatusesMapToHttpStatuses() {
        assertThat(DaprError.from("x", Status.UNAVAILABLE.withDescription("down").asRuntimeException()).isUnavailable())
                .isTrue();
        assertThat(DaprError.from("x", Status.ABORTED.asRuntimeException()).isConflict()).isTrue();
        assertThat(DaprError.statusOfGrpcCode("PERMISSION_DENIED")).isEqualTo(403);
        assertThat(DaprError.statusOfGrpcCode("DEADLINE_EXCEEDED")).isEqualTo(504);
        assertThat(DaprError.statusOfGrpcCode("RESOURCE_EXHAUSTED")).isEqualTo(429);
        assertThat(DaprError.statusOfGrpcCode(null)).isEqualTo(500);
    }

    @Test
    void transportFailuresAreUnavailableAndTimeoutsAre504() {
        DaprError refused = DaprError.from("Failed", new IOException("Connection refused"));
        assertThat(refused.getStatus()).isEqualTo(DaprError.NO_RESPONSE);
        assertThat(refused.isUnavailable()).isTrue();
        assertThat(DaprError.from("Failed", new HttpTimeoutException("timed out")).getStatus()).isEqualTo(504);
    }

    @Test
    void wrappersAreLookedThroughAndDaprErrorsPassUnchanged() {
        DaprError original = new DaprError("boom", 409, "ETAG", "etag mismatch");
        assertThat(DaprError.from("other", new java.util.concurrent.CompletionException(original))).isSameAs(original);
        DaprError generic = DaprError.from("Failed to publish", new IllegalStateException("broker"));
        assertThat(generic.getStatus()).isEqualTo(500);
        assertThat(generic.getMessage()).isEqualTo("Failed to publish: broker");
    }

    @Test
    void bodiesAndDetailsAreExposed() {
        DaprError error = new DaprError("Invocation failed", 422, "HTTP_422", "invalid", Map.of("field", "qty"),
                "{\"detail\":\"invalid\"}".getBytes(), null);
        assertThat(error.getBodyAsString()).isEqualTo("{\"detail\":\"invalid\"}");
        assertThat(error.getDetails()).containsEntry("field", "qty");
        assertThat(error.isConflict()).isFalse();
    }
}
