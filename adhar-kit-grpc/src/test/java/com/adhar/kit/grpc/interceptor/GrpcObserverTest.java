package com.adhar.kit.grpc.interceptor;

import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for the tracing-library-free {@link GrpcObserver} abstraction and
 * its no-op instances.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class GrpcObserverTest {

    @Test
    void noopObserver_returnsNoopSpan_thatDoesNothing() {
        GrpcObserver.Span span = GrpcObserver.NOOP.start(
                "pkg.Svc/M", GrpcObserver.Kind.SERVER, TraceContext.newRoot());

        assertThat(span).isSameAs(GrpcObserver.NOOP_SPAN);
        assertThatCode(() -> span.finish(Status.OK)).doesNotThrowAnyException();
        assertThatCode(() -> span.finish(Status.INTERNAL)).doesNotThrowAnyException();
    }

    @Test
    void noopSpan_finish_isSafeForAnyStatus() {
        assertThatCode(() -> GrpcObserver.NOOP_SPAN.finish(Status.CANCELLED))
                .doesNotThrowAnyException();
    }

    @Test
    void kind_hasServerAndClient() {
        assertThat(GrpcObserver.Kind.values())
                .containsExactly(GrpcObserver.Kind.SERVER, GrpcObserver.Kind.CLIENT);
        assertThat(GrpcObserver.Kind.valueOf("CLIENT")).isEqualTo(GrpcObserver.Kind.CLIENT);
    }
}
