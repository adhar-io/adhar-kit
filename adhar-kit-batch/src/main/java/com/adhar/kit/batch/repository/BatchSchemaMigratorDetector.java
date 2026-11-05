package com.adhar.kit.batch.repository;

import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;

import java.util.Set;

/**
 * Tells Spring Boot that {@link BatchSchemaMigrator} initializes a database.
 *
 * <p>Spring Boot then makes every bean that depends on database initialization (the
 * JobRepository, JPA's EntityManagerFactory, {@code JdbcTemplate}...) wait for the migration,
 * so no job reads a table that does not exist yet. Registered in {@code META-INF/spring.factories}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class BatchSchemaMigratorDetector extends AbstractBeansOfTypeDatabaseInitializerDetector {

    @Override
    protected Set<Class<?>> getDatabaseInitializerBeanTypes() {
        return Set.of(BatchSchemaMigrator.class);
    }
}
