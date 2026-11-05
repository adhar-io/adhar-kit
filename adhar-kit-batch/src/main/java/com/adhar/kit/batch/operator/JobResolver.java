package com.adhar.kit.batch.operator;

import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.ListableBeanFactory;

import java.util.Map;
import java.util.TreeSet;

/**
 * Finds a {@link Job} by name.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@FunctionalInterface
public interface JobResolver {

    /**
     * @param name a job's bean name or {@code Job#getName()}
     * @return the job
     * @throws IllegalArgumentException when there is no such job
     */
    Job resolve(String name);

    /**
     * Resolves jobs from the application's beans: by bean name first, then by job name.
     *
     * @param beanFactory the application's bean factory
     * @return the resolver
     */
    static JobResolver of(ListableBeanFactory beanFactory) {
        return name -> {
            if (beanFactory.containsBean(name) && beanFactory.isTypeMatch(name, Job.class)) {
                return beanFactory.getBean(name, Job.class);
            }
            Map<String, Job> jobs = beanFactory.getBeansOfType(Job.class);
            return jobs.values().stream()
                    .filter(job -> name.equals(job.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No Job named [" + name
                            + "]; jobs in this application: " + new TreeSet<>(jobs.keySet())));
        };
    }
}
