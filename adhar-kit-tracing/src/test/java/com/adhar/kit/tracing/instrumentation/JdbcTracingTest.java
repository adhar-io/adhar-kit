package com.adhar.kit.tracing.instrumentation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import net.ttddyy.observation.boot.autoconfigure.DataSourceObservationAutoConfiguration;
import net.ttddyy.observation.boot.autoconfigure.opentelemetry.DataSourceObservationOpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.metrics.DataSourcePoolMetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Queries through the application's own DataSource become spans: one for acquiring the
 * connection, one per statement, children of the caller's span. The statement is recorded
 * sanitized - literals replaced, parameter values never attached - and Hikari's pool metrics
 * are still published for the decorated DataSource.
 */
class JdbcTracingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(TracingTestSupport::applyKitEnvironmentDefaults)
            .withConfiguration(TracingTestSupport.TRACING)
            .withConfiguration(AutoConfigurations.of(
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class,
                    SimpleMetricsExportAutoConfiguration.class,
                    DataSourceAutoConfiguration.class,
                    DataSourcePoolMetricsAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class,
                    DataSourceObservationAutoConfiguration.class,
                    DataSourceObservationOpenTelemetryAutoConfiguration.class))
            .withUserConfiguration(TracingTestSupport.InMemoryExport.class)
            .withPropertyValues(TracingTestSupport.PROPERTIES)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
                    "spring.datasource.hikari.pool-name=orders-pool");

    @Test
    void queriesAndConnectionAcquisitionAreSpansWithSanitizedSql() {
        runner.run(context -> {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("create table orders (id int primary key, customer varchar(64))");
            context.getBean(InMemorySpanExporter.class).reset();

            ObservationRegistry registry = context.getBean(ObservationRegistry.class);
            Observation.createNotStarted("load-order", registry).observe(() -> {
                jdbc.queryForList("select customer from orders where id = 4711 and customer = 'alice'");
                jdbc.queryForList("select customer from orders where id = ?", 1337);
            });

            List<SpanData> spans = context.getBean(InMemorySpanExporter.class).getFinishedSpanItems();
            SpanData parent = spans.stream().filter(s -> s.getName().equals("load-order")).findFirst().orElseThrow();
            List<SpanData> jdbcSpans = spans.stream().filter(s -> s != parent).toList();

            assertThat(jdbcSpans).isNotEmpty()
                    .allSatisfy(span -> assertThat(span.getTraceId()).isEqualTo(parent.getTraceId()));
            assertThat(jdbcSpans).extracting(SpanData::getName).anyMatch(name -> name.contains("connection"));

            List<String> statements = jdbcSpans.stream()
                    .map(span -> span.getAttributes().get(AttributeKey.stringKey("db.query.text")))
                    .filter(text -> text != null)
                    .toList();
            assertThat(statements).hasSize(2)
                    .allSatisfy(text -> assertThat(text).startsWithIgnoringCase("select customer from orders where id = ?"));

            // No literal and no bound parameter value anywhere on any span.
            String everyAttribute = jdbcSpans.stream().map(s -> s.getAttributes().toString()).reduce("", String::concat);
            assertThat(everyAttribute).doesNotContain("4711", "alice", "1337");
        });
    }

    @Test
    void aServiceWithoutADatabaseStartsWithoutSpringBootJdbc() {
        // The kit excludes spring-boot-jdbc from datasource-micrometer so a database-less
        // service (gateway-service) does not gain DataSource auto-configuration.
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("org.springframework.boot.jdbc", "com.zaxxer.hikari"))
                .withInitializer(TracingTestSupport::applyKitEnvironmentDefaults)
                .withConfiguration(TracingTestSupport.TRACING)
                .withConfiguration(AutoConfigurations.of(
                        DataSourceObservationAutoConfiguration.class,
                        DataSourceObservationOpenTelemetryAutoConfiguration.class))
                .withPropertyValues(TracingTestSupport.PROPERTIES)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(javax.sql.DataSource.class));
    }

    @Test
    void hikariPoolMetricsAreUnchanged() {
        runner.run(context -> {
            context.getBean(JdbcTemplate.class).queryForObject("select 1", Integer.class);
            MeterRegistry meters = context.getBean(MeterRegistry.class);
            assertThat(meters.find("hikaricp.connections").tag("pool", "orders-pool").gauge()).isNotNull();
            assertThat(meters.find("hikaricp.connections.acquire").tag("pool", "orders-pool").timer()).isNotNull();
        });
    }
}
