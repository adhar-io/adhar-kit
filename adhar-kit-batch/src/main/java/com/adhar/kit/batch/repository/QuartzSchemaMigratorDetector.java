package com.adhar.kit.batch.repository;

import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;

import java.util.Set;

/**
 * Tells Spring Boot that {@link QuartzSchemaMigrator} initializes a database.
 *
 * <p>Spring Boot's Quartz scheduler declares that it depends on database initialization, so it is
 * created only once the Quartz tables exist, as the JobRepository waits for
 * {@link BatchSchemaMigratorDetector}'s migration. Registered in {@code META-INF/spring.factories}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class QuartzSchemaMigratorDetector extends AbstractBeansOfTypeDatabaseInitializerDetector {

    @Override
    protected Set<Class<?>> getDatabaseInitializerBeanTypes() {
        return Set.of(QuartzSchemaMigrator.class);
    }
}
