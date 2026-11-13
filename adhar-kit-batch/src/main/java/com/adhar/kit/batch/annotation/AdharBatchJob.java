package com.adhar.kit.batch.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a batch job to the platform: its name, what it does, and the parameters a run accepts.
 * Put it on the {@code @Bean} method of the {@code Job} (or on the job's class).
 *
 * <pre>{@code
 * @Bean
 * @AdharBatchJob(description = "Imports inbound records into Postgres", parameters = {
 *     @JobParam(name = "businessDate", type = ParamType.DATE, source = ParamSource.SCHEDULE),
 *     @JobParam(name = "source", defaultValue = "inbound/records.csv", pattern = "^[a-zA-Z0-9/._-]+$"),
 *     @JobParam(name = "dryRun", type = ParamType.BOOLEAN, identifying = false, defaultValue = "false")})
 * Job importJob(JobRepository jobRepository, Step importStep) {
 *     return new JobBuilder("importJob", jobRepository).start(importStep).build();
 * }
 * }</pre>
 *
 * <p>The Kubernetes runner validates every run against these declarations, and
 * {@code BatchContractAssertions} fails the build when the deployed {@code BatchJob} declares
 * different ones. A job with a {@code JobParametersIncrementer} is refused unless
 * {@link #allowIncrementer()}: an incrementer makes Spring Batch ignore the supplied parameters,
 * so a scheduled run could never be restarted (design 0025 §5.6).</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdharBatchJob {

    /**
     * @return the job name; empty means the bean name
     */
    String name() default "";

    /**
     * @return what the job does, for the Console and the CLI
     */
    String description() default "";

    /**
     * @return the parameters a run accepts; anything else is refused
     */
    JobParam[] parameters() default {};

    /**
     * @return whether the job may carry an incrementer (housekeeping jobs only)
     */
    boolean allowIncrementer() default false;
}
