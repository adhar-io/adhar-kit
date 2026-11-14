package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.runtime.AdharBatchRunner;
import com.adhar.kit.batch.runtime.ExitCodes;
import com.adhar.kit.batch.runtime.PartitionConsumer;
import com.adhar.kit.batch.runtime.RunContext;
import com.adhar.kit.batch.runtime.RunOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.integration.partition.StepExecutionRequest;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.Message;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * A worker pod of a remote-partitioned job (design 0025 §5.10): it claims the requests of its run's
 * message group from the partition channel and executes each partition, until the run is over.
 *
 * <pre>
 * loop:
 *   terminating                  stop: exit 143 (the partition in hand was stopped by WorkerStopHandler)
 *   a request claimed            StaleRequestGuard admits it  -> record this pod as its owner, execute the partition
 *                                otherwise                    -> discard it (a failed attempt's leftover)
 *   nothing to claim             the identity's last execution COMPLETED -> exit 0
 *                                                              ABANDONED -> exit 6
 *                                otherwise wait poll-interval: the manager is still splitting, or a restart
 *                                of a failed attempt is coming, and the Job's own lifecycle (the manager
 *                                completing, CompleteJob, the Indexed Job's success policy, a deadline) ends
 *                                the pod
 * </pre>
 *
 * <p>A worker holds no lease. It records itself as the partition's {@link PartitionOwner} before running
 * it, and the manager's {@link WorkerLivenessWatch} fails a partition whose owner pod is gone, so a
 * worker SIGKILLed or OOM-killed mid-partition costs the run one restart instead of the manager's
 * whole timeout.</p>
 *
 * <p>Workers are interchangeable: any of them takes any request, and how many there are is independent
 * of the grid size. A worker never exits because an execution FAILED or STOPPED: on an Indexed Job an
 * index that succeeds is never run again, so a worker that left would be missing from the restart.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class PartitionWorker implements PartitionConsumer {

    private final JdbcPartitionTransport transport;
    private final StaleRequestGuard guard;
    private final WorkerStopHandler stop;
    private final JobRepository jobRepository;
    private final ListableBeanFactory beanFactory;
    private final Duration pollInterval;
    private final AdharBatchRunner.Sleeper sleeper;

    /**
     * @param transport     the partition channel
     * @param guard         decides whether a claimed request may run
     * @param stop          stops the partition in hand when the pod terminates
     * @param jobRepository the shared JobRepository
     * @param beanFactory   holds the worker steps
     * @param pollInterval  {@code adhar.batch.partition.poll-interval}: how long an idle worker waits
     * @param sleeper       waits between polls
     */
    public PartitionWorker(JdbcPartitionTransport transport, StaleRequestGuard guard, WorkerStopHandler stop,
                           JobRepository jobRepository, ListableBeanFactory beanFactory, Duration pollInterval,
                           AdharBatchRunner.Sleeper sleeper) {
        this.transport = transport;
        this.guard = guard;
        this.stop = stop;
        this.jobRepository = jobRepository;
        this.beanFactory = beanFactory;
        this.pollInterval = pollInterval;
        this.sleeper = sleeper;
    }

    @Override
    public RunOutcome consume(Job job, JobParameters parameters, RunContext context, BooleanSupplier stopping) {
        String group = JdbcPartitionTransport.group(job.getName(), parameters);
        QueueChannel channel = transport.channel(group);
        log.info("Worker {} serving the partitions of job [{}] from {}", context.podName(), job.getName(), group);
        int served = 0;
        while (true) {
            if (stopping.getAsBoolean()) {
                return stopped(served);
            }
            Message<?> message = channel.receive(0);
            if (message != null) {
                if (!(message.getPayload() instanceof StepExecutionRequest request)) {
                    log.warn("Discarded a message that is not a partition request: {}", message.getPayload());
                    continue;
                }
                StaleRequestGuard.Verdict verdict = guard.admit(request);
                if (!verdict.admitted()) {
                    log.info("Discarded the request for {} (step execution {}): {}", request.getStepName(),
                            request.getStepExecutionId(), verdict.reason());
                    continue;
                }
                if (stopping.getAsBoolean()) {
                    // Not started yet: hand it back for another worker instead of stopping it.
                    channel.send(message);
                    return stopped(served);
                }
                execute(request, verdict.partition(), context);
                served++;
                continue;
            }
            JobExecution last = lastExecution(job, parameters);
            if (last != null && last.getStatus() == BatchStatus.COMPLETED) {
                return new RunOutcome(RunOutcome.Kind.COMPLETED, null, ExitCodes.COMPLETED, last, null,
                        "worker served " + served + " partition(s); execution " + last.getId() + " completed");
            }
            if (last != null && last.getStatus() == BatchStatus.ABANDONED) {
                return RunOutcome.abandoned(last);
            }
            sleeper.sleep(pollInterval);
        }
    }

    private void execute(StepExecutionRequest request, StepExecution partition, RunContext context) {
        Optional<Step> step = findStep(beanFactory, request.getStepName());
        if (step.isEmpty()) {
            // Fail it at once: left STARTING, the manager would wait for it until its timeout.
            log.error("No step named [{}] in this worker: failing partition {}", request.getStepName(),
                    partition.getStepName());
            partition.setStatus(BatchStatus.FAILED);
            partition.setEndTime(LocalDateTime.now());
            partition.setExitStatus(ExitStatus.FAILED.addExitDescription("No step named [" + request.getStepName()
                    + "] in the worker: is the worker step defined for role worker?"));
            jobRepository.update(partition);
            return;
        }
        log.info("Running partition {} (step execution {}) of execution {}", partition.getStepName(),
                partition.getId(), partition.getJobExecutionId());
        if (!context.podName().isBlank()) {
            // Before it runs: a worker killed from here on leaves a partition the manager can see is orphaned.
            new PartitionOwner(partition.getId(), context.podName(), context.podUid()).writeTo(partition);
            jobRepository.updateExecutionContext(partition);
        }
        stop.serving(partition);
        try {
            step.get().execute(partition);
        } catch (Exception ex) {
            // AbstractStep records its own failures; anything else is logged, never fatal to the worker.
            log.error("Partition {} could not be executed", partition.getStepName(), ex);
        } finally {
            stop.served(partition);
        }
        log.info("Partition {} ended {} (read {}, written {})", partition.getStepName(), partition.getStatus(),
                partition.getReadCount(), partition.getWriteCount());
    }

    private JobExecution lastExecution(Job job, JobParameters parameters) {
        JobInstance instance = jobRepository.getJobInstance(job.getName(), parameters);
        return instance == null ? null : jobRepository.getLastJobExecution(instance);
    }

    private static RunOutcome stopped(int served) {
        return new RunOutcome(RunOutcome.Kind.STOPPED, null, RunOutcome.SIGTERM_STATUS, null, null,
                "worker stopped by SIGTERM after serving " + served + " partition(s); a partition it was running "
                        + "stopped at its last commit and runs again on restart");
    }

    /**
     * @param beanFactory the application's beans
     * @param name        a step's bean name or step name
     * @return the step
     */
    static Optional<Step> findStep(ListableBeanFactory beanFactory, String name) {
        if (beanFactory.containsBean(name) && beanFactory.isTypeMatch(name, Step.class)) {
            return Optional.of(beanFactory.getBean(name, Step.class));
        }
        return beanFactory.getBeansOfType(Step.class).values().stream()
                .filter(step -> name.equals(step.getName())).findFirst();
    }
}
