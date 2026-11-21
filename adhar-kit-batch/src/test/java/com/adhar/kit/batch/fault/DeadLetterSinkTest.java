package com.adhar.kit.batch.fault;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.listener.BatchListenerAttacher;
import com.adhar.kit.batch.step.AdharSteps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.jdbc.core.JdbcOperations;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link JdbcDeadLetterSink}, the business date a dead letter carries, and the step builder's
 * handling of {@code deadLetter()} without a sink.
 */
class DeadLetterSinkTest {

    @Test
    @DisplayName("on the kit's PostgreSQL schema ITEM is cast to JSONB; elsewhere it is a plain parameter")
    void sql() {
        assertThat(new JdbcDeadLetterSink(mock(JdbcOperations.class), "batch.ADHAR_BATCH_DEAD_LETTER", true).sql())
                .startsWith("INSERT INTO batch.ADHAR_BATCH_DEAD_LETTER (").contains("CAST(? AS JSONB)");
        assertThat(new JdbcDeadLetterSink(mock(JdbcOperations.class), "ADHAR_BATCH_DEAD_LETTER", false).sql())
                .doesNotContain("JSONB");
    }

    @Test
    @DisplayName("values longer than their column are cut, not refused")
    void cut() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        String longMessage = "m".repeat(3000);

        new JdbcDeadLetterSink(jdbc, "T", false).write(new DeadLetterSink.Entry(7, "j".repeat(150), "s", null,
                "write", null, null, "E", longMessage));

        verify(jdbc).update(any(String.class), eq(7L), eq("j".repeat(100)), eq("s"), eq(null), eq("write"), eq(null),
                eq(null), eq("E"), eq("m".repeat(2500)));
        assertThat(JdbcDeadLetterSink.cut(null, 3)).isNull();
        assertThat(JdbcDeadLetterSink.cut("a\u0000b", 3)).isEqualTo("a�b");
        assertThat(JdbcDeadLetterSink.json("{\"a\":\"x\\u0000\",\"b\":\"\\\\u0000\",\"c\":\"\\\\\\u0000\"}"))
                .isEqualTo("{\"a\":\"x\\ufffd\",\"b\":\"\\\\u0000\",\"c\":\"\\\\\\ufffd\"}");
    }

    @Test
    @DisplayName("the business date is the businessDate parameter, else the first identifying date")
    void businessDate() {
        JobParameters named = new JobParametersBuilder().addLocalDate("asOf", LocalDate.of(2026, 1, 1))
                .addLocalDate("businessDate", LocalDate.of(2026, 11, 3)).toJobParameters();
        JobParameters other = new JobParametersBuilder().addLocalDate("valueDate", LocalDate.of(2026, 2, 2))
                .addLocalDate("asOf", LocalDate.of(2026, 1, 1)).addLocalDate("ignored", LocalDate.of(2020, 1, 1), false)
                .toJobParameters();
        JobParameters text = new JobParametersBuilder().addString("businessDate", "2026-11-03").toJobParameters();

        assertThat(DeadLetterSkipListener.businessDate(named)).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(DeadLetterSkipListener.businessDate(other)).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(DeadLetterSkipListener.businessDate(text)).isNull();
    }

    @Test
    @DisplayName("Jackson 3 writes the item; a serializer that fails keeps the item by type only")
    void serializer() {
        assertThat(DeadLetterSkipListener.defaultSerializer().apply(List.of("a", 1))).isEqualTo("[\"a\",1]");
    }

    @Test
    @DisplayName("deadLetter() with no sink fails the build while enabled, and is ignored when disabled")
    void noSink() {
        BatchProperties properties = new BatchProperties();
        AdharSteps steps = new AdharSteps(new ResourcelessJobRepository(), new ResourcelessTransactionManager(),
                mock(BatchListenerAttacher.class), properties, null, null, null);
        FaultTolerance faultTolerance = FaultTolerance.none().skip(IllegalArgumentException.class, 1).deadLetter();

        assertThatThrownBy(() -> steps.<String, String>chunk("s").reader(new ListItemReader<>(List.of("a")))
                .writer(chunk -> { }).faultTolerance(faultTolerance).build())
                .hasMessageContaining("there is no dead-letter table");
        properties.getDeadLetter().setEnabled(false);
        assertThat(steps.<String, String>chunk("s").reader(new ListItemReader<>(List.of("a"))).writer(chunk -> { })
                .faultTolerance(faultTolerance).build()).isNotNull();
    }
}
