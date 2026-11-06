package com.adhar.kit.batch.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;

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
}
