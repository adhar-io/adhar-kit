package com.adhar.kit.batch.ops;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The command labels are the CLI's ({@code cmd/batch/ops.go}: {@code abandon}, {@code recover},
 * {@code dlq-replay}) and the Console's; a mismatch would make every ops Job exit 2.
 */
class BatchCommandTest {

    @Test
    @DisplayName("every label the CLI and the Console write parses, ignoring case and blanks; anything else does not")
    void labels() {
        assertThat(Arrays.stream(BatchCommand.values()).map(BatchCommand::label))
                .containsExactly("abandon", "recover", "stop", "status", "housekeeping", "dlq-replay");
        assertThat(BatchCommand.parse(" DLQ-Replay ")).contains(BatchCommand.DLQ_REPLAY);
        assertThat(BatchCommand.parse("abandon")).contains(BatchCommand.ABANDON);
        assertThat(BatchCommand.parse("restart")).as("a restart runs the business job, never an ops command")
                .isEmpty();
        assertThat(BatchCommand.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("abandon, recover, stop and dlq-replay need an execution; status and housekeeping do not")
    void executionNeeded() {
        assertThat(Arrays.stream(BatchCommand.values()).filter(BatchCommand::needsExecution))
                .containsExactly(BatchCommand.ABANDON, BatchCommand.RECOVER, BatchCommand.STOP, BatchCommand.DLQ_REPLAY);
    }
}
