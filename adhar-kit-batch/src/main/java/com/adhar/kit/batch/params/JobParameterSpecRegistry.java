package com.adhar.kit.batch.params;

import com.adhar.kit.batch.annotation.AdharBatchJob;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The declared parameters of every job in the application: {@link AdharBatchJob} on the job's
 * bean method or class, and {@link JobParameterSpec} beans for jobs whose parameters are computed.
 * Collected once every singleton exists, so a broken declaration fails the startup.
 *
 * <p>A spec is found by the job's name or its bean name. A job with no declaration accepts no
 * parameters ({@link JobParameterSpec#none(String)}).</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class JobParameterSpecRegistry implements SmartInitializingSingleton {

    private final ListableBeanFactory beanFactory;
    private final Map<String, JobParameterSpec> specs = new LinkedHashMap<>();

    /**
     * @param beanFactory the application's beans
     */
    public JobParameterSpecRegistry(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * @param specs the specs
     * @return a registry holding exactly these (tests, tools)
     */
    public static JobParameterSpecRegistry of(Collection<JobParameterSpec> specs) {
        JobParameterSpecRegistry registry = new JobParameterSpecRegistry(null);
        specs.forEach(spec -> registry.register(spec.jobName(), spec, "a JobParameterSpec"));
        return registry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (Map.Entry<String, JobParameterSpec> bean : beanFactory.getBeansOfType(JobParameterSpec.class).entrySet()) {
            register(bean.getValue().jobName(), bean.getValue(), "bean " + bean.getKey());
        }
        for (String beanName : beanFactory.getBeanNamesForType(Job.class, true, false)) {
            AdharBatchJob annotation = beanFactory.findAnnotationOnBean(beanName, AdharBatchJob.class);
            if (annotation == null) {
                continue;
            }
            String jobName = beanFactory.getBean(beanName, Job.class).getName();
            if (!annotation.name().isEmpty() && !annotation.name().equals(jobName)) {
                throw new IllegalStateException("@AdharBatchJob on bean " + beanName + " names the job ["
                        + annotation.name() + "], but the job is named [" + jobName + "]");
            }
            JobParameterSpec spec = JobParameterSpec.of(jobName, annotation);
            register(jobName, spec, "@AdharBatchJob on bean " + beanName);
            if (!beanName.equals(jobName)) {
                specs.putIfAbsent(beanName, spec);
            }
        }
    }

    private void register(String name, JobParameterSpec spec, String origin) {
        if (specs.putIfAbsent(name, spec) != null) {
            throw new IllegalStateException("Job [" + name + "] has two parameter declarations; the second is "
                    + origin);
        }
    }

    /**
     * @param jobName a job's name or bean name
     * @return its declaration, empty when it has none
     */
    public Optional<JobParameterSpec> find(String jobName) {
        return Optional.ofNullable(specs.get(jobName));
    }

    /**
     * @param jobName a job's name
     * @return its declaration, or one accepting no parameters
     */
    public JobParameterSpec spec(String jobName) {
        return find(jobName).orElseGet(() -> JobParameterSpec.none(jobName));
    }

    /**
     * @return every declaration by job name (and by bean name where it differs)
     */
    public Map<String, JobParameterSpec> all() {
        return Map.copyOf(specs);
    }
}
