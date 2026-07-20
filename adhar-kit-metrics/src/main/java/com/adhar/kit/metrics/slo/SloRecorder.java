package com.adhar.kit.metrics.slo;

import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Records request outcomes against configured SLO (Service Level Objective) targets
 * and exposes error-budget gauges computed over a rolling window.
 * <p>
 * Targets are configured via {@code adhar.metrics.slo.targets.<name>=<availability>}
 * (e.g. {@code adhar.metrics.slo.targets.checkout=0.999}). The special target name
 * {@link #GLOBAL_TARGET} matches every request routed through
 * {@link #recordHttp(String, boolean)}; any other target matches when the request
 * uri equals the target name or contains it as a path segment
 * (target {@code checkout} matches uri {@code /api/checkout}).
 * </p>
 *
 * <p>For every configured target two gauges are published:</p>
 * <ul>
 *   <li>{@code adhar.slo.error_budget.remaining} - fraction of the error budget still
 *       available in the rolling window (1.0 = untouched, 0.0 = exhausted)</li>
 *   <li>{@code adhar.slo.burn_rate} - observed error rate divided by the allowed error
 *       rate ({@code 1 - objective}); values above 1.0 mean the budget is being burned
 *       faster than the SLO allows</li>
 * </ul>
 */
@Slf4j
public class SloRecorder {

    /**
     * Gauge name for the remaining error budget fraction per target.
     */
    public static final String ERROR_BUDGET_METRIC = "adhar.slo.error_budget.remaining";

    /**
     * Gauge name for the burn rate per target.
     */
    public static final String BURN_RATE_METRIC = "adhar.slo.burn_rate";

    /**
     * Target name that matches every recorded request.
     */
    public static final String GLOBAL_TARGET = "global";

    private static final int BUCKET_COUNT = 60;
    private static final double MIN_ALLOWED_ERROR_RATE = 1e-9;

    private final Map<String, Double> objectives;
    private final Map<String, RollingWindow> windows = new ConcurrentHashMap<>();
    private final long windowMillis;
    private final LongSupplier timeSource;

    /**
     * Creates a recorder from the configured SLO properties using the system clock.
     *
     * @param registry the meter registry to publish gauges to
     * @param properties the SLO configuration (targets and rolling window length)
     */
    public SloRecorder(MeterRegistry registry, AdharMetricsProperties.SloProperties properties) {
        this(registry, properties, System::currentTimeMillis);
    }

    /**
     * Creates a recorder with an explicit time source (useful for tests).
     *
     * @param registry the meter registry to publish gauges to
     * @param properties the SLO configuration (targets and rolling window length)
     * @param timeSource supplier of the current epoch time in milliseconds
     */
    public SloRecorder(MeterRegistry registry, AdharMetricsProperties.SloProperties properties,
                       LongSupplier timeSource) {
        this.objectives = Map.copyOf(properties.getTargets());
        this.windowMillis = Math.max(1000L, properties.getWindowSeconds() * 1000L);
        this.timeSource = timeSource;

        for (Map.Entry<String, Double> entry : objectives.entrySet()) {
            String target = entry.getKey();
            windows.put(target, new RollingWindow(windowMillis, BUCKET_COUNT));

            Gauge.builder(ERROR_BUDGET_METRIC, this, recorder -> recorder.errorBudgetRemaining(target))
                    .description("Fraction of the SLO error budget remaining in the rolling window")
                    .tag("target", target)
                    .tag("objective", String.valueOf(entry.getValue()))
                    .register(registry);

            Gauge.builder(BURN_RATE_METRIC, this, recorder -> recorder.burnRate(target))
                    .description("Observed error rate divided by the allowed error rate")
                    .tag("target", target)
                    .tag("objective", String.valueOf(entry.getValue()))
                    .register(registry);
        }

        log.debug("SloRecorder initialized with {} target(s) over a {}ms rolling window",
                objectives.size(), windowMillis);
    }

    /**
     * Records an outcome for a specific configured target. Unknown targets are ignored.
     *
     * @param target the SLO target name
     * @param success whether the request/operation met the objective
     */
    public void record(String target, boolean success) {
        RollingWindow window = windows.get(target);
        if (window != null) {
            window.record(timeSource.getAsLong(), success);
        }
    }

    /**
     * Records an HTTP request outcome against every matching target.
     * The {@link #GLOBAL_TARGET} target always matches; other targets match when
     * the uri equals the target name or contains it as a path segment.
     *
     * @param uri the (templated) request uri
     * @param success whether the request succeeded (typically status &lt; 500)
     */
    public void recordHttp(String uri, boolean success) {
        for (String target : objectives.keySet()) {
            if (matches(target, uri)) {
                record(target, success);
            }
        }
    }

    /**
     * Computes the remaining error budget fraction for a target over the rolling window.
     *
     * @param target the SLO target name
     * @return remaining budget in [0.0, 1.0]; 1.0 when no traffic was observed
     */
    public double errorBudgetRemaining(String target) {
        Counts counts = snapshot(target);
        if (counts.total == 0) {
            return 1.0;
        }
        double allowedErrors = allowedErrorRate(target) * counts.total;
        return Math.max(0.0, 1.0 - counts.errors / allowedErrors);
    }

    /**
     * Computes the burn rate for a target over the rolling window.
     *
     * @param target the SLO target name
     * @return observed error rate divided by the allowed error rate (0.0 without traffic)
     */
    public double burnRate(String target) {
        Counts counts = snapshot(target);
        if (counts.total == 0) {
            return 0.0;
        }
        double observedErrorRate = counts.errors / (double) counts.total;
        return observedErrorRate / allowedErrorRate(target);
    }

    /**
     * Returns the configured objectives keyed by target name.
     *
     * @return an immutable map of target name to availability objective
     */
    public Map<String, Double> getObjectives() {
        return objectives;
    }

    private boolean matches(String target, String uri) {
        if (GLOBAL_TARGET.equals(target)) {
            return true;
        }
        if (uri == null) {
            return false;
        }
        return uri.equals(target)
                || uri.equals("/" + target)
                || uri.contains("/" + target + "/")
                || uri.endsWith("/" + target);
    }

    private double allowedErrorRate(String target) {
        Double objective = objectives.get(target);
        double allowed = objective == null ? 1.0 : 1.0 - objective;
        return Math.max(MIN_ALLOWED_ERROR_RATE, allowed);
    }

    private Counts snapshot(String target) {
        RollingWindow window = windows.get(target);
        if (window == null) {
            return new Counts(0, 0);
        }
        return window.snapshot(timeSource.getAsLong());
    }

    private record Counts(long total, long errors) {
    }

    /**
     * Fixed-size ring of time buckets covering the rolling window.
     */
    private static final class RollingWindow {

        private final long bucketMillis;
        private final int bucketCount;
        private final long[] bucketEpochs;
        private final long[] totals;
        private final long[] errors;

        RollingWindow(long windowMillis, int bucketCount) {
            this.bucketCount = bucketCount;
            this.bucketMillis = Math.max(1L, windowMillis / bucketCount);
            this.bucketEpochs = new long[bucketCount];
            this.totals = new long[bucketCount];
            this.errors = new long[bucketCount];
        }

        synchronized void record(long nowMillis, boolean success) {
            long epoch = nowMillis / bucketMillis;
            int index = (int) (epoch % bucketCount);
            if (bucketEpochs[index] != epoch) {
                bucketEpochs[index] = epoch;
                totals[index] = 0;
                errors[index] = 0;
            }
            totals[index]++;
            if (!success) {
                errors[index]++;
            }
        }

        synchronized Counts snapshot(long nowMillis) {
            long currentEpoch = nowMillis / bucketMillis;
            long oldestValidEpoch = currentEpoch - bucketCount + 1;
            long total = 0;
            long errorCount = 0;
            for (int i = 0; i < bucketCount; i++) {
                if (bucketEpochs[i] >= oldestValidEpoch && bucketEpochs[i] <= currentEpoch && totals[i] > 0) {
                    total += totals[i];
                    errorCount += errors[i];
                }
            }
            return new Counts(total, errorCount);
        }
    }
}
