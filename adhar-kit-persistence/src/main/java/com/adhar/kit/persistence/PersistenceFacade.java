package com.adhar.kit.persistence;

import com.adhar.kit.persistence.api.PersistenceService;
import com.adhar.kit.persistence.metrics.QueryStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Universal Persistence facade for data access.
 *
 * <p>The facade is a thin delegator: all operations forward to the
 * {@link PersistenceService} set via {@link #setDelegate}. In a Spring Boot
 * environment the {@link com.adhar.kit.persistence.spring.SpringPersistenceAdapter}
 * (a real JPA implementation) is wired in by auto-configuration.</p>
 *
 * <p><b>No configured delegate = loud failure.</b> Every operation throws
 * {@link IllegalStateException} when no {@link PersistenceService} has been
 * configured, rather than pretending to succeed. Earlier versions silently
 * no-opped ({@code save()} returned the entity unpersisted), which is silent
 * data loss - an unacceptable failure mode for a persistence API.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class PersistenceFacade implements PersistenceService {

    private static volatile PersistenceFacade instance;

    private volatile PersistenceService delegate;

    private PersistenceFacade() {
        log.info("Initialized PersistenceFacade (no delegate yet - operations fail until one is set)");
    }

    public static PersistenceFacade getInstance() {
        if (instance == null) {
            synchronized (PersistenceFacade.class) {
                if (instance == null) {
                    instance = new PersistenceFacade();
                }
            }
        }
        return instance;
    }

    /**
     * Sets the backing {@link PersistenceService} implementation (e.g. the
     * Spring JPA adapter). Passing {@code null} reverts the facade to its
     * unconfigured, fail-loud state.
     *
     * @param delegate the persistence implementation, or {@code null}
     */
    public void setDelegate(PersistenceService delegate) {
        this.delegate = delegate;
        log.info("PersistenceFacade delegate {}", delegate != null
                ? "set to " + delegate.getClass().getSimpleName() : "cleared");
    }

    /**
     * Whether a real {@link PersistenceService} is configured.
     *
     * @return true if operations will be delegated
     */
    public boolean isConfigured() {
        return delegate != null;
    }

    private PersistenceService required() {
        PersistenceService current = delegate;
        if (current == null) {
            throw new IllegalStateException("No PersistenceService configured - refusing to fake "
                    + "persistence operations. On Spring Boot ensure a JPA EntityManagerFactory is "
                    + "configured so the SpringPersistenceAdapter is auto-wired, or call "
                    + "PersistenceFacade.setDelegate(...) with your implementation.");
        }
        return current;
    }

    // ---- Basic CRUD ----

    @Override
    public <T> T save(T entity) {
        return required().save(entity);
    }

    @Override
    public <T> List<T> saveAll(Iterable<T> entities) {
        return required().saveAll(entities);
    }

    @Override
    public <T, ID> Optional<T> findById(Class<T> entityClass, ID id) {
        return required().findById(entityClass, id);
    }

    @Override
    public <T> List<T> findAll(Class<T> entityClass) {
        return required().findAll(entityClass);
    }

    @Override
    public <T> List<T> query(Class<T> entityClass, String query) {
        return required().query(entityClass, query);
    }

    @Override
    public <T> List<T> query(Class<T> entityClass, String query, Object... params) {
        return required().query(entityClass, query, params);
    }

    @Override
    public <T> void delete(T entity) {
        required().delete(entity);
    }

    @Override
    public <T, ID> void deleteById(Class<T> entityClass, ID id) {
        required().deleteById(entityClass, id);
    }

    @Override
    public <T, ID> boolean existsById(Class<T> entityClass, ID id) {
        return required().existsById(entityClass, id);
    }

    @Override
    public <T> long count(Class<T> entityClass) {
        return required().count(entityClass);
    }

    @Override
    public <T> T executeInTransaction(Supplier<T> operation) {
        return required().executeInTransaction(operation);
    }

    @Override
    public void flush() {
        required().flush();
    }

    // ---- Pagination ----

    @Override
    public <T> Page<T> findAll(Class<T> entityClass, int page, int size) {
        return required().findAll(entityClass, page, size);
    }

    @Override
    public <T> Page<T> findAll(Class<T> entityClass, int page, int size, String sortBy, boolean ascending) {
        return required().findAll(entityClass, page, size, sortBy, ascending);
    }

    // ---- Specification queries ----

    @Override
    public <T> List<T> findAll(Class<T> entityClass, Specification<T> spec) {
        return required().findAll(entityClass, spec);
    }

    @Override
    public <T> Page<T> findAll(Class<T> entityClass, Specification<T> spec, Pageable pageable) {
        return required().findAll(entityClass, spec, pageable);
    }

    @Override
    public <T> long count(Class<T> entityClass, Specification<T> spec) {
        return required().count(entityClass, spec);
    }

    // ---- Bulk operations ----

    @Override
    public <T> List<T> saveAllInBatch(List<T> entities, int batchSize) {
        return required().saveAllInBatch(entities, batchSize);
    }

    @Override
    public <T> int bulkUpdate(Class<T> entityClass, String jpql, Object... params) {
        return required().bulkUpdate(entityClass, jpql, params);
    }

    @Override
    public <T> int bulkDelete(Class<T> entityClass, String jpql, Object... params) {
        return required().bulkDelete(entityClass, jpql, params);
    }

    // ---- Transaction control ----

    @Override
    public <T> T executeInTransaction(Supplier<T> operation, int isolationLevel) {
        return required().executeInTransaction(operation, isolationLevel);
    }

    @Override
    public <T> T executeReadOnly(Supplier<T> operation) {
        return required().executeReadOnly(operation);
    }

    @Override
    public void executeInNewTransaction(Runnable operation) {
        required().executeInNewTransaction(operation);
    }

    // ---- Metrics and tracing ----

    @Override
    public QueryStats getQueryStats() {
        PersistenceService current = delegate;
        return current != null ? current.getQueryStats() : QueryStats.empty();
    }

    @Override
    public void resetQueryStats() {
        PersistenceService current = delegate;
        if (current != null) {
            current.resetQueryStats();
        }
    }

    // ---- Entity utilities ----

    @Override
    public <T> T refresh(T entity) {
        return required().refresh(entity);
    }

    @Override
    public <T> T merge(T entity) {
        return required().merge(entity);
    }

    @Override
    public void detach(Object entity) {
        required().detach(entity);
    }

    @Override
    public boolean isManaged(Object entity) {
        return required().isManaged(entity);
    }
}
