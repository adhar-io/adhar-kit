package com.adhar.kit.batch.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Refuses to start an application whose JobRepository keeps nothing.
 *
 * <p>Spring Boot falls back to an in-memory ({@link ResourcelessJobRepository}) repository when
 * there is no DataSource. A job then cannot be restarted from its last commit, a second run of
 * the same business date is not recognised as a duplicate, and no history survives the pod.
 * Unless {@code adhar.batch.repository.allow-in-memory=true} (tests), the startup fails with
 * exit code 2 and says how to fix it.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class ResourcelessRepositoryGuard implements SmartInitializingSingleton {

    private final ObjectProvider<JobRepository> jobRepository;
    private final boolean allowInMemory;

    /**
     * @param jobRepository the JobRepository, resolved when every singleton exists
     * @param allowInMemory {@code adhar.batch.repository.allow-in-memory}
     */
    public ResourcelessRepositoryGuard(ObjectProvider<JobRepository> jobRepository, boolean allowInMemory) {
        this.jobRepository = jobRepository;
        this.allowInMemory = allowInMemory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        JobRepository repository = jobRepository.getIfAvailable();
        if (repository == null || !isResourceless(repository)) {
            return;
        }
        if (allowInMemory) {
            log.warn("The JobRepository is in memory (adhar.batch.repository.allow-in-memory=true): runs cannot be "
                    + "restarted and no history survives the process. Use this for tests only.");
            return;
        }
        throw new BatchRepositoryException("The JobRepository is in memory: no DataSource is configured, so no run "
                + "could be restarted, no duplicate run would be recognised and no history would survive the pod. "
                + "Configure the application's database (spring.datasource.url, on the platform the Database "
                + "secret) so the JobRepository is JDBC. Tests may set adhar.batch.repository.allow-in-memory=true.");
    }

    /**
     * @param repository a JobRepository, possibly behind a proxy
     * @return whether it keeps its state in memory only
     */
    static boolean isResourceless(JobRepository repository) {
        return repository instanceof ResourcelessJobRepository
                || ResourcelessJobRepository.class.isAssignableFrom(AopUtils.getTargetClass(repository));
    }
}
