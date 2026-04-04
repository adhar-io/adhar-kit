package com.adhar.kit.persistence.api;

import com.adhar.kit.persistence.metrics.QueryStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Framework-agnostic Persistence Service API.
 *
 * <p>This interface provides a unified data access abstraction with support
 * for JPA, pagination, specifications, bulk operations, transaction control,
 * metrics, and entity lifecycle management.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see com.adhar.kit.persistence.PersistenceFacade
 */
public interface PersistenceService {

    // ---- Basic CRUD ----

    <T> T save(T entity);

    <T> List<T> saveAll(Iterable<T> entities);

    <T, ID> Optional<T> findById(Class<T> entityClass, ID id);

    <T> List<T> findAll(Class<T> entityClass);

    <T> List<T> query(Class<T> entityClass, String query);

    <T> List<T> query(Class<T> entityClass, String query, Object... params);

    <T> void delete(T entity);

    <T, ID> void deleteById(Class<T> entityClass, ID id);

    <T, ID> boolean existsById(Class<T> entityClass, ID id);

    <T> long count(Class<T> entityClass);

    <T> T executeInTransaction(Supplier<T> operation);

    void flush();

    // ---- Pagination ----

    <T> Page<T> findAll(Class<T> entityClass, int page, int size);

    <T> Page<T> findAll(Class<T> entityClass, int page, int size, String sortBy, boolean ascending);

    // ---- Specification queries ----

    <T> List<T> findAll(Class<T> entityClass, Specification<T> spec);

    <T> Page<T> findAll(Class<T> entityClass, Specification<T> spec, Pageable pageable);

    <T> long count(Class<T> entityClass, Specification<T> spec);

    // ---- Bulk operations ----

    <T> List<T> saveAllInBatch(List<T> entities, int batchSize);

    <T> int bulkUpdate(Class<T> entityClass, String jpql, Object... params);

    <T> int bulkDelete(Class<T> entityClass, String jpql, Object... params);

    // ---- Transaction control ----

    <T> T executeInTransaction(Supplier<T> operation, int isolationLevel);

    <T> T executeReadOnly(Supplier<T> operation);

    void executeInNewTransaction(Runnable operation);

    // ---- Metrics and tracing ----

    QueryStats getQueryStats();

    void resetQueryStats();

    // ---- Entity utilities ----

    <T> T refresh(T entity);

    <T> T merge(T entity);

    void detach(Object entity);

    boolean isManaged(Object entity);
}
