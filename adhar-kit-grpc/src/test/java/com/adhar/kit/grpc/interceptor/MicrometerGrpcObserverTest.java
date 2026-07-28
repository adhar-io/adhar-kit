package com.adhar.kit.grpc.interceptor;

import io.grpc.Status;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MicrometerGrpcObserver}, verifying it records real
 * Micrometer observations with the expected name, contextual name and tags,
 * and reports errors for failed calls.
 *
 * <p>micrometer-observation is an optional compile dependency of this module,
 * so it is present on the module's own test classpath.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class MicrometerGrpcObserverTest {

    private RecordingHandler handler;
    private ObservationRegistry registry;
    private MicrometerGrpcObserver observer;

    @BeforeEach
    void setUp() {
        handler = new RecordingHandler();
        registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);
        observer = new MicrometerGrpcObserver(registry);
    }

    @Test
    void serverSpan_recordsObservation_withNameAndTags() {
        TraceContext ctx = TraceContext.newRoot();

        GrpcObserver.Span span = observer.start("pkg.Svc/Get", GrpcObserver.Kind.SERVER, ctx);
        span.finish(Status.OK);

        assertThat(handler.started.get()).isEqualTo(1);
        assertThat(handler.stopped.get()).isEqualTo(1);
        assertThat(handler.errored.get()).isZero();
        assertThat(handler.lastName).isEqualTo("grpc.server");
        assertThat(handler.lastContextualName).isEqualTo("pkg.Svc/Get");
        assertThat(handler.lastLowCardinalityTags)
                .containsEntry("rpc.system", "grpc")
                .containsEntry("rpc.kind", "server")
                .containsEntry("rpc.status", "OK");
    }

    @Test
    void clientSpan_usesClientName_andKind() {
        observer.start("pkg.Svc/Get", GrpcObserver.Kind.CLIENT, TraceContext.newRoot())
                .finish(Status.OK);

        assertThat(handler.lastName).isEqualTo("grpc.client");
        assertThat(handler.lastLowCardinalityTags).containsEntry("rpc.kind", "client");
    }

    @Test
    void failedCall_withCause_recordsErrorAndStatus() {
        RuntimeException cause = new RuntimeException("kaboom");

        observer.start("pkg.Svc/Get", GrpcObserver.Kind.SERVER, TraceContext.newRoot())
                .finish(Status.INTERNAL.withCause(cause));

        assertThat(handler.errored.get()).isEqualTo(1);
        assertThat(handler.lastError).isSameAs(cause);
        assertThat(handler.lastLowCardinalityTags).containsEntry("rpc.status", "INTERNAL");
        assertThat(handler.stopped.get()).isEqualTo(1);
    }

    @Test
    void failedCall_withoutCause_recordsStatusButNoError() {
        observer.start("pkg.Svc/Get", GrpcObserver.Kind.SERVER, TraceContext.newRoot())
                .finish(Status.RESOURCE_EXHAUSTED);

        assertThat(handler.errored.get()).isZero();
        assertThat(handler.lastLowCardinalityTags).containsEntry("rpc.status", "RESOURCE_EXHAUSTED");
    }

    /**
     * Captures observation lifecycle callbacks so the test can assert on the
     * recorded name/tags without depending on micrometer-observation-test.
     */
    private static final class RecordingHandler implements ObservationHandler<Observation.Context> {
        private final AtomicInteger started = new AtomicInteger();
        private final AtomicInteger stopped = new AtomicInteger();
        private final AtomicInteger errored = new AtomicInteger();
        private String lastName;
        private String lastContextualName;
        private Throwable lastError;
        private final Map<String, String> lastLowCardinalityTags = new HashMap<>();

        @Override
        public void onStart(Observation.Context context) {
            started.incrementAndGet();
        }

        @Override
        public void onError(Observation.Context context) {
            errored.incrementAndGet();
            lastError = context.getError();
        }

        @Override
        public void onStop(Observation.Context context) {
            stopped.incrementAndGet();
            lastName = context.getName();
            lastContextualName = context.getContextualName();
            lastLowCardinalityTags.clear();
            for (KeyValue kv : context.getLowCardinalityKeyValues()) {
                lastLowCardinalityTags.put(kv.getKey(), kv.getValue());
            }
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
