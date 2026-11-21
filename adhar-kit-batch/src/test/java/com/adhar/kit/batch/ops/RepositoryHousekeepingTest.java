package com.adhar.kit.batch.ops;

import com.adhar.kit.batch.runtime.ExecutionLease;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RepositoryHousekeeping}'s rules (design 0025 D4, A3.5) against a JobRepository that says
 * what a test needs; the same code on a real repository is in {@code OpsCommandsTest}.
 */
class RepositoryHousekeepingTest {

    private static final Instant NOW = Instant.parse("2026-11-21T00:00:00Z");

    private final JobRepository repository = mock(JobRepository.class);
    private final ExecutionLease lease = mock(ExecutionLease.class);
    private final List<String> recorded = new CopyOnWriteArrayList<>();
    private final RepositoryHousekeeping housekeeping = new RepositoryHousekeeping(repository, lease,
            (job, succeeded, deleted) -> recorded.add(succeeded + ":" + deleted), Clock.fixed(NOW, ZoneOffset.UTC));

    private static LocalDateTime daysAgo(int days) {
        return LocalDateTime.ofInstant(NOW.minusSeconds(days * 86_400L), ZoneId.systemDefault());
    }

    /** Instances 1..n, newest (n) first, each with one execution of the given status ended that many days ago. */
    private List<JobInstance> instances(BatchStatus status, int daysAgo, long... ids) {
        List<JobInstance> instances = new ArrayList<>();
        for (long id : ids) {
            JobInstance instance = new JobInstance(id, "importJob");
            JobExecution execution = new JobExecution(id * 10, instance, new JobParameters());
            execution.setStatus(status);
            execution.setEndTime(daysAgo(daysAgo));
            when(repository.getJobExecutions(instance)).thenReturn(List.of(execution));
            instances.add(instance);
        }
        return instances;
    }

    @Test
    @DisplayName("a job that cannot be restarted keeps no FAILED instance for a restart that cannot happen")
    void notRestartable() {
        List<JobInstance> all = instances(BatchStatus.FAILED, 200, 3, 2, 1);
        when(repository.getJobInstances(eq("importJob"), eq(0), anyInt())).thenReturn(all);

        RepositoryHousekeeping.Result result = housekeeping.clean("importJob", false, 90, 1);

        assertThat(result).isEqualTo(new RepositoryHousekeeping.Result(3, 1, 2));
        verify(repository).deleteJobInstance(all.get(1));
        verify(repository).deleteJobInstance(all.get(2));
        verify(lease).release(20L);
        verify(lease).release(10L);
        assertThat(recorded).containsExactly("true:2");
    }

    @Test
    @DisplayName("a restartable job keeps its newest FAILED or STOPPED instance however old, and only that one")
    void restartable() {
        List<JobInstance> all = new ArrayList<>(instances(BatchStatus.STOPPED, 300, 3));
        all.addAll(instances(BatchStatus.FAILED, 300, 2, 1));
        when(repository.getJobInstances(eq("importJob"), eq(0), anyInt())).thenReturn(all);

        RepositoryHousekeeping.Result result = housekeeping.clean("importJob", true, 90, 0);

        assertThat(result.deleted()).isEqualTo(2);
        verify(repository, never()).deleteJobInstance(all.get(0));
    }

    @Test
    @DisplayName("an instance without executions is being launched right now and stays")
    void instanceWithoutExecutions() {
        JobInstance launching = new JobInstance(9L, "importJob");
        when(repository.getJobInstances(eq("importJob"), eq(0), anyInt())).thenReturn(List.of(launching));
        when(repository.getJobExecutions(launching)).thenReturn(List.of());

        assertThat(housekeeping.clean("importJob", true, 1, 0).deleted()).isZero();
        verify(repository, never()).deleteJobInstance(launching);
    }

    @Test
    @DisplayName("every page of instances is read before anything is deleted, and the newest are kept by id")
    void pages() {
        int total = RepositoryHousekeeping.PAGE + 3;
        List<JobInstance> all = instances(BatchStatus.COMPLETED, 200,
                LongStream.rangeClosed(1, total).map(id -> total + 1 - id).toArray());
        when(repository.getJobInstances("importJob", 0, RepositoryHousekeeping.PAGE))
                .thenReturn(all.subList(0, RepositoryHousekeeping.PAGE));
        when(repository.getJobInstances("importJob", RepositoryHousekeeping.PAGE, RepositoryHousekeeping.PAGE))
                .thenReturn(all.subList(RepositoryHousekeeping.PAGE, total));

        RepositoryHousekeeping.Result result = housekeeping.clean("importJob", true, 90, 5);

        assertThat(result).isEqualTo(new RepositoryHousekeeping.Result(total, 5, total - 5));
        verify(repository, never()).deleteJobInstance(all.get(4));
        verify(repository).deleteJobInstance(all.get(5));
    }

    @Test
    @DisplayName("a failure part-way is counted as failed with what was deleted so far, and rethrown")
    void failure() {
        List<JobInstance> all = instances(BatchStatus.COMPLETED, 200, 2, 1);
        when(repository.getJobInstances(eq("importJob"), eq(0), anyInt())).thenReturn(all);
        doThrow(new DataAccessResourceFailureException("connection lost")).when(repository).deleteJobInstance(all.get(1));

        assertThatThrownBy(() -> housekeeping.clean("importJob", true, 90, 0))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(recorded).containsExactly("false:1");
    }

    @Test
    @DisplayName("a retention of less than a day or a negative keepLast is refused")
    void arguments() {
        assertThatThrownBy(() -> housekeeping.clean("importJob", true, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> housekeeping.clean("importJob", true, 1, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(RepositoryHousekeeping.Recorder.none()).isNotNull();
        RepositoryHousekeeping.Recorder.none().record("importJob", true, 1);
    }
}
