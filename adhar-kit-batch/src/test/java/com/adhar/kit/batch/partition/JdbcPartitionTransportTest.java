package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.runtime.AdharBatchRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.DefaultJobKeyGenerator;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The partition channel's group (design 0025 §5.10): {@code adhar-batch:<job>:<jobKey>}, computed
 * alike by the manager and by every worker from the run's parameters. Only the identifying ones may
 * count: the provenance the runner adds (trigger, attempt, pod, image) differs from pod to pod.
 */
class JdbcPartitionTransportTest {

    private static JobParameters identity(String date) {
        return new JobParametersBuilder().addLocalDate("businessDate", LocalDate.parse(date))
                .addString("region", "sg").toJobParameters();
    }

    @Test
    @DisplayName("the group is adhar-batch:<job>:<Spring Batch's job key>")
    void groupIsTheJobKey() {
        JobParameters parameters = identity("2026-11-03");

        assertThat(JdbcPartitionTransport.group("settlementJob", parameters))
                .isEqualTo("adhar-batch:settlementJob:" + new DefaultJobKeyGenerator().generateKey(parameters));
    }

    @Test
    @DisplayName("a manager and a worker with different provenance share a group; another identity does not")
    void onlyIdentifyingParametersCount() {
        JobParameters manager = new JobParametersBuilder(identity("2026-11-03"))
                .addString(AdharBatchRunner.TRIGGER, "schedule", false).addLong(AdharBatchRunner.ATTEMPT, 2L, false)
                .addString(AdharBatchRunner.APP_VERSION, "sha256:aaa", false).toJobParameters();
        JobParameters worker = new JobParametersBuilder(identity("2026-11-03"))
                .addString(AdharBatchRunner.TRIGGER, "schedule", false).addLong(AdharBatchRunner.ATTEMPT, 1L, false)
                .addString(AdharBatchRunner.K8S_JOB, "settlement-x7k2", false).toJobParameters();

        assertThat(JdbcPartitionTransport.group("settlementJob", worker))
                .isEqualTo(JdbcPartitionTransport.group("settlementJob", manager));
        assertThat(JdbcPartitionTransport.group("settlementJob", identity("2026-11-04")))
                .isNotEqualTo(JdbcPartitionTransport.group("settlementJob", manager));
        assertThat(JdbcPartitionTransport.group("otherJob", manager))
                .isNotEqualTo(JdbcPartitionTransport.group("settlementJob", manager));
    }
}
