package com.adhar.kit.batch.repository;

import com.adhar.kit.batch.config.BatchProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.batch.jdbc.autoconfigure.BatchDataSource;
import org.springframework.boot.batch.jdbc.autoconfigure.BatchJdbcProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.MetaDataAccessException;

import javax.sql.DataSource;

/**
 * The JDBC JobRepository the kit manages: its layout, the schema migration, and Spring Batch's
 * table prefix. Active when Spring Boot's JDBC batch configuration is in charge (its
 * {@link BatchJdbcProperties} exist) and there is a DataSource; an application that builds its
 * own batch configuration ({@code @EnableBatchProcessing}) manages its schema itself.
 *
 * <p>The repository's DataSource is the one Spring Boot gives the JobRepository: the
 * {@link BatchDataSource @BatchDataSource} bean when there is one, else the primary DataSource.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean({DataSource.class, BatchJdbcProperties.class})
public class AdharBatchRepositoryConfiguration {

    /**
     * @param dataSource      the primary DataSource
     * @param batchDataSource the dedicated batch DataSource, if any
     * @param properties      {@code adhar.batch.*}
     * @return where the repository lives
     * @throws MetaDataAccessException when the database cannot be reached
     */
    @Bean
    @ConditionalOnMissingBean
    public BatchRepositoryLayout batchRepositoryLayout(DataSource dataSource,
                                                       @BatchDataSource ObjectProvider<DataSource> batchDataSource,
                                                       BatchProperties properties) throws MetaDataAccessException {
        return BatchRepositoryLayout.detect(batchDataSource.getIfAvailable(() -> dataSource),
                properties.getRepository());
    }

    /**
     * @param dataSource      the primary DataSource
     * @param batchDataSource the dedicated batch DataSource, if any
     * @param layout          where the repository lives
     * @param properties      {@code adhar.batch.*}
     * @return the migrator, run before the JobRepository is created
     */
    @Bean
    @ConditionalOnMissingBean
    public BatchSchemaMigrator batchSchemaMigrator(DataSource dataSource,
                                                   @BatchDataSource ObjectProvider<DataSource> batchDataSource,
                                                   BatchRepositoryLayout layout, BatchProperties properties) {
        return new BatchSchemaMigrator(batchDataSource.getIfAvailable(() -> dataSource), layout,
                properties.getRepository().isMigrate());
    }

    /**
     * @param layout resolved lazily, when Spring Boot's batch JDBC properties are bound
     * @return the post-processor pointing Spring Batch's table prefix at the kit's schema
     */
    @Bean
    public static BatchTablePrefixConfigurer batchTablePrefixConfigurer(ObjectProvider<BatchRepositoryLayout> layout) {
        return new BatchTablePrefixConfigurer(layout);
    }
}
