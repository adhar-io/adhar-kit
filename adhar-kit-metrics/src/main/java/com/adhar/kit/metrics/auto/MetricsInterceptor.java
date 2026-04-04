package com.adhar.kit.metrics.auto;

import com.adhar.kit.metrics.annotation.MonitorPerformance;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * AOP aspect that automatically instruments bean methods annotated with
 * {@link Measured} or {@link MonitorPerformance}.
 * <p>
 * For every intercepted method invocation this aspect records:
 * <ul>
 *   <li>{@code adhar.operation.duration} -- a Timer capturing latency</li>
 *   <li>{@code adhar.operation.count} -- a Counter for total invocations</li>
 *   <li>{@code adhar.operation.errors} -- a Counter incremented on exceptions</li>
 * </ul>
 *
 * <p>All metrics are tagged with: module, operation, class, method, and success.</p>
 *
 * <p><b>Annotation Resolution Order:</b></p>
 * <ol>
 *   <li>Method-level {@link Measured} takes priority</li>
 *   <li>Type-level {@link Measured} is used as fallback</li>
 *   <li>{@link MonitorPerformance} is handled by a separate pointcut</li>
 * </ol>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see Measured
 * @see MonitorPerformance
 */
@Aspect
public class MetricsInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MetricsInterceptor.class);

    private static final String METRIC_DURATION = "adhar.operation.duration";
    private static final String METRIC_COUNT = "adhar.operation.count";
    private static final String METRIC_ERRORS = "adhar.operation.errors";

    private final MeterRegistry registry;

    /**
     * Constructs a MetricsInterceptor backed by the given MeterRegistry.
     *
     * @param registry the Micrometer MeterRegistry
     */
    public MetricsInterceptor(MeterRegistry registry) {
        this.registry = registry;
        log.info("MetricsInterceptor initialized -- auto-instrumenting @Measured and @Profiled methods");
    }

    // -------------------------------------------------------------------------
    // Pointcuts
    // -------------------------------------------------------------------------

    @Pointcut("@annotation(com.adhar.kit.metrics.auto.Measured)")
    public void measuredMethod() {
        // pointcut for method-level @Measured
    }

    @Pointcut("@within(com.adhar.kit.metrics.auto.Measured)")
    public void measuredType() {
        // pointcut for type-level @Measured
    }

    @Pointcut("@annotation(com.adhar.kit.metrics.annotation.MonitorPerformance)")
    public void profiledMethod() {
        // pointcut for method-level @MonitorPerformance
    }

    @Pointcut("@within(com.adhar.kit.metrics.annotation.MonitorPerformance)")
    public void profiledType() {
        // pointcut for type-level @MonitorPerformance
    }

    // -------------------------------------------------------------------------
    // Advice -- @Measured
    // -------------------------------------------------------------------------

    /**
     * Around advice for methods or types annotated with {@link Measured}.
     */
    @Around("measuredMethod() || measuredType()")
    public Object aroundMeasured(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        Measured measured = method.getAnnotation(Measured.class);
        if (measured == null) {
            measured = joinPoint.getTarget().getClass().getAnnotation(Measured.class);
        }

        String module = measured != null ? measured.module() : "general";
        String operation = resolveMeasuredOperation(measured, method);
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        String[] extraTags = measured != null ? measured.tags() : new String[0];

        return executeAndRecord(joinPoint, module, operation, className, methodName, extraTags);
    }

    // -------------------------------------------------------------------------
    // Advice -- @MonitorPerformance (Profiled)
    // -------------------------------------------------------------------------

    /**
     * Around advice for methods or types annotated with {@link MonitorPerformance}.
     */
    @Around("profiledMethod() || profiledType()")
    public Object aroundProfiled(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        MonitorPerformance profiled = method.getAnnotation(MonitorPerformance.class);
        if (profiled == null) {
            profiled = joinPoint.getTarget().getClass().getAnnotation(MonitorPerformance.class);
        }

        String module = "profiled";
        String operation = (profiled != null && !profiled.name().isEmpty())
                ? profiled.name()
                : joinPoint.getTarget().getClass().getSimpleName() + "." + method.getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        String[] extraTags = profiled != null ? profiled.tags() : new String[0];

        return executeAndRecord(joinPoint, module, operation, className, methodName, extraTags);
    }

    // -------------------------------------------------------------------------
    // Core Recording Logic
    // -------------------------------------------------------------------------

    private Object executeAndRecord(ProceedingJoinPoint joinPoint,
                                    String module,
                                    String operation,
                                    String className,
                                    String methodName,
                                    String[] extraTags) throws Throwable {
        long startNanos = System.nanoTime();
        boolean success = true;
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            success = false;
            recordError(module, operation, className, methodName, ex, extraTags);
            throw ex;
        } finally {
            long durationNanos = System.nanoTime() - startNanos;
            recordDuration(module, operation, className, methodName, durationNanos, success, extraTags);
            recordCount(module, operation, className, methodName, success, extraTags);
        }
    }

    private void recordDuration(String module, String operation, String className,
                                String methodName, long durationNanos, boolean success,
                                String[] extraTags) {
        Timer.Builder builder = Timer.builder(METRIC_DURATION)
                .description("Operation execution duration")
                .tag("module", module)
                .tag("operation", operation)
                .tag("class", className)
                .tag("method", methodName)
                .tag("success", String.valueOf(success));
        applyExtraTags(builder, extraTags);
        builder.register(registry).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private void recordCount(String module, String operation, String className,
                             String methodName, boolean success, String[] extraTags) {
        Counter.Builder builder = Counter.builder(METRIC_COUNT)
                .description("Operation invocation count")
                .tag("module", module)
                .tag("operation", operation)
                .tag("class", className)
                .tag("method", methodName)
                .tag("success", String.valueOf(success));
        applyExtraTags(builder, extraTags);
        builder.register(registry).increment();
    }

    private void recordError(String module, String operation, String className,
                             String methodName, Throwable ex, String[] extraTags) {
        Counter.Builder builder = Counter.builder(METRIC_ERRORS)
                .description("Operation error count")
                .tag("module", module)
                .tag("operation", operation)
                .tag("class", className)
                .tag("method", methodName)
                .tag("exception", ex.getClass().getSimpleName());
        applyExtraTags(builder, extraTags);
        builder.register(registry).increment();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolveMeasuredOperation(Measured measured, Method method) {
        if (measured != null && !measured.value().isEmpty()) {
            return measured.value();
        }
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private void applyExtraTags(Timer.Builder builder, String[] tags) {
        for (int i = 0; i + 1 < tags.length; i += 2) {
            builder.tag(tags[i], tags[i + 1]);
        }
    }

    private void applyExtraTags(Counter.Builder builder, String[] tags) {
        for (int i = 0; i + 1 < tags.length; i += 2) {
            builder.tag(tags[i], tags[i + 1]);
        }
    }
}
