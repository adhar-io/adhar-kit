package com.adhar.kit.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.AbstractJob;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.AbstractStep;
import org.springframework.batch.core.step.ListableStepLocator;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.item.ChunkOrientedStep;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Attaches the kit's listeners to every job and step (K5: the first kit release defined them and
 * attached them to nothing).
 *
 * <p>As a {@link BeanPostProcessor} it instruments every {@link Job} and {@link Step} bean, and
 * the steps a job bean reaches through {@link ListableStepLocator} even when they are not beans
 * themselves. {@code AdharSteps} calls {@link #attach(Step)} on the steps it builds. Attaching is
 * idempotent, so a step reached both ways is instrumented once: every job gets the
 * {@link AdharJobExecutionListener}, every step the {@link AdharStepExecutionListener}, and every
 * chunk-oriented step (Spring Batch 6's {@link ChunkOrientedStep}) the {@link AdharChunkListener}
 * and {@link AdharSkipListener}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class BatchListenerAttacher implements BeanPostProcessor {

    private final ObjectProvider<AdharJobExecutionListener> jobListener;
    private final ObjectProvider<AdharStepExecutionListener> stepListener;
    private final ObjectProvider<AdharChunkListener> chunkListener;
    private final ObjectProvider<AdharSkipListener> skipListener;
    private final Map<Object, Boolean> attached = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * The listeners are resolved on first use: a post-processor must not create beans while
     * post-processors are being registered.
     *
     * @param jobListener   attached to every job
     * @param stepListener  attached to every step
     * @param chunkListener attached to every chunk-oriented step
     * @param skipListener  attached to every chunk-oriented step
     */
    public BatchListenerAttacher(ObjectProvider<AdharJobExecutionListener> jobListener,
                                 ObjectProvider<AdharStepExecutionListener> stepListener,
                                 ObjectProvider<AdharChunkListener> chunkListener,
                                 ObjectProvider<AdharSkipListener> skipListener) {
        this.jobListener = jobListener;
        this.stepListener = stepListener;
        this.chunkListener = chunkListener;
        this.skipListener = skipListener;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof Job job) {
            attach(job);
        } else if (bean instanceof Step step) {
            attach(step);
        }
        return bean;
    }

    /**
     * Attaches the job listener to a job, and the step listeners to the steps it can list.
     *
     * @param job the job
     */
    public void attach(Job job) {
        if (job instanceof AbstractJob abstractJob && firstTime(job)) {
            AdharJobExecutionListener listener = jobListener.getIfAvailable();
            if (listener != null) {
                abstractJob.registerJobExecutionListener(listener);
            }
            log.debug("Attached the kit's listeners to job [{}]", job.getName());
        }
        if (job instanceof ListableStepLocator locator) {
            for (String stepName : locator.getStepNames()) {
                Step step = locator.getStep(stepName);
                if (step != null) {
                    attach(step);
                }
            }
        }
    }

    /**
     * Attaches the step listeners to a step.
     *
     * @param step the step
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void attach(Step step) {
        if (!(step instanceof AbstractStep abstractStep) || !firstTime(step)) {
            return;
        }
        AdharStepExecutionListener listener = stepListener.getIfAvailable();
        if (listener != null) {
            abstractStep.registerStepExecutionListener(listener);
        }
        if (step instanceof ChunkOrientedStep chunkStep) {
            AdharChunkListener chunk = chunkListener.getIfAvailable();
            if (chunk != null) {
                chunkStep.registerChunkListener(chunk);
            }
            AdharSkipListener skip = skipListener.getIfAvailable();
            if (skip != null) {
                chunkStep.registerSkipListener(skip);
            }
        }
        log.debug("Attached the kit's listeners to step [{}]", step.getName());
    }

    /**
     * @param target a job or step
     * @return whether the kit's listeners are attached to it
     */
    public boolean isAttached(Object target) {
        return attached.containsKey(target);
    }

    private boolean firstTime(Object target) {
        return attached.putIfAbsent(target, Boolean.TRUE) == null;
    }
}
