package com.adhar.kit.batch.reader;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;

import java.util.Map;

/**
 * Utility for creating JPA-based {@link JpaPagingItemReader} instances
 * with sensible defaults for paginated database reads.
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * var reader = JpaItemReaderBuilder.jpaPagingReader(Order.class, emf)
 *         .queryString("SELECT o FROM Order o WHERE o.status = :status")
 *         .parameterValues(Map.of("status", "PENDING"))
 *         .pageSize(100)
 *         .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class JpaItemReaderBuilder {

    private JpaItemReaderBuilder() {
        // utility class
    }

    /**
     * Creates a pre-configured {@link JpaPagingItemReaderBuilder} for the given entity class.
     *
     * <p>The builder is initialized with:</p>
     * <ul>
     *   <li>The entity manager factory for JPA access</li>
     *   <li>A reader name derived from the entity class name</li>
     *   <li>A default page size of 50</li>
     * </ul>
     *
     * @param entityClass the JPA entity class to read
     * @param emf         the entity manager factory
     * @param <T>         the entity type
     * @return a pre-configured builder ready for further customization
     */
    public static <T> JpaPagingItemReaderBuilder<T> jpaPagingReader(Class<T> entityClass, EntityManagerFactory emf) {
        return new JpaPagingItemReaderBuilder<T>()
                .name(entityClass.getSimpleName() + "Reader")
                .entityManagerFactory(emf)
                .pageSize(50);
    }

    /**
     * Creates a fully configured {@link JpaPagingItemReader} with the given JPQL query.
     *
     * @param entityClass the JPA entity class to read
     * @param emf         the entity manager factory
     * @param queryString the JPQL query string
     * @param pageSize    the number of items per page
     * @return a configured JpaPagingItemReader
     * @throws Exception if the reader cannot be initialized
     */
    public static <T> JpaPagingItemReader<T> jpaPagingReader(
            Class<T> entityClass,
            EntityManagerFactory emf,
            String queryString,
            int pageSize) throws Exception {
        return jpaPagingReader(entityClass, emf)
                .queryString(queryString)
                .pageSize(pageSize)
                .build();
    }

    /**
     * Creates a fully configured {@link JpaPagingItemReader} with query parameters.
     *
     * @param entityClass     the JPA entity class to read
     * @param emf             the entity manager factory
     * @param queryString     the JPQL query string
     * @param parameterValues the query parameter values
     * @param pageSize        the number of items per page
     * @return a configured JpaPagingItemReader
     * @throws Exception if the reader cannot be initialized
     */
    public static <T> JpaPagingItemReader<T> jpaPagingReader(
            Class<T> entityClass,
            EntityManagerFactory emf,
            String queryString,
            Map<String, Object> parameterValues,
            int pageSize) throws Exception {
        return jpaPagingReader(entityClass, emf)
                .queryString(queryString)
                .parameterValues(parameterValues)
                .pageSize(pageSize)
                .build();
    }
}
