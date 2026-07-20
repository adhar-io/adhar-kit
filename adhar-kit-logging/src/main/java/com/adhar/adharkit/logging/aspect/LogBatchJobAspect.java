package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.LogBatchJob;
import com.adhar.adharkit.logging.batch.BatchJobLogger;
import com.adhar.adharkit.logging.batch.BatchJobRun;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Aspect implementation for {@link LogBatchJob @LogBatchJob}: wraps the annotated method in a
 * tracked {@link BatchJobRun}, publishing STARTED and COMPLETED/FAILED batch events with the
 * job's duration.
 */
@Aspect
@Component
public class LogBatchJobAspect {

    private final BatchJobLogger batchJobLogger;

    public LogBatchJobAspect(BatchJobLogger batchJobLogger) {
        this.batchJobLogger = batchJobLogger;
    }

    @Around("@annotation(com.adhar.adharkit.logging.annotation.LogBatchJob)")
    public Object logBatchJob(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LogBatchJob annotation = AnnotationUtils.findAnnotation(method, LogBatchJob.class);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        String jobName = annotation.value().isEmpty()
                ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
                : annotation.value();

        BatchJobRun run = batchJobLogger.startJob(jobName);
        try {
            Object result = joinPoint.proceed();
            run.complete();
            return result;
        } catch (Throwable t) {
            run.fail(t);
            throw t;
        }
    }
}
