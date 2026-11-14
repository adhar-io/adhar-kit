package com.adhar.kit.batch.telemetry;

import com.adhar.kit.batch.fault.DeadLetterSkipListener;
import com.adhar.kit.batch.listener.BatchRunObserver;
import com.adhar.kit.batch.runtime.AdharBatchRunner;
import org.slf4j.MDC;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.core.Ordered;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Puts the run into the logging MDC (design 0025 §5.13), so every log line of a job, including the
 * application's own and those of partition worker threads, carries {@value #JOB},
 * {@value #INSTANCE}, {@value #EXECUTION}, {@value #STEP}, {@value #PARTITION},
 * {@value #BUSINESS_DATE} and {@value #K8S_JOB}. The JSON encoder of adhar-kit-logging writes the
 * MDC into each line.
 *
 * <p>The keys are set when a job or step starts on a thread and the thread's previous MDC is
 * restored when it ends, so a pooled thread never carries one run's keys into the next. It runs
 * first among the observers, so their start-up lines already carry the keys.</p>
 *
 * @since 0.1.0
 */
public class BatchMdc implements BatchRunObserver, Ordered {

    /** The job name. */
    public static final String JOB = "batch.job";
    /** The JobInstance id. */
    public static final String INSTANCE = "batch.instance";
    /** The JobExecution id. */
    public static final String EXECUTION = "batch.execution";
    /** The step name (without the partition). */
    public static final String STEP = "batch.step";
    /** The partition, on a partition's worker thread. */
    public static final String PARTITION = "batch.partition";
    /** The business date, when the run has one. */
    public static final String BUSINESS_DATE = "batch.businessDate";
    /** The Kubernetes Job of the run pod, in Kubernetes mode. */
    public static final String K8S_JOB = "k8s.job";

    private final ThreadLocal<Deque<Map<String, String>>> saved = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * @param execution a job execution
     * @return its job-level keys, as the MDC and adhar-kit-logging's BATCH events carry them
     */
    public static Map<String, String> jobContext(JobExecution execution) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put(JOB, execution.getJobInstance().getJobName());
        context.put(INSTANCE, String.valueOf(execution.getJobInstance().getInstanceId()));
        context.put(EXECUTION, String.valueOf(execution.getId()));
        LocalDate businessDate = DeadLetterSkipListener.businessDate(execution.getJobParameters());
        if (businessDate != null) {
            context.put(BUSINESS_DATE, businessDate.toString());
        }
        String k8sJob = execution.getJobParameters().getString(AdharBatchRunner.K8S_JOB);
        if (k8sJob != null && !k8sJob.isBlank()) {
            context.put(K8S_JOB, k8sJob);
        }
        return context;
    }

    @Override
    public void jobStarted(JobExecution execution) {
        push(jobContext(execution));
    }

    @Override
    public void jobFinished(JobExecution execution) {
        pop();
    }

    @Override
    public void stepStarted(StepExecution execution) {
        Map<String, String> context = jobContext(execution.getJobExecution());
        String name = execution.getStepName();
        int separator = name.indexOf(':');
        context.put(STEP, separator < 0 ? name : name.substring(0, separator));
        if (separator >= 0) {
            context.put(PARTITION, name.substring(separator + 1));
        }
        push(context);
    }

    @Override
    public void stepFinished(StepExecution execution) {
        pop();
    }

    private void push(Map<String, String> context) {
        saved.get().push(snapshot());
        context.forEach(MDC::put);
    }

    private void pop() {
        Deque<Map<String, String>> stack = saved.get();
        if (stack.isEmpty()) {
            saved.remove();
            return;
        }
        Map<String, String> previous = stack.pop();
        if (previous.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
        if (stack.isEmpty()) {
            saved.remove();
        }
    }

    private static Map<String, String> snapshot() {
        Map<String, String> copy = MDC.getCopyOfContextMap();
        return copy == null ? Map.of() : copy;
    }
}
