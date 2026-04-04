package com.adhar.kit.persistence.spring;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import com.adhar.kit.persistence.api.PersistenceService;
import com.adhar.kit.persistence.config.PersistenceProperties;
import com.adhar.kit.persistence.metrics.PersistenceMetricsCollector;
import com.adhar.kit.persistence.metrics.QueryStats;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Spring Boot-specific persistence adapter using JPA and Spring Data.
 *
 * <p>This adapter provides enterprise-grade database access for Spring Boot applications,
 * leveraging Spring Data JPA, Hibernate ORM, and Spring's transaction management.</p>
 *
 * <p>All query and transaction operations are instrumented via
 * {@link PersistenceMetricsCollector} when metrics are enabled.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see PersistenceService
 * @see FrameworkAdapter
 * @see EntityManager
 */
@Service
@ConditionalOnClass(name = "org.springframework.boot.SpringApplication")
@Transactional
public class SpringPersistenceAdapter implements FrameworkAdapter<PersistenceService>, PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(SpringPersistenceAdapter.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final PersistenceMetricsCollector metricsCollector;
    private final PlatformTransactionManager transactionManager;
    private final PersistenceProperties properties;

    public SpringPersistenceAdapter(PersistenceMetricsCollector metricsCollector,
                                    PlatformTransactionManager transactionManager,
                                    PersistenceProperties properties) {
        this.metricsCollector = metricsCollector;
        this.transactionManager = transactionManager;
        this.properties = properties;
    }

    @Override
    public Framework getSupportedFramework() {
        return Framework.SPRING_BOOT;
    }

    @Override
    public PersistenceService getService() {
        return this;
    }

    // ---- Basic CRUD ----

    @Override
    public <T> T save(T entity) {
        log.debug("Saving entity: {}", entity.getClass().getSimpleName());
        return metricsCollector.recordQueryExecution(() -> entityManager.merge(entity));
    }

    @Override
    public <T> List<T> saveAll(Iterable<T> entities) {
        log.debug("Batch saving entities");
        return metricsCollector.recordQueryExecution(() -> {
            List<T> result = new ArrayList<>();
            int count = 0;
            int batchSize = properties.getDefaultBatchSize();
            for (T entity : entities) {
                result.add(entityManager.merge(entity));
                count++;
                if (count % batchSize == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
            return result;
        });
    }

    @Override
    public <T, ID> Optional<T> findById(Class<T> entityClass, ID id) {
        log.debug("Finding {} by ID: {}", entityClass.getSimpleName(), id);
        return metricsCollector.recordQueryExecution(() ->
                Optional.ofNullable(entityManager.find(entityClass, id)));
    }

    @Override
    public <T> List<T> findAll(Class<T> entityClass) {
        log.debug("Finding all {}", entityClass.getSimpleName());
        return metricsCollector.recordQueryExecution(() -> {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> query = cb.createQuery(entityClass);
            query.select(query.from(entityClass));
            return entityManager.createQuery(query).getResultList();
        });
    }

    @Override
    public <T> List<T> query(Class<T> entityClass, String query) {
        log.debug("Executing JPQL query for {}: {}", entityClass.getSimpleName(), query);
        return metricsCollector.recordQueryExecution(() ->
                entityManager.createQuery(query, entityClass).getResultList());
    }

    @Override
    public <T> List<T> query(Class<T> entityClass, String query, Object... params) {
        log.debug("Executing JPQL query for {}: {}", entityClass.getSimpleName(), query);
        return metricsCollector.recordQueryExecution(() -> {
            TypedQuery<T> typedQuery = entityManager.createQuery(query, entityClass);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    typedQuery.setParameter(i + 1, params[i]);
                }
            }
            return typedQuery.getResultList();
        });
    }

    /**
     * Executes a JPQL query with named parameters.
     */
    public <T> List<T> query(Class<T> entityClass, String jpql, Map<String, Object> parameters) {
        log.debug("Executing JPQL query for {}: {}", entityClass.getSimpleName(), jpql);
        return metricsCollector.recordQueryExecution(() -> {
            TypedQuery<T> query = entityManager.createQuery(jpql, entityClass);
            if (parameters != null) {
                parameters.forEach(query::setParameter);
            }
            return query.getResultList();
        });
    }

    @Override
    public <T> void delete(T entity) {
        log.debug("Deleting entity: {}", entity.getClass().getSimpleName());
        metricsCollector.recordQueryExecution(() -> {
            if (entityManager.contains(entity)) {
                entityManager.remove(entity);
            } else {
                entityManager.remove(entityManager.merge(entity));
            }
        });
    }

    @Override
    public <T, ID> void deleteById(Class<T> entityClass, ID id) {
        log.debug("Deleting {} by ID: {}", entityClass.getSimpleName(), id);
        metricsCollector.recordQueryExecution(() -> {
            T entity = entityManager.find(entityClass, id);
            if (entity != null) {
                entityManager.remove(entity);
            }
        });
    }

    @Override
    public <T, ID> boolean existsById(Class<T> entityClass, ID id) {
        log.debug("Checking if {} exists with ID: {}", entityClass.getSimpleName(), id);
        return metricsCollector.recordQueryExecution(() ->
                entityManager.find(entityClass, id) != null);
    }

    @Override
    public <T> long count(Class<T> entityClass) {
        log.debug("Counting entities of type: {}", entityClass.getSimpleName());
        return metricsCollector.recordQueryExecution(() -> {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            cq.select(cb.count(cq.from(entityClass)));
            return entityManager.createQuery(cq).getSingleResult();
        });
    }

    @Override
    @Transactional
    public <T> T executeInTransaction(Supplier<T> operation) {
        log.debug("Executing operation in transaction");
        long start = System.nanoTime();
        try {
            T result = operation.get();
            metricsCollector.recordTransaction(System.nanoTime() - start);
            return result;
        } catch (RuntimeException ex) {
            metricsCollector.recordTransaction(System.nanoTime() - start);
            metricsCollector.recordError();
            throw ex;
        }
    }

    @Override
    public void flush() {
        log.debug("Flushing persistence context");
        entityManager.flush();
    }

    // ---- Pagination ----

    @Override
    public <T> Page<T> findAll(Class<T> entityClass, int page, int size) {
        log.debug("Finding {} with pagination: page={}, size={}", entityClass.getSimpleName(), page, size);
        return findAllPaged(entityClass, PageRequest.of(page, size), null);
    }

    @Override
    public <T> Page<T> findAll(Class<T> entityClass, int page, int size, String sortBy, boolean ascending) {
        log.debug("Finding {} with pagination and sort: page={}, size={}, sortBy={}, asc={}",
                entityClass.getSimpleName(), page, size, sortBy, ascending);
        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        return findAllPaged(entityClass, PageRequest.of(page, size, sort), null);
    }

    /**
     * Finds all entities with pagination support (existing convenience method).
     */
    public <T> Page<T> findAll(Class<T> entityClass, Pageable pageable) {
        return findAllPaged(entityClass, pageable, null);
    }

    // ---- Specification queries ----

    @Override
    public <T> List<T> findAll(Class<T> entityClass, Specification<T> spec) {
        log.debug("Finding {} with specification", entityClass.getSimpleName());
        return metricsCollector.recordQueryExecution(() -> {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> query = cb.createQuery(entityClass);
            Root<T> root = query.from(entityClass);
            query.select(root);

            Predicate predicate = spec.toPredicate(root, query, cb);
            if (predicate != null) {
                query.where(predicate);
            }

            return entityManager.createQuery(query).getResultList();
        });
    }

    @Override
    public <T> Page<T> findAll(Class<T> entityClass, Specification<T> spec, Pageable pageable) {
        log.debug("Finding {} with specification and pagination", entityClass.getSimpleName());
        return findAllPaged(entityClass, pageable, spec);
    }

    @Override
    public <T> long count(Class<T> entityClass, Specification<T> spec) {
        log.debug("Counting {} with specification", entityClass.getSimpleName());
        return metricsCollector.recordQueryExecution(() -> {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
            Root<T> root = countQuery.from(entityClass);
            countQuery.select(cb.count(root));

            Predicate predicate = spec.toPredicate(root, countQuery, cb);
            if (predicate != null) {
                countQuery.where(predicate);
            }

            return entityManager.createQuery(countQuery).getSingleResult();
        });
    }

    // ---- Bulk operations ----

    @Override
    public <T> List<T> saveAllInBatch(List<T> entities, int batchSize) {
        log.debug("Batch saving {} entities with batchSize={}", entities.size(), batchSize);
        return metricsCollector.recordQueryExecution(() -> {
            List<T> result = new ArrayList<>();
            for (int i = 0; i < entities.size(); i++) {
                result.add(entityManager.merge(entities.get(i)));
                if ((i + 1) % batchSize == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
            if (entities.size() % batchSize != 0) {
                entityManager.flush();
            }
            return result;
        });
    }

    @Override
    public <T> int bulkUpdate(Class<T> entityClass, String jpql, Object... params) {
        log.debug("Bulk update for {}: {}", entityClass.getSimpleName(), jpql);
        return metricsCollector.recordQueryExecution(() -> {
            var query = entityManager.createQuery(jpql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    query.setParameter(i + 1, params[i]);
                }
            }
            return query.executeUpdate();
        });
    }

    @Override
    public <T> int bulkDelete(Class<T> entityClass, String jpql, Object... params) {
        log.debug("Bulk delete for {}: {}", entityClass.getSimpleName(), jpql);
        return bulkUpdate(entityClass, jpql, params);
    }

    // ---- Transaction control ----

    @Override
    public <T> T executeInTransaction(Supplier<T> operation, int isolationLevel) {
        log.debug("Executing in transaction with isolation level {}", isolationLevel);
        long start = System.nanoTime();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(isolationLevel);
        try {
            T result = template.execute(status -> operation.get());
            metricsCollector.recordTransaction(System.nanoTime() - start);
            return result;
        } catch (RuntimeException ex) {
            metricsCollector.recordTransaction(System.nanoTime() - start);
            metricsCollector.recordError();
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public <T> T executeReadOnly(Supplier<T> operation) {
        log.debug("Executing read-only operation");
        long start = System.nanoTime();
        try {
            T result = operation.get();
            metricsCollector.recordTransaction(System.nanoTime() - start);
            return result;
        } catch (RuntimeException ex) {
            metricsCollector.recordTransaction(System.nanoTime() - start);
            metricsCollector.recordError();
            throw ex;
        }
    }

    @Override
    public void executeInNewTransaction(Runnable operation) {
        log.debug("Executing in new transaction");
        long start = System.nanoTime();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            template.executeWithoutResult(status -> operation.run());
            metricsCollector.recordTransaction(System.nanoTime() - start);
        } catch (RuntimeException ex) {
            metricsCollector.recordTransaction(System.nanoTime() - start);
            metricsCollector.recordError();
            throw ex;
        }
    }

    // ---- Metrics and tracing ----

    @Override
    public QueryStats getQueryStats() {
        return metricsCollector.getStats();
    }

    @Override
    public void resetQueryStats() {
        metricsCollector.reset();
    }

    // ---- Entity utilities ----

    @Override
    public <T> T refresh(T entity) {
        log.debug("Refreshing entity: {}", entity.getClass().getSimpleName());
        entityManager.refresh(entity);
        return entity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T merge(T entity) {
        log.debug("Merging entity: {}", entity.getClass().getSimpleName());
        return entityManager.merge(entity);
    }

    @Override
    public void detach(Object entity) {
        log.debug("Detaching entity: {}", entity.getClass().getSimpleName());
        entityManager.detach(entity);
    }

    @Override
    public boolean isManaged(Object entity) {
        return entityManager.contains(entity);
    }

    // ---- Accessors ----

    /**
     * Gets the JPA CriteriaBuilder for type-safe queries.
     */
    public CriteriaBuilder getCriteriaBuilder() {
        return entityManager.getCriteriaBuilder();
    }

    /**
     * Gets the underlying JPA EntityManager.
     */
    public EntityManager getEntityManager() {
        return entityManager;
    }

    // ---- Internal helpers ----

    private <T> Page<T> findAllPaged(Class<T> entityClass, Pageable pageable, Specification<T> spec) {
        return metricsCollector.recordQueryExecution(() -> {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> query = cb.createQuery(entityClass);
            Root<T> root = query.from(entityClass);
            query.select(root);

            if (spec != null) {
                Predicate predicate = spec.toPredicate(root, query, cb);
                if (predicate != null) {
                    query.where(predicate);
                }
            }

            // Apply sorting
            if (pageable.getSort().isSorted()) {
                List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
                pageable.getSort().forEach(order -> {
                    if (order.isAscending()) {
                        orders.add(cb.asc(root.get(order.getProperty())));
                    } else {
                        orders.add(cb.desc(root.get(order.getProperty())));
                    }
                });
                query.orderBy(orders);
            }

            TypedQuery<T> typedQuery = entityManager.createQuery(query);
            typedQuery.setFirstResult((int) pageable.getOffset());
            typedQuery.setMaxResults(pageable.getPageSize());

            List<T> content = typedQuery.getResultList();

            // Count query
            CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
            Root<T> countRoot = countQuery.from(entityClass);
            countQuery.select(cb.count(countRoot));

            if (spec != null) {
                Predicate countPredicate = spec.toPredicate(countRoot, countQuery, cb);
                if (countPredicate != null) {
                    countQuery.where(countPredicate);
                }
            }

            Long total = entityManager.createQuery(countQuery).getSingleResult();
            return new PageImpl<>(content, pageable, total);
        });
    }
}
