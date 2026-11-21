package com.adhar.kit.batch.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.FlowStep;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Which steps {@link RestartGuard} considers a job to have: a restart is checked against every
 * step the job can run, nested flows included, and a job it cannot list is not checked.
 */
class RestartGuardTest {

    private final JobRepository repository = mock(JobRepository.class);

    private Step step(String name) {
        return new StepBuilder(name, repository).tasklet((contribution, context) -> RepeatStatus.FINISHED,
                new ResourcelessTransactionManager()).build();
    }

    @Test
    @DisplayName("a simple job's steps")
    void simpleJob() {
        Job job = new JobBuilder("j", repository).start(step("a")).next(step("b")).build();

        assertThat(RestartGuard.stepNames(job)).contains(Set.of("a", "b"));
    }

    @Test
    @DisplayName("a flow job's steps, and the steps inside a flow step, which lists nothing itself")
    void flowsAreListed() {
        Flow inner = new FlowBuilder<Flow>("inner").start(step("inner-1")).next(step("inner-2")).build();
        Step wrapper = new StepBuilder("wrapper", repository).flow(inner).build();
        Flow split = new FlowBuilder<Flow>("split").start(step("split-1")).build();
        Job job = new JobBuilder("j", repository).start(step("first")).on("*").to(wrapper)
                .from(wrapper).on("*").to(split).end().build();

        assertThat(RestartGuard.stepNames(job).orElseThrow())
                .containsExactlyInAnyOrder("first", "wrapper", "inner-1", "inner-2", "split-1");
    }

    @Test
    @DisplayName("a flow step whose flow cannot be read makes the job one that is not judged")
    void unreadableFlowStep() {
        // FlowStep's own constructor drops its flow (Spring Batch 6.0.4); the builder sets it.
        FlowStep wrapper = new FlowStep(new FlowBuilder<Flow>("inner").start(step("inner-1")).build());
        wrapper.setName("wrapper");
        Job job = new JobBuilder("j", repository).start(step("first")).next(wrapper).build();

        assertThat(RestartGuard.stepNames(job)).isEmpty();
    }

    @Test
    @DisplayName("a job that cannot list its steps is not judged")
    void customJob() {
        Job job = new Job() {
            @Override
            public String getName() {
                return "custom";
            }

            @Override
            public void execute(JobExecution execution) {
            }
        };

        assertThat(RestartGuard.stepNames(job)).isEmpty();
        assertThat(new RestartGuard(repository).check(job, null, "")).isEmpty();
    }

    @Test
    @DisplayName("the message names the image change only when both images are known and differ")
    void imagesInTheMessage() {
        var known = new RestartGuard.Change("j", 7, Set.of("old"), Set.of("new"), "sha256:a", "sha256:b");
        var same = new RestartGuard.Change("j", 7, Set.of("old"), Set.of("new"), "sha256:a", "sha256:a");
        var unknown = new RestartGuard.Change("j", 7, Set.of("old"), Set.of("new"), "", "sha256:b");

        assertThat(known.imageChanged()).isTrue();
        assertThat(known.message()).contains("it ran image sha256:a; this is sha256:b");
        assertThat(same.imageChanged()).isFalse();
        assertThat(same.message()).contains("(image sha256:a)").doesNotContain("this is");
        assertThat(unknown.imageChanged()).isFalse();
        assertThat(unknown.message()).contains("ran; its steps are now [new]");
    }
}
