package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.runtime.BatchRole;
import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A bean that exists only in the roles named (design 0025 §5.10): one image, one
 * {@code @Configuration}, and a worker never builds what only the manager needs, nor the manager
 * the worker's step.
 *
 * <pre>{@code
 * @Bean
 * @ConditionalOnBatchRole(BatchRole.WORKER)
 * Step importStep(AdharSteps steps, ...) { ... }   // the step a worker runs once per partition
 * }</pre>
 *
 * <p>The role is the pod's effective one: {@code adhar.batch.role} ({@code ADHAR_BATCH_ROLE}), with
 * {@code indexed} resolved through {@code JOB_COMPLETION_INDEX} (0 is the manager, any other index a
 * worker). A {@code single} pod plays every part, so it matches {@code MANAGER} and {@code WORKER}
 * alike: run outside the platform, a partitioned job runs its partitions in the one process.
 * {@code INDEXED} may not be named; it is not a part a bean plays.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnBatchRoleCondition.class)
public @interface ConditionalOnBatchRole {

    /**
     * @return the roles in which the bean exists
     */
    BatchRole[] value();
}
