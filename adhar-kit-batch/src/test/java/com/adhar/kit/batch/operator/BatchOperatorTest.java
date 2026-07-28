package com.adhar.kit.batch.operator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchOperator} using a mocked {@link JobOperator} and
 * {@link JobRepository}.
 */
@ExtendWith(MockitoExtension.class)
class BatchOperatorTest {

    @Mock
    private JobOperator jobOperator;
    @Mock
    private JobRepository jobRepository;

    private BatchOperator operator;

    @BeforeEach
    void setUp() {
        operator = new BatchOperator(jobOperator, jobRepository);
    }

    @Test
    @DisplayName("restart delegates to JobOperator and returns the new execution id")
    void restartDelegates() throws Exception {
        when(jobOperator.restart(10L)).thenReturn(11L);

        assertThat(operator.restart(10L)).isEqualTo(11L);
        verify(jobOperator).restart(10L);
    }

    @Test
    @DisplayName("stop delegates to JobOperator")
    void stopDelegates() throws Exception {
        when(jobOperator.stop(5L)).thenReturn(true);

        assertThat(operator.stop(5L)).isTrue();
        verify(jobOperator).stop(5L);
    }

    @Test
    @DisplayName("stop propagates JobExecutionNotRunningException")
    void stopPropagates() throws Exception {
        when(jobOperator.stop(5L)).thenThrow(new JobExecutionNotRunningException("not running"));

        assertThatThrownBy(() -> operator.stop(5L))
                .isInstanceOf(JobExecutionNotRunningException.class);
    }

    @Test
    @DisplayName("abandon delegates to JobOperator and returns the execution")
    void abandonDelegates() throws Exception {
        var exec = mock(JobExecution.class);
        when(jobOperator.abandon(7L)).thenReturn(exec);

        assertThat(operator.abandon(7L)).isSameAs(exec);
        verify(jobOperator).abandon(7L);
    }

    @Test
    @DisplayName("getRunningExecutions delegates to JobOperator")
    void getRunningExecutionsDelegates() throws Exception {
        when(jobOperator.getRunningExecutions("job")).thenReturn(Set.of(1L, 2L));

        assertThat(operator.getRunningExecutions("job")).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("getJobExecution delegates to JobRepository")
    void getJobExecutionDelegates() {
        var exec = mock(JobExecution.class);
        when(jobRepository.getJobExecution(3L)).thenReturn(exec);

        assertThat(operator.getJobExecution(3L)).isSameAs(exec);
        verify(jobRepository).getJobExecution(3L);
    }
}
