package com.adhar.adharkit.metrics.aspect;

import com.adhar.adharkit.metrics.annotation.*;
import com.adhar.adharkit.metrics.util.AdharMetrics;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced aspect for automatically recording metrics for annotated methods.
 * <p>
 * This aspect provides automated instrumentation for methods, recording various metrics
 * based on annotations like @Timed, @Counted, @Gauged, @Summary, @Histogram,
 * @MonitorPerformance, @CacheMetrics, @DatabaseMetrics, and @ApiMetrics.
 * </p>
 */
@Aspect
@Slf4j
public class EnhancedMetricsAspect {

    private final MeterRegistry registry;

    // Cache for metrics to prevent creating multiple instances
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final Map<String, DistributionSummary> summaryCache = new ConcurrentHashMap<>();
    private final Map<String, Gauge> gaugeCache = new ConcurrentHashMap<>();

    // Performance monitoring cache
    private final Map<String, PerformanceMetrics> performanceCache = new ConcurrentHashMap<>();

    public EnhancedMetricsAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    // ==================== Existing Annotation Handlers ====================

    @Around("@annotation(timed)")
    public Object timeMethod(ProceedingJoinPoint joinPoint, Timed timed) throws Throwable {
        String metricName = getMetricName(joinPoint, timed.name());
        Timer timer = getOrCreateTimer(metricName, timed);

        Timer.Sample sample = Timer.start(registry);
        boolean success = false;
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } finally {
            if (!timed.successOnly() || success) {
                sample.stop(timer);
            }
        }
    }

    @Around("@annotation(counted)")
    public Object countMethod(ProceedingJoinPoint joinPoint, Counted counted) throws Throwable {
        String metricName = getMetricName(joinPoint, counted.name());
        Counter counter = getOrCreateCounter(metricName, counted);
        Counter failureCounter = null;

        if (counted.recordFailures()) {
            failureCounter = getOrCreateCounter(metricName + ".failures", counted);
        }

        boolean success = false;
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } catch (Exception e) {
            if (failureCounter != null) {
                failureCounter.increment();
            }
            throw e;
        } finally {
            if (!counted.successOnly() || success) {
                counter.increment();
            }
        }
    }

    @AfterReturning(pointcut = "@annotation(summary)", returning = "result")
    public void recordSummary(JoinPoint joinPoint, Object result, Summary summary) {
        if (!summary.successOnly() || result != null) {
            String metricName = getMetricName(joinPoint, summary.name());
            DistributionSummary distributionSummary = getOrCreateSummary(metricName, summary);

            double value = extractValueForSummary(result, summary.valueField());
            if (!Double.isNaN(value)) {
                distributionSummary.record(value);
            }
        }
    }

    @AfterReturning(pointcut = "@annotation(gauged)", returning = "result")
    public void recordGauge(JoinPoint joinPoint, Object result, Gauged gauged) {
        if (gauged.updateOnCall() && result != null) {
            String metricName = getMetricName(joinPoint, gauged.name());

            String cacheKey = metricName + ":" + Tags.of(gauged.tags()).toString();

            if (!gaugeCache.containsKey(cacheKey)) {
                GaugeValue gaugeValue = new GaugeValue();

                Gauge gauge = Gauge.builder(metricName, gaugeValue, GaugeValue::getValue)
                        .description(StringUtils.hasText(gauged.description()) ? gauged.description() : null)
                        .tags(gauged.tags())
                        .register(registry);

                gaugeCache.put(cacheKey, gauge);
            }

            double value = extractValueForGauge(result);
            if (!Double.isNaN(value)) {
                log.debug("Updated gauge {} with value {}", metricName, value);
            }
        }
    }

    // ==================== New Annotation Handlers ====================

    @Around("@annotation(histogram)")
    public Object recordHistogram(ProceedingJoinPoint joinPoint, Histogram histogram) throws Throwable {
        String metricName = getMetricName(joinPoint, histogram.name());

        if (histogram.recordTiming()) {
            // Record execution time as histogram
            Timer timer = getOrCreateHistogramTimer(metricName, histogram);
            Timer.Sample sample = Timer.start(registry);

            boolean success = false;
            try {
                Object result = joinPoint.proceed();
                success = true;
                return result;
            } finally {
                if (!histogram.successOnly() || success) {
                    sample.stop(timer);
                }
            }
        } else {
            // Execute method and record return value
            Object result = joinPoint.proceed();
            if (!histogram.successOnly() || result != null) {
                double value = extractValueForSummary(result, histogram.valueField());
                if (!Double.isNaN(value)) {
                    DistributionSummary summary = getOrCreateHistogramSummary(metricName, histogram);
                    summary.record(value);
                }
            }
            return result;
        }
    }

    @Around("@annotation(performance)")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint, MonitorPerformance performance) throws Throwable {
        String baseName = getMetricName(joinPoint, performance.name());
        PerformanceMetrics metrics = performanceCache.computeIfAbsent(baseName,
            k -> new PerformanceMetrics(baseName, performance, registry));

        return metrics.execute(() -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e);
            }
        });
    }

    @Around("@annotation(cacheMetrics)")
    public Object monitorCache(ProceedingJoinPoint joinPoint, CacheMetrics cacheMetrics) throws Throwable {
        String cacheName = StringUtils.hasText(cacheMetrics.cacheName()) ?
            cacheMetrics.cacheName() : getMetricName(joinPoint, cacheMetrics.name());

        Timer.Sample sample = null;
        if (cacheMetrics.recordLoadTime()) {
            sample = Timer.start(registry);
        }

        boolean hit = false;
        try {
            Object result = joinPoint.proceed();

            // Determine if it's a cache hit or miss
            if (cacheMetrics.nullIsMiss() && result == null) {
                hit = false;
            } else if (StringUtils.hasText(cacheMetrics.hitIndicator())) {
                hit = cacheMetrics.hitIndicator().equals(String.valueOf(result));
            } else {
                hit = result != null; // Default: non-null means hit
            }

            recordCacheMetrics(cacheName, hit, cacheMetrics);
            return result;

        } finally {
            if (sample != null) {
                sample.stop(Timer.builder("cache.load.time")
                    .tag("cache", cacheName)
                    .register(registry));
            }
        }
    }

    @Around("@annotation(dbMetrics)")
    public Object monitorDatabase(ProceedingJoinPoint joinPoint, DatabaseMetrics dbMetrics) throws Throwable {
        String operation = StringUtils.hasText(dbMetrics.operation()) ? dbMetrics.operation() : "UNKNOWN";
        String table = StringUtils.hasText(dbMetrics.table()) ? dbMetrics.table() : "unknown";
        String dataSource = StringUtils.hasText(dbMetrics.dataSource()) ? dbMetrics.dataSource() : "default";

        String[] baseTags = {"operation", operation, "table", table, "dataSource", dataSource};
        String[] allTags = combineTags(baseTags, dbMetrics.tags());

        Timer.Sample sample = null;
        if (dbMetrics.recordExecutionTime()) {
            sample = Timer.start(registry);
        }

        boolean success = false;
        try {
            Object result = joinPoint.proceed();
            success = true;

            if (dbMetrics.recordRowCount()) {
                recordRowCount(result, allTags);
            }

            return result;

        } catch (Exception e) {
            if (dbMetrics.recordErrors()) {
                AdharMetrics.increment("database.errors", allTags);
            }
            throw e;
        } finally {
            if (sample != null) {
                Timer timer = Timer.builder("database.query.time").tags(allTags).register(registry);
                long durationNanos = sample.stop(timer);
                long durationMs = durationNanos / 1_000_000;

                if (dbMetrics.alertOnSlowQuery() && durationMs > dbMetrics.slowQueryThresholdMs()) {
                    AdharMetrics.increment("database.slow_queries", allTags);
                }
            }
        }
    }

    @Around("@annotation(apiMetrics)")
    public Object monitorApi(ProceedingJoinPoint joinPoint, ApiMetrics apiMetrics) throws Throwable {
        String endpoint = StringUtils.hasText(apiMetrics.endpoint()) ?
            apiMetrics.endpoint() : getMetricName(joinPoint, "");
        String method = StringUtils.hasText(apiMetrics.method()) ? apiMetrics.method() : "UNKNOWN";
        String version = StringUtils.hasText(apiMetrics.version()) ? apiMetrics.version() : "v1";

        String[] baseTags = {"endpoint", endpoint, "method", method, "version", version};
        String[] allTags = combineTags(baseTags, apiMetrics.tags());

        Timer.Sample sample = Timer.start(registry);
        String statusCode = "200"; // Default success

        try {
            Object result = joinPoint.proceed();

            if (apiMetrics.recordPayloadSize() && result != null) {
                recordPayloadSize(result, "response", allTags);
            }

            return result;

        } catch (Exception e) {
            statusCode = "500"; // Default error
            throw e;
        } finally {
            sample.stop(Timer.builder("api.request.time").tags(allTags).register(registry));

            if (apiMetrics.recordStatusCodes()) {
                AdharMetrics.increment("api.requests",
                    combineTags(allTags, new String[]{"status", statusCode}));
            }
        }
    }

    // ==================== Helper Methods ====================

    private Timer getOrCreateHistogramTimer(String metricName, Histogram histogram) {
        String cacheKey = metricName + ":histogram:" + Tags.of(histogram.tags()).toString();
        return timerCache.computeIfAbsent(cacheKey, key -> {
            Timer.Builder builder = Timer.builder(metricName)
                    .tags(histogram.tags());

            if (StringUtils.hasText(histogram.description())) {
                builder.description(histogram.description());
            }



            return builder.register(registry);
        });
    }

    private DistributionSummary getOrCreateHistogramSummary(String metricName, Histogram histogram) {
        String cacheKey = metricName + ":summary:" + Tags.of(histogram.tags()).toString();
        return summaryCache.computeIfAbsent(cacheKey, key -> {
            DistributionSummary.Builder builder = DistributionSummary.builder(metricName)
                    .tags(histogram.tags());

            if (StringUtils.hasText(histogram.description())) {
                builder.description(histogram.description());
            }



            return builder.register(registry);
        });
    }

    private void recordCacheMetrics(String cacheName, boolean hit, CacheMetrics cacheMetrics) {
        String[] tags = combineTags(new String[]{"cache", cacheName}, cacheMetrics.tags());

        if (cacheMetrics.recordHitRate()) {
            AdharMetrics.increment("cache." + (hit ? "hits" : "misses"), tags);
            AdharMetrics.increment("cache.requests", tags);
        }
    }

    private void recordRowCount(Object result, String[] tags) {
        double count = 0;

        if (result instanceof Collection) {
            count = ((Collection<?>) result).size();
        } else if (result instanceof Number) {
            count = ((Number) result).doubleValue();
        } else if (result != null && result.getClass().isArray()) {
            count = ((Object[]) result).length;
        }

        if (count > 0) {
            AdharMetrics.recordSummary("database.rows", count, tags);
        }
    }

    private void recordPayloadSize(Object payload, String type, String[] baseTags) {
        double size = 0;

        if (payload instanceof String) {
            size = ((String) payload).length();
        } else if (payload instanceof Collection) {
            size = ((Collection<?>) payload).size();
        } else if (payload != null && payload.getClass().isArray()) {
            size = ((Object[]) payload).length;
        }

        if (size > 0) {
            String[] tags = combineTags(baseTags, new String[]{"type", type});
            AdharMetrics.recordSummary("api.payload.size", size, tags);
        }
    }

    private String[] combineTags(String[] baseTags, String[] additionalTags) {
        String[] combined = new String[baseTags.length + additionalTags.length];
        System.arraycopy(baseTags, 0, combined, 0, baseTags.length);
        System.arraycopy(additionalTags, 0, combined, baseTags.length, additionalTags.length);
        return combined;
    }

    private Timer getOrCreateTimer(String metricName, Timed timed) {
        String cacheKey = metricName + ":" + Tags.of(timed.tags()).toString();
        return timerCache.computeIfAbsent(cacheKey, key -> {
            Timer.Builder builder = Timer.builder(metricName)
                    .tags(timed.tags());

            if (StringUtils.hasText(timed.description())) {
                builder.description(timed.description());
            }

            if (timed.percentiles().length > 0) {
                builder.publishPercentiles(timed.percentiles());
            }

            return builder.register(registry);
        });
    }

    private Counter getOrCreateCounter(String metricName, Counted counted) {
        String cacheKey = metricName + ":" + Tags.of(counted.tags()).toString();
        return counterCache.computeIfAbsent(cacheKey, key -> {
            Counter.Builder builder = Counter.builder(metricName)
                    .tags(counted.tags());

            if (StringUtils.hasText(counted.description())) {
                builder.description(counted.description());
            }

            return builder.register(registry);
        });
    }

    private DistributionSummary getOrCreateSummary(String metricName, Summary summary) {
        String cacheKey = metricName + ":" + Tags.of(summary.tags()).toString();
        return summaryCache.computeIfAbsent(cacheKey, key -> {
            DistributionSummary.Builder builder = DistributionSummary.builder(metricName)
                    .tags(summary.tags());

            if (StringUtils.hasText(summary.description())) {
                builder.description(summary.description());
            }


            if (summary.percentiles().length > 0) {
                builder.publishPercentiles(summary.percentiles());
            }

            return builder.register(registry);
        });
    }

    private String getMetricName(JoinPoint joinPoint, String annotationName) {
        if (StringUtils.hasText(annotationName)) {
            return annotationName;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = method.getDeclaringClass().getSimpleName().toLowerCase();
        String methodName = method.getName();

        return className + "." + methodName;
    }

    private double extractValueForSummary(Object result, String valueField) {
        if (result == null) {
            return Double.NaN;
        }

        try {
            if (StringUtils.hasText(valueField)) {
                return extractFieldValue(result, valueField);
            }

            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            } else if (result instanceof Collection) {
                return ((Collection<?>) result).size();
            } else if (result.getClass().isArray()) {
                return ((Object[]) result).length;
            } else if (result instanceof String) {
                return ((String) result).length();
            }

            return Double.NaN;
        } catch (Exception e) {
            log.warn("Failed to extract value for summary from result: {}", e.getMessage());
            return Double.NaN;
        }
    }

    private double extractValueForGauge(Object result) {
        if (result == null) {
            return Double.NaN;
        }

        try {
            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            } else if (result instanceof Collection) {
                return ((Collection<?>) result).size();
            } else if (result.getClass().isArray()) {
                return ((Object[]) result).length;
            }

            return Double.NaN;
        } catch (Exception e) {
            log.warn("Failed to extract value for gauge from result: {}", e.getMessage());
            return Double.NaN;
        }
    }

    private double extractFieldValue(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);

            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }

            return Double.NaN;
        } catch (Exception e) {
            log.warn("Failed to extract field {} from object: {}", fieldName, e.getMessage());
            return Double.NaN;
        }
    }

    private static class GaugeValue {
        private volatile double value = 0.0;

        public double getValue() {
            return value;
        }
        
        public void setValue(double value) {
            this.value = value;
        }
    }

    private static class PerformanceMetrics {
        private final MeterRegistry registry;
        private final Timer timer;
        private final Counter totalCounter;
        private final Counter errorCounter;

        public PerformanceMetrics(String baseName, MonitorPerformance config, MeterRegistry registry) {
            this.registry = registry;
            Timer.Builder timerBuilder = Timer.builder(baseName + ".duration");
            if (config.recordPercentiles()) {
                timerBuilder.publishPercentiles(config.percentiles());
            }
            this.timer = timerBuilder.register(registry);

            this.totalCounter = config.recordThroughput() ?
                Counter.builder(baseName + ".total").register(registry) : null;

            this.errorCounter = config.recordErrorRate() ?
                Counter.builder(baseName + ".errors").register(registry) : null;
        }

        public <T> T execute(java.util.function.Supplier<T> operation) {
            Timer.Sample sample = Timer.start(registry);
            boolean success = false;

            try {
                T result = operation.get();
                success = true;
                return result;
            } catch (RuntimeException e) {
                if (errorCounter != null) {
                    errorCounter.increment();
                }
                throw e;
            } finally {
                sample.stop(timer);
                if (totalCounter != null) {
                    totalCounter.increment();
                }
            }
        }
    }
}

