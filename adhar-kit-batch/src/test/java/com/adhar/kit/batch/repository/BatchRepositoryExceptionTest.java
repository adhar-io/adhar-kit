package com.adhar.kit.batch.repository;

import com.adhar.kit.batch.runtime.ExitCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ExitCodeGenerator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A JobRepository that cannot be used safely is a configuration problem a pod restart cannot fix,
 * so both exceptions exit with code 2.
 */
class BatchRepositoryExceptionTest {

    @Test
    @DisplayName("the repository exception exits with code 2 and keeps its message and cause")
    void repositoryException() {
        var cause = new IllegalArgumentException("cause");

        BatchRepositoryException plain = new BatchRepositoryException("in memory");
        BatchRepositoryException caused = new BatchRepositoryException("in memory", cause);

        assertThat(plain).isInstanceOf(ExitCodeGenerator.class).isInstanceOf(IllegalStateException.class)
                .hasMessage("in memory");
        assertThat(plain.getExitCode()).isEqualTo(ExitCodes.INVALID_REQUEST).isEqualTo(2);
        assertThat(caused).hasCause(cause);
    }

    @Test
    @DisplayName("a failed migration is a repository exception too")
    void migrationException() {
        var cause = new IllegalArgumentException("cause");

        BatchSchemaMigrationException plain = new BatchSchemaMigrationException("missing tables");
        BatchSchemaMigrationException caused = new BatchSchemaMigrationException("missing tables", cause);

        assertThat(plain).isInstanceOf(BatchRepositoryException.class).hasMessage("missing tables");
        assertThat(plain.getExitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(caused).hasCause(cause);
        assertThat(caused.getExitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
    }
}
