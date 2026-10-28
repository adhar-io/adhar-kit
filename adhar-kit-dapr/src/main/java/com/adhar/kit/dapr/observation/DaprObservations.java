package com.adhar.kit.dapr.observation;

import com.adhar.kit.dapr.DaprError;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Wraps every Dapr call in a Micrometer {@link Observation} named {@value #NAME}, tagged with the
 * building block, the operation, the component (store, pub/sub, binding or target app id) and
 * the outcome. With a meter registry that is the {@code adhar_dapr_seconds} timer the platform's
 * Dapr dashboard reads per app; with a tracer it is a client span around the sidecar call.
 *
 * <p>All tags are low-cardinality: component names and app ids are a handful per service, keys and
 * topics are never tags.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class DaprObservations {

    /** Observation (and meter) name. */
    public static final String NAME = "adhar.dapr";

    public static final String API = "dapr.api";
    public static final String OPERATION = "dapr.operation";
    public static final String COMPONENT = "dapr.component";
    public static final String OUTCOME = "outcome";

    /** Observations that record nothing. */
    public static final DaprObservations NOOP = new DaprObservations(ObservationRegistry.NOOP);

    private final ObservationRegistry registry;

    public DaprObservations(ObservationRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * Runs a call inside an observation.
     *
     * @param api       the building block ({@code state}, {@code pubsub}, {@code invoke}, ...)
     * @param operation the operation ({@code get}, {@code save}, {@code publish}, ...)
     * @param component the component name or target app id
     * @param call      the call
     * @param <T>       the result type
     * @return the call's result
     */
    public <T> T observe(String api, String operation, String component, Supplier<T> call) {
        if (registry.isNoop()) {
            return call.get();
        }
        Observation observation = Observation.createNotStarted(NAME, registry)
                .contextualName("dapr " + api + " " + operation)
                .lowCardinalityKeyValue(API, api)
                .lowCardinalityKeyValue(OPERATION, operation)
                .lowCardinalityKeyValue(COMPONENT, component == null || component.isBlank() ? "none" : component)
                .start();
        try (Observation.Scope ignored = observation.openScope()) {
            T result = call.get();
            observation.lowCardinalityKeyValue(OUTCOME, "SUCCESS");
            return result;
        } catch (RuntimeException | Error e) {
            observation.lowCardinalityKeyValue(OUTCOME, outcomeOf(e));
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    /**
     * Classifies a failure the way the dashboard groups it.
     *
     * @param failure the failure
     * @return {@code CONFLICT}, {@code NOT_FOUND}, {@code CLIENT_ERROR}, {@code UNAVAILABLE} or
     *         {@code SERVER_ERROR}
     */
    static String outcomeOf(Throwable failure) {
        if (failure instanceof DaprError error) {
            if (error.isConflict()) {
                return "CONFLICT";
            }
            if (error.isNotFound()) {
                return "NOT_FOUND";
            }
            if (error.isUnavailable()) {
                return "UNAVAILABLE";
            }
            if (error.getStatus() >= 400 && error.getStatus() < 500) {
                return "CLIENT_ERROR";
            }
        }
        return "SERVER_ERROR";
    }
}
