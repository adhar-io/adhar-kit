package com.adhar.kit.batch.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.batch.jdbc.autoconfigure.BatchJdbcProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.core.Ordered;

/**
 * Points Spring Batch's table prefix ({@code spring.batch.jdbc.table-prefix}) at the kit's
 * schema once Spring Boot has bound {@link BatchJdbcProperties}.
 *
 * <p>Spring Boot reads the prefix when it creates the JobRepository, so it is set on the bound
 * properties object, after binding and before the JobRepository exists. On a managed
 * (PostgreSQL) layout the configured prefix must be unset or equal to
 * {@code <schema>.<table-prefix>}: a different value would make the kit migrate one set of
 * tables while Spring Batch reads another, a repository that silently forgets every run. That
 * fails the startup with exit code 2 instead.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class BatchTablePrefixConfigurer implements BeanPostProcessor, Ordered {

    private final ObjectProvider<BatchRepositoryLayout> layout;

    /**
     * @param layout resolved lazily, when the properties are post-processed, so the DataSource
     *               is created normally and not as a dependency of a post-processor
     */
    public BatchTablePrefixConfigurer(ObjectProvider<BatchRepositoryLayout> layout) {
        this.layout = layout;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof BatchJdbcProperties properties) {
            configure(properties, layout.getObject());
        }
        return bean;
    }

    /**
     * Applies the layout to Spring Boot's batch JDBC properties.
     *
     * @param properties the bound {@code spring.batch.jdbc.*} properties
     * @param layout     where the repository lives
     * @throws BatchRepositoryException when the configured prefix contradicts the managed schema
     */
    static void configure(BatchJdbcProperties properties, BatchRepositoryLayout layout) {
        if (!layout.managed()) {
            return;
        }
        if (properties.getInitializeSchema() == DatabaseInitializationMode.ALWAYS) {
            throw new BatchRepositoryException("spring.batch.jdbc.initialize-schema=always would run Spring Boot's own "
                    + "schema script outside the kit's schema '" + layout.schema() + "'. On PostgreSQL the kit "
                    + "migrates the JobRepository schema itself: remove spring.batch.jdbc.initialize-schema.");
        }
        String configured = properties.getTablePrefix();
        if (!layout.acceptsSpringBatchPrefix(configured)) {
            throw new BatchRepositoryException("spring.batch.jdbc.table-prefix=" + configured
                    + " contradicts the kit-managed JobRepository schema, whose prefix is "
                    + layout.qualifiedTablePrefix() + ". Remove spring.batch.jdbc.table-prefix and set "
                    + "adhar.batch.repository.schema / adhar.batch.repository.table-prefix instead.");
        }
        properties.setTablePrefix(layout.qualifiedTablePrefix());
        log.debug("Spring Batch table prefix set to {}", layout.qualifiedTablePrefix());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
