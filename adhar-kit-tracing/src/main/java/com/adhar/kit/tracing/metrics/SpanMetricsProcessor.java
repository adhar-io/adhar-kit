package com.adhar.kit.tracing.metrics;

import com.adhar.kit.tracing.properties.AdharTracingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * A {@link SpanProcessor} that derives RED (Rate / Errors / Duration) metrics from finished
 * spans and records them into a Micrometer {@link MeterRegistry}.
 * <p>
 * For every ended span it records a {@link Timer} named {@code <meterPrefix>.duration} whose
 * count captures the request <em>rate</em> and whose distribution captures the
 * <em>duration</em>. When {@link AdharTracingProperties.MetricsBridgeProperties#isRecordErrors()}
 * is enabled and the span ended with an {@link StatusCode#ERROR error} status, a
 * {@code <meterPrefix>.errors} {@link Counter} is also incremented. Each meter is tagged with
 * the span name ({@code span.name}) and, when
 * {@link AdharTracingProperties.MetricsBridgeProperties#isIncludeSpanKind()} is enabled, the
 * span kind ({@code span.kind}); the duration timer additionally carries an {@code error} tag.
 * </p>
 * <p>
 * <strong>SDK-hook note:</strong> the OpenTelemetry SDK's {@link SpanProcessor#onEnd} callback
 * is the natural, first-class hook for span-derived metrics, so this class implements it
 * directly and is registered alongside the exporting processors.
 * </p>
 */
@Slf4j
public class SpanMetricsProcessor implements SpanProcessor {

    private final MeterRegistry meterRegistry;
    private final String durationMeter;
    private final String errorMeter;
    private final boolean includeSpanKind;
    private final boolean recordErrors;

    public SpanMetricsProcessor(MeterRegistry meterRegistry, AdharTracingProperties.MetricsBridgeProperties props) {
        this.meterRegistry = meterRegistry;
        String prefix = props.getMeterPrefix() != null && !props.getMeterPrefix().isBlank()
                ? props.getMeterPrefix().trim() : "span";
        this.durationMeter = prefix + ".duration";
        this.errorMeter = prefix + ".errors";
        this.includeSpanKind = props.isIncludeSpanKind();
        this.recordErrors = props.isRecordErrors();
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // No-op: metrics are derived when the span ends.
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        try {
            boolean error = span.toSpanData().getStatus().getStatusCode() == StatusCode.ERROR;

            Tags baseTags = Tags.of("span.name", span.getName());
            if (includeSpanKind) {
                baseTags = baseTags.and("span.kind", span.getKind().name());
            }

            Tags timerTags = recordErrors ? baseTags.and("error", Boolean.toString(error)) : baseTags;
            Timer.builder(durationMeter)
                    .description("Duration of spans, by name and kind (RED metrics bridge)")
                    .tags(timerTags)
                    .register(meterRegistry)
                    .record(Math.max(0, span.getLatencyNanos()), TimeUnit.NANOSECONDS);

            if (recordErrors && error) {
                Counter.builder(errorMeter)
                        .description("Count of spans that ended in error, by name and kind")
                        .tags(baseTags)
                        .register(meterRegistry)
                        .increment();
            }
        } catch (RuntimeException e) {
            log.warn("Failed to record span metrics for span '{}'", span.getName(), e);
        }
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }
}
