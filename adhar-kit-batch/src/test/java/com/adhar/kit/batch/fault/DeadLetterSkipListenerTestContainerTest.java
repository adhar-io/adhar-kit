package com.adhar.kit.batch.fault;

import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dead-letter table on PostgreSQL 18 in the kit's own schema: the item is stored as JSONB
 * (queryable by field), and a dead letter commits with its chunk.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = {BatchTestApplication.class, TestJobs.class})
class DeadLetterSkipListenerTestContainerTest {

    private static final String URL = PostgresDatabases.createDatabase("dlq");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BatchTestApplication.postgres(URL).forEach((key, value) -> registry.add(key, () -> value));
    }

    record Payment(String id, long cents) {
    }

    @Autowired
    AdharSteps steps;
    @Autowired
    JobRepository jobRepository;
    @Autowired
    JobOperator jobOperator;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("a skipped item is a JSONB row in batch.ADHAR_BATCH_DEAD_LETTER")
    void jsonbOnPostgres() throws Exception {
        var step = steps.<Payment, Payment>chunk("payments", 10)
                .reader(new ListItemReader<>(List.of(new Payment("p1", 100), new Payment("p2", -5))))
                .processor(payment -> {
                    if (payment.cents() < 0) {
                        throw new IllegalArgumentException("negative amount");
                    }
                    return payment;
                })
                .writer(chunk -> { })
                .faultTolerance(FaultTolerance.none().skip(IllegalArgumentException.class, 10).deadLetter())
                .build();
        JobExecution execution = jobOperator.start(new JobBuilder("paymentsJob", jobRepository).start(step).build(),
                new JobParametersBuilder().addLocalDate("businessDate", LocalDate.of(2026, 11, 3)).toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        Map<String, Object> row = jdbc.queryForMap("SELECT ITEM->>'id' AS ID, (ITEM->>'cents')::BIGINT AS CENTS, "
                + "BUSINESS_DATE, PHASE, JOB_EXECUTION_ID FROM batch.ADHAR_BATCH_DEAD_LETTER");
        assertThat(row).containsEntry("id", "p2").containsEntry("cents", -5L).containsEntry("phase", "process")
                .containsEntry("job_execution_id", execution.getId());
        assertThat(row.get("business_date").toString()).isEqualTo("2026-11-03");
    }
}
