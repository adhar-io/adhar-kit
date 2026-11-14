package com.adhar.kit.batch.event;

import com.adhar.kit.batch.listener.BatchRunObserver;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import com.adhar.kit.batch.params.ParameterMasking;
import com.adhar.kit.batch.runtime.ExitCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Publishes {@link BatchRunEvent}s: {@code Started}, {@code Completed}, {@code Failed} and
 * {@code Stopped} for every execution the kit's listeners see, {@code Skipped} and
 * {@code Recovered} for the Kubernetes runner's decisions. A listener that throws is logged and
 * never fails the run.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class BatchRunEventPublisher implements BatchRunObserver {

    private final ApplicationEventPublisher publisher;
    private final ObjectProvider<JobParameterSpecRegistry> specs;

    /**
     * @param publisher the application's event publisher
     * @param specs     the declared parameters (for the sensitive names), resolved lazily
     */
    public BatchRunEventPublisher(ApplicationEventPublisher publisher, ObjectProvider<JobParameterSpecRegistry> specs) {
        this.publisher = publisher;
        this.specs = specs;
    }

    @Override
    public void jobStarted(JobExecution execution) {
        publish(new BatchRunEvent.Started(this, name(execution), instanceId(execution), execution.getId(),
                parameters(name(execution), execution.getJobParameters())));
    }

    @Override
    public void jobFinished(JobExecution execution) {
        String job = name(execution);
        Map<String, String> parameters = parameters(job, execution.getJobParameters());
        BatchStatus status = execution.getStatus();
        if (status == BatchStatus.COMPLETED) {
            publish(new BatchRunEvent.Completed(this, job, instanceId(execution), execution.getId(), parameters,
                    duration(execution)));
        } else if (status == BatchStatus.STOPPED) {
            publish(new BatchRunEvent.Stopped(this, job, instanceId(execution), execution.getId(), parameters));
        } else if (status.isUnsuccessful()) {
            List<String> failures = execution.getAllFailureExceptions().stream()
                    .map(failure -> failure.getClass().getName() + ": " + firstLine(failure.getMessage())).toList();
            publish(new BatchRunEvent.Failed(this, job, instanceId(execution), execution.getId(), parameters,
                    ExitCodes.of(execution), failures));
        }
    }

    /**
     * @param jobName     the job
     * @param instanceId  the JobInstance, when known
     * @param executionId the execution found, when there is one
     * @param parameters  the run's parameters
     * @param reason      why nothing was done
     */
    public void skipped(String jobName, Long instanceId, Long executionId, JobParameters parameters, String reason) {
        publish(new BatchRunEvent.Skipped(this, jobName, instanceId, executionId, parameters(jobName, parameters),
                reason));
    }

    /**
     * @param execution the execution taken over, now FAILED
     * @param reason    why
     */
    public void recovered(JobExecution execution, String reason) {
        publish(new BatchRunEvent.Recovered(this, name(execution), instanceId(execution), execution.getId(),
                parameters(name(execution), execution.getJobParameters()), reason));
    }

    private void publish(BatchRunEvent event) {
        try {
            publisher.publishEvent(event);
        } catch (RuntimeException ex) {
            log.warn("A listener of {} for job [{}] failed: {}", event.getClass().getSimpleName(), event.getJobName(),
                    ex.toString());
        }
    }

    Map<String, String> parameters(String jobName, JobParameters parameters) {
        if (parameters == null) {
            return Map.of();
        }
        JobParameterSpecRegistry registry = specs.getIfAvailable();
        Set<String> sensitive = registry == null ? Set.of() : registry.spec(jobName).sensitiveNames();
        Map<String, String> masked = new TreeMap<>();
        for (JobParameter<?> parameter : parameters) {
            boolean hidden = sensitive.contains(parameter.name()) || ParameterMasking.isSecretName(parameter.name());
            masked.put(parameter.name(), hidden ? ParameterMasking.MASK : String.valueOf(parameter.value()));
        }
        return masked;
    }

    private static String name(JobExecution execution) {
        return execution.getJobInstance().getJobName();
    }

    private static Long instanceId(JobExecution execution) {
        return execution.getJobInstance().getInstanceId();
    }

    private static Duration duration(JobExecution execution) {
        return execution.getStartTime() == null || execution.getEndTime() == null ? Duration.ZERO
                : Duration.between(execution.getStartTime(), execution.getEndTime());
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline >= 0 ? message.substring(0, newline) : message;
    }
}
