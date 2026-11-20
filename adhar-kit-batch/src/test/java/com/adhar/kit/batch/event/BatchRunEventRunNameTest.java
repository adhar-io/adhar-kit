package com.adhar.kit.batch.event;

import com.adhar.kit.batch.runtime.AdharBatchRunner;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Design 0025 §5.20.8: every run event carries its run's name.
 */
class BatchRunEventRunNameTest {

    @Test
    @DisplayName("an in-service run is named by adhar.run, a Kubernetes run by its Job, and one with neither by nothing")
    void runNames() {
        BatchRunEvent inService = new BatchRunEvent.Skipped(this, "importJob", 4L, 5L,
                Map.of(BatchScheduler.RUN_PARAMETER, "import-job-09932cb3", AdharBatchRunner.K8S_JOB, "ignored"),
                "already-complete");
        BatchRunEvent kubernetes = new BatchRunEvent.Started(this, "importJob", 4L, 5L,
                Map.of(AdharBatchRunner.K8S_JOB, "importer-29123"));
        BatchRunEvent anonymous = new BatchRunEvent.Stopped(this, "importJob", 4L, 5L, null);

        assertThat(inService.getRunName()).isEqualTo("import-job-09932cb3");
        assertThat(kubernetes.getRunName()).isEqualTo("importer-29123");
        assertThat(anonymous.getRunName()).isNull();
    }
}
