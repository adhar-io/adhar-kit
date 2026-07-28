package com.adhar.kit.grpc.interceptor;

import io.grpc.Status;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * {@link GrpcObserver} that records each gRPC call as a Micrometer
 * {@link Observation}, so applications with a configured tracing bridge (e.g.
 * OpenTelemetry or Brave via Micrometer Tracing) emit real spans for gRPC
 * calls.
 *
 * <p>This class references {@code io.micrometer.observation} types directly and
 * is therefore only instantiated when micrometer-observation is on the
 * classpath - the gRPC auto-configuration guards its creation with
 * {@code @ConditionalOnClass(ObservationRegistry.class)}. The rest of the module
 * depends only on the tracing-library-free {@link GrpcObserver} abstraction, so
 * it compiles and runs without micrometer-observation present.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class MicrometerGrpcObserver implements GrpcObserver {

    private static final String SERVER_OBSERVATION_NAME = "grpc.server";
    private static final String CLIENT_OBSERVATION_NAME = "grpc.client";

    private final ObservationRegistry registry;

    /**
     * Creates the observer.
     *
     * @param registry the Micrometer observation registry to record into
     */
    public MicrometerGrpcObserver(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Span start(String methodName, Kind kind, TraceContext context) {
        String name = kind == Kind.SERVER ? SERVER_OBSERVATION_NAME : CLIENT_OBSERVATION_NAME;
        Observation observation = Observation.createNotStarted(name, registry)
                .contextualName(methodName)
                .lowCardinalityKeyValue("rpc.system", "grpc")
                .lowCardinalityKeyValue("rpc.kind", kind.name().toLowerCase())
                .highCardinalityKeyValue("rpc.method", methodName)
                .highCardinalityKeyValue("trace.id", context.getTraceId())
                .start();
        return status -> {
            observation.lowCardinalityKeyValue("rpc.status", status.getCode().name());
            if (!status.isOk()) {
                Throwable cause = status.getCause();
                if (cause != null) {
                    observation.error(cause);
                }
            }
            observation.stop();
        };
    }
}
