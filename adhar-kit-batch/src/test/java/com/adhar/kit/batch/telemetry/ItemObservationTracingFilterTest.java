package com.adhar.kit.batch.telemetry;

import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.TestJobs;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.test.simple.SimpleSpan;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tracing contract of design 0025 §5.13, through Spring Boot's real tracing auto-configuration
 * and handler grouping: a 10,000-item run is fewer than 50 spans (job, step and chunk spans only),
 * while the per-item metrics the dashboards read are still recorded.
 */
class ItemObservationTracingFilterTest {

    private static final int ITEMS = 10_000;
    private static final int CHUNK = 500;

    /** A tracer that keeps every span. */
    @Configuration(proxyBeanMethods = false)
    static class InMemoryTracer {

        @Bean
        SimpleTracer simpleTracer() {
            return new SimpleTracer();
        }
    }

    private static List<SimpleSpan> run(boolean traceItems) throws Exception {
        Map<String, Object> properties = BatchTestApplication.h2();
        properties.put("adhar.batch.telemetry.trace-items", String.valueOf(traceItems));
        try (ConfigurableApplicationContext context = BatchTestApplication.builder(TestJobs.class, InMemoryTracer.class)
                .properties(properties).run()) {
            JobRepository jobRepository = context.getBean(JobRepository.class);
            List<Long> items = LongStream.rangeClosed(1, ITEMS).boxed().toList();
            var step = context.getBean(AdharSteps.class).<Long, Long>chunk("tracedStep", CHUNK)
                    .reader(new ListItemReader<>(items)).processor(item -> item).writer(chunk -> { }).build();
            // As a bean, the job would get the registry from Boot's batch observation post-processor.
            var job = new JobBuilder("tracedJob", jobRepository)
                    .observationRegistry(context.getBean(ObservationRegistry.class)).start(step).build();
            var execution = context.getBean(JobOperator.class).start(job, new JobParametersBuilder().addLong("items", (long) ITEMS).toJobParameters());

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            MeterRegistry meters = context.getBean(MeterRegistry.class);
            assertThat(meters.find("spring.batch.item.read").timers().stream().mapToLong(Timer::count).sum())
                    .as("per-item metrics stay").isGreaterThanOrEqualTo(ITEMS);
            return List.copyOf(context.getBean(SimpleTracer.class).getSpans());
        }
    }

    @Test
    @DisplayName("a 10,000-item run is fewer than 50 spans, with the job, step and chunk spans kept")
    void tenThousandItemsAreFewerThanFiftySpans() throws Exception {
        List<SimpleSpan> spans = run(false);

        assertThat(spans).hasSizeLessThan(50);
        assertThat(spans).extracting(SimpleSpan::getName).anyMatch(name -> name.contains("job"))
                .anyMatch(name -> name.contains("step")).anyMatch(name -> name.contains("chunk"))
                .noneMatch(name -> name.contains("item"));
    }

    @Test
    @DisplayName("adhar.batch.telemetry.trace-items=true traces every item")
    void traceItemsTracesEveryItem() throws Exception {
        assertThat(run(true)).hasSizeGreaterThanOrEqualTo(ITEMS);
    }

    @Test
    @DisplayName("the wrapper opens no span for an item, yet leaves it the tracing context the meter handler needs")
    void wrapperOpensNoSpanForItems() {
        SimpleTracer tracer = new SimpleTracer();
        TracingObservationHandler<Observation.Context> handler =
                ItemObservationTracingFilter.wrap(new DefaultTracingObservationHandler(tracer));
        Observation.Context item = new Observation.Context();
        item.setName("spring.batch.item.process");
        Observation.Context chunk = new Observation.Context();
        chunk.setName("spring.batch.chunk.write");

        handler.onStart(item);
        handler.onScopeOpened(item);
        handler.onScopeClosed(item);
        handler.onStop(item);
        handler.onStart(chunk);
        handler.onStop(chunk);

        TracingObservationHandler.TracingContext tracing = item.get(TracingObservationHandler.TracingContext.class);
        assertThat(tracing).isNotNull();
        assertThat(tracing.getSpan()).isNull();
        assertThat(tracer.getSpans()).extracting(SimpleSpan::getName).containsExactly("spring.batch.chunk.write");
        assertThat(handler.supportsContext(item)).isTrue();
        assertThat(handler.getTracer()).isSameAs(tracer);
        assertThat(new ItemObservationTracingFilter().postProcessAfterInitialization(handler, "h")).isSameAs(handler);
        assertThat(new ItemObservationTracingFilter().postProcessAfterInitialization("other", "o")).isEqualTo("other");
    }
}
