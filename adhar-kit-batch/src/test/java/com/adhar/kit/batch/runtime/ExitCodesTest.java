package com.adhar.kit.batch.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.adhar.kit.batch.fault.NonRetryableBatchException;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exit codes are a contract with the platform's pod failure policy (design 0025 §5.7).
 */
class ExitCodesTest {

    @Test
    @DisplayName("the terminal codes equal Spring Boot's BatchStatus ordinals")
    void terminalCodesAreOrdinals() {
        assertThat(ExitCodes.COMPLETED).isEqualTo(BatchStatus.COMPLETED.ordinal()).isZero();
        assertThat(ExitCodes.STOPPED).isEqualTo(BatchStatus.STOPPED.ordinal()).isEqualTo(4);
        assertThat(ExitCodes.FAILED_RETRYABLE).isEqualTo(BatchStatus.FAILED.ordinal()).isEqualTo(5);
        assertThat(ExitCodes.ABANDONED).isEqualTo(BatchStatus.ABANDONED.ordinal()).isEqualTo(6);
        assertThat(ExitCodes.UNKNOWN).isEqualTo(BatchStatus.UNKNOWN.ordinal()).isEqualTo(7);
    }

    @Test
    @DisplayName("the kit's own codes take the ordinals a finished run never exits with")
    void kitCodes() {
        assertThat(ExitCodes.STARTUP_FAILURE).isEqualTo(1);
        assertThat(ExitCodes.INVALID_REQUEST).isEqualTo(2);
        assertThat(ExitCodes.FAILED_NOT_RETRYABLE).isEqualTo(3);
    }

    private static JobExecution execution(BatchStatus status, Throwable... failures) {
        JobExecution execution = new JobExecution(1L, new JobInstance(1L, "job"), new JobParameters());
        execution.setStatus(status);
        for (Throwable failure : failures) {
            execution.addFailureException(failure);
        }
        return execution;
    }

    @Test
    @DisplayName("a finished execution maps to its code; non-terminal statuses are unknown")
    void ofStatus() {
        assertThat(ExitCodes.of(execution(BatchStatus.COMPLETED))).isEqualTo(ExitCodes.COMPLETED);
        assertThat(ExitCodes.of(execution(BatchStatus.STOPPED))).isEqualTo(ExitCodes.STOPPED);
        assertThat(ExitCodes.of(execution(BatchStatus.ABANDONED))).isEqualTo(ExitCodes.ABANDONED);
        assertThat(ExitCodes.of(execution(BatchStatus.UNKNOWN))).isEqualTo(ExitCodes.UNKNOWN);
        assertThat(ExitCodes.of(execution(BatchStatus.STARTED))).isEqualTo(ExitCodes.UNKNOWN);
        assertThat(ExitCodes.of(execution(BatchStatus.FAILED))).isEqualTo(ExitCodes.FAILED_RETRYABLE);
    }

    @Test
    @DisplayName("FAILED is 3 when a non-retryable exception or the skip limit is anywhere in a cause chain, else 5")
    void failedClassification() {
        assertThat(ExitCodes.of(execution(BatchStatus.FAILED, new IllegalStateException("db down"))))
                .isEqualTo(ExitCodes.FAILED_RETRYABLE);
        assertThat(ExitCodes.of(execution(BatchStatus.FAILED, new IllegalStateException("x"),
                new RuntimeException(new NonRetryableBatchException("bad data"))))).isEqualTo(ExitCodes.FAILED_NOT_RETRYABLE);
        assertThat(ExitCodes.of(execution(BatchStatus.FAILED,
                new RuntimeException(new SkipLimitExceededException(10, new IllegalArgumentException())))))
                .isEqualTo(ExitCodes.FAILED_NOT_RETRYABLE);
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertThat(ExitCodes.isNotRetryable(execution(BatchStatus.FAILED, a))).as("a cause cycle ends").isFalse();
    }
}
