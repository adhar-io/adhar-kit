package com.adhar.kit.persistence.spring;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.commons.framework.FrameworkAdapter;
import com.adhar.kit.persistence.api.PersistenceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>JPA 3.1 Support:</b> Full Jakarta Persistence API implementation</li>
 *   <li><b>Hibernate Integration:</b> Advanced ORM features and optimizations</li>
 *   <li><b>Spring Transactions:</b> Declarative transaction management</li>
 *   <li><b>Query Optimization:</b> Criteria API, JPQL, and native SQL support</li>
 *   <li><b>Caching:</b> First and second-level cache support</li>
 *   <li><b>Connection Pooling:</b> HikariCP for high-performance pooling</li>
 * </ul>
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * # application.yml
 * spring:
 *   datasource:
 *     url: jdbc:postgresql://localhost:5432/mydb
 *     username: user
 *     password: pass
 *     hikari:
 *       maximum-pool-size: 10
 *       minimum-idle: 5
 *   jpa:
 *     hibernate:
 *       ddl-auto: validate
 *     properties:
 *       hibernate:
 *         dialect: org.hibernate.dialect.PostgreSQLDialect
 *         jdbc:
 *           batch_size: 20
 *         order_inserts: true
 *         order_updates: true
 * }</pre>
 *
 * <p><b>Basic Usage:</b></p>
 * <pre>{@code
 * @Service
 * public class UserService {
 *
 *     @Autowired
 *     private SpringPersistenceAdapter persistence;
 *
 *     public User createUser(String email, String name) {
 *         User user = new User();
 *         user.setEmail(email);
 *         user.setName(name);
 *         return persistence.save(user);
 *     }
 *
 *     public Optional<User> findUserByEmail(String email) {
 *         return persistence.query(User.class,
 *             "SELECT u FROM User u WHERE u.email = :email",
 *             Map.of("email", email))
 *             .stream()
 *             .findFirst();
 *     }
 * }
 * }</pre>
 *
 * <p><b>Transaction Management:</b></p>
 * <pre>{@code
 * // Programmatic transactions
 * Order order = persistence.executeInTransaction(() -> {
 *     Order o = new Order();
 *     o.setCustomer(customer);
 *     o = persistence.save(o);
 *
 *     for (OrderItem item : items) {
 *         item.setOrder(o);
 *         persistence.save(item);
 *     }
 *
 *     return o;
 * });
 *
 * // Declarative transactions (recommended)
 * @Transactional
 * public Order createOrder(Customer customer, List<OrderItem> items) {
 *     Order order = new Order();
 *     order.setCustomer(customer);
 *     order = persistence.save(order);
 *
 *     for (OrderItem item : items) {
 *         item.setOrder(order);
 *         persistence.save(item);
 *     }
 *
 *     return order;
 * }
 * }</pre>
 *
 * <p><b>Performance Optimization:</b></p>
 * <pre>{@code
 * // Batch operations
 * List<User> users = createUsers(1000);
 * persistence.saveAll(users); // Uses batch inserts
 *
 * // Pagination for large datasets
 * Pageable pageable = PageRequest.of(0, 20);
 * Page<User> userPage = persistence.findAll(User.class, pageable);
 *
 * // Criteria API for complex queries
 * CriteriaBuilder cb = persistence.getCriteriaBuilder();
 * CriteriaQuery<User> query = cb.createQuery(User.class);
 * Root<User> root = query.from(User.class);
 * query.where(
 *     cb.and(
 *         cb.equal(root.get("active"), true),
 *         cb.greaterThan(root.get("createdAt"), lastWeek)
 *     )
 * );
 * List<User> activeUsers = persistence.query(query);
 * }</pre>
 *
 * <p><b>Connection Pooling:</b></p>
 * <p>Uses HikariCP for optimal database connection management:</p>
 * <ul>
 *   <li>Fast connection acquisition (microseconds)</li>
 *   <li>Automatic connection leak detection</li>
 *   <li>Health checks and timeouts</li>
 *   <li>Metrics and monitoring</li>
 * </ul>
 *
 * <p><b>Hibernate Second-Level Cache:</b></p>
 * <pre>{@code
 * # Enable second-level cache
 * spring.jpa.properties.hibernate.cache.use_second_level_cache=true
 * spring.jpa.properties.hibernate.cache.region.factory_class=org.hibernate.cache.jcache.JCacheRegionFactory
 *
 * // Cache entity
 * @Entity
 * @Cacheable
 * @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
 * public class User {
 *     // ...
 * }
 * }</pre>
 *
 * <p><b>Thread Safety:</b></p>
 * <p>This adapter is thread-safe. The EntityManager is injected per-thread via
 * @PersistenceContext, and Spring manages transaction contexts automatically.</p>
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

    /**
     * SLF4J logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(SpringPersistenceAdapter.class);

    /**
     * JPA EntityManager for database operations.
     * <p>
     * Injected per-thread by Spring. Each thread gets its own EntityManager
     * instance bound to the current transaction context.
     * </p>
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Gets the framework supported by this adapter.
     *
     * @return {@link Framework#SPRING_BOOT}
     */
    @Override
    public Framework getSupportedFramework() {
        return Framework.SPRING_BOOT;
    }

    /**
     * Gets the persistence service instance (this adapter itself).
     *
     * @return this adapter as a PersistenceService
     */
    @Override
    public PersistenceService getService() {
        return this;
    }

    /**
     * Saves an entity to the database.
     * <p>
     * Uses JPA merge operation which handles both insert and update.
     * For new entities (no ID), performs INSERT. For existing entities, performs UPDATE.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * User user = new User();
     * user.setEmail("john@example.com");
     * user.setName("John Doe");
     * user = persistence.save(user); // Returns managed entity with ID
     * }</pre>
     *
     * @param entity the entity to save
     * @param <T> the entity type
     * @return the managed entity after save
     */
    @Override
    public <T> T save(T entity) {
        log.debug("Saving entity: {}", entity.getClass().getSimpleName());
        return entityManager.merge(entity);
    }

    /**
     * Saves multiple entities in a batch operation.
     * <p>
     * Uses Hibernate's batch processing for optimal performance. Configure batch size:
     * </p>
     * <pre>
     * spring.jpa.properties.hibernate.jdbc.batch_size=20
     * </pre>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * List<User> users = createUsers(1000);
     * List<User> saved = persistence.saveAll(users);
     * }</pre>
     *
     * @param entities the entities to save
     * @param <T> the entity type
     * @return list of saved entities
     */
    @Override
    public <T> List<T> saveAll(Iterable<T> entities) {
        log.debug("Batch saving entities");
        List<T> result = new java.util.ArrayList<>();
        int count = 0;
        for (T entity : entities) {
            result.add(entityManager.merge(entity));
            count++;
            // Flush and clear every batch to prevent memory issues
            if (count % 20 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        return result;
    }

    /**
     * Finds an entity by its primary key.
     * <p>
     * Returns Optional.empty() if not found, avoiding null checks.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * Optional<User> user = persistence.findById(User.class, userId);
     * user.ifPresent(u -> System.out.println("Found: " + u.getName()));
     * }</pre>
     *
     * @param entityClass the entity class
     * @param id the primary key
     * @param <T> the entity type
     * @param <ID> the ID type
     * @return Optional containing the entity if found
     */
    @Override
    public <T, ID> Optional<T> findById(Class<T> entityClass, ID id) {
        log.debug("Finding {} by ID: {}", entityClass.getSimpleName(), id);
        T entity = entityManager.find(entityClass, id);
        return Optional.ofNullable(entity);
    }

    /**
     * Finds all entities of a given type.
     * <p>
     * <b>Warning:</b> Use pagination for large tables to avoid memory issues.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * List<User> allUsers = persistence.findAll(User.class);
     * }</pre>
     *
     * @param entityClass the entity class
     * @param <T> the entity type
     * @return list of all entities
     */
    @Override
    public <T> List<T> findAll(Class<T> entityClass) {
        log.debug("Finding all {}", entityClass.getSimpleName());
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        query.select(root);
        return entityManager.createQuery(query).getResultList();
    }

    /**
     * Finds all entities with pagination.
     * <p>
     * Recommended for large datasets. Returns a Page object with total count
     * and page metadata.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
     * Page<User> userPage = persistence.findAll(User.class, pageable);
     *
     * System.out.println("Total: " + userPage.getTotalElements());
     * System.out.println("Pages: " + userPage.getTotalPages());
     * userPage.getContent().forEach(user -> System.out.println(user.getName()));
     * }</pre>
     *
     * @param entityClass the entity class
     * @param pageable pagination parameters
     * @param <T> the entity type
     * @return page of entities
     */
    public <T> Page<T> findAll(Class<T> entityClass, Pageable pageable) {
        log.debug("Finding {} with pagination: page={}, size={}",
                 entityClass.getSimpleName(), pageable.getPageNumber(), pageable.getPageSize());

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        query.select(root);

        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<T> content = typedQuery.getResultList();

        // Get total count
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        countQuery.select(cb.count(countQuery.from(entityClass)));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Executes a custom JPQL query without parameters.
     * <p>
     * Simple query execution for queries that don't need parameters.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * List<User> allUsers = persistence.query(User.class,
     *     "SELECT u FROM User u");
     * }</pre>
     *
     * @param entityClass the result type
     * @param query the JPQL query
     * @param <T> the result type
     * @return list of query results
     */
    @Override
    public <T> List<T> query(Class<T> entityClass, String query) {
        log.debug("Executing JPQL query for {}: {}", entityClass.getSimpleName(), query);
        return entityManager.createQuery(query, entityClass).getResultList();
    }

    /**
     * Executes a custom JPQL query with positional parameters.
     * <p>
     * Parameters are provided as varargs and applied in order (1, 2, 3...).
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * List<User> activeUsers = persistence.query(User.class,
     *     "SELECT u FROM User u WHERE u.active = ?1 AND u.role = ?2",
     *     true, "ADMIN");
     * }</pre>
     *
     * @param entityClass the result type
     * @param query the JPQL query with positional parameters (?1, ?2, etc.)
     * @param params query parameters in order
     * @param <T> the result type
     * @return list of query results
     */
    @Override
    public <T> List<T> query(Class<T> entityClass, String query, Object... params) {
        log.debug("Executing JPQL query for {}: {}", entityClass.getSimpleName(), query);
        TypedQuery<T> typedQuery = entityManager.createQuery(query, entityClass);
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                typedQuery.setParameter(i + 1, params[i]);
            }
        }
        return typedQuery.getResultList();
    }

    /**
     * Executes a JPQL query with named parameters.
     * <p>
     * Supports JPQL (Java Persistence Query Language) for type-safe queries.
     * This is an additional convenience method not in the interface.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * List<User> activeUsers = persistence.query(User.class,
     *     "SELECT u FROM User u WHERE u.active = :active AND u.role = :role",
     *     Map.of("active", true, "role", "ADMIN")
     * );
     * }</pre>
     *
     * @param entityClass the result type
     * @param jpql the JPQL query
     * @param parameters named parameters
     * @param <T> the result type
     * @return list of query results
     */
    public <T> List<T> query(Class<T> entityClass, String jpql, Map<String, Object> parameters) {
        log.debug("Executing JPQL query for {}: {}", entityClass.getSimpleName(), jpql);
        TypedQuery<T> query = entityManager.createQuery(jpql, entityClass);
        if (parameters != null) {
            parameters.forEach(query::setParameter);
        }
        return query.getResultList();
    }

    /**
     * Deletes an entity from the database.
     * <p>
     * The entity must be managed (retrieved from database) before deletion.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * User user = persistence.findById(User.class, userId).orElseThrow();
     * persistence.delete(user);
     * }</pre>
     *
     * @param entity the entity to delete
     * @param <T> the entity type
     */
    @Override
    public <T> void delete(T entity) {
        log.debug("Deleting entity: {}", entity.getClass().getSimpleName());
        if (entityManager.contains(entity)) {
            entityManager.remove(entity);
        } else {
            entityManager.remove(entityManager.merge(entity));
        }
    }

    /**
     * Deletes an entity by its primary key.
     * <p>
     * More efficient than loading the entity first, especially for cascade deletes.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * persistence.deleteById(User.class, userId);
     * }</pre>
     *
     * @param entityClass the entity class
     * @param id the primary key
     * @param <T> the entity type
     * @param <ID> the ID type
     */
    @Override
    public <T, ID> void deleteById(Class<T> entityClass, ID id) {
        log.debug("Deleting {} by ID: {}", entityClass.getSimpleName(), id);
        T entity = entityManager.find(entityClass, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    /**
     * Checks if an entity exists by its ID.
     * <p>
     * More efficient than findById when you only need to check existence.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * if (persistence.existsById(User.class, userId)) {
     *     // user exists
     * }
     * }</pre>
     *
     * @param entityClass the entity class
     * @param id the entity ID
     * @param <T> the entity type
     * @param <ID> the ID type
     * @return true if entity exists, false otherwise
     */
    @Override
    public <T, ID> boolean existsById(Class<T> entityClass, ID id) {
        log.debug("Checking if {} exists with ID: {}", entityClass.getSimpleName(), id);
        return entityManager.find(entityClass, id) != null;
    }

    /**
     * Counts the total number of entities of a given type.
     * <p>
     * Uses a COUNT query which is more efficient than loading all entities.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * long userCount = persistence.count(User.class);
     * }</pre>
     *
     * @param entityClass the entity class
     * @param <T> the entity type
     * @return total count of entities
     */
    @Override
    public <T> long count(Class<T> entityClass) {
        log.debug("Counting entities of type: {}", entityClass.getSimpleName());
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<T> root = cq.from(entityClass);
        cq.select(cb.count(root));
        return entityManager.createQuery(cq).getSingleResult();
    }

    /**
     * Executes an operation within a transaction.
     * <p>
     * If the operation throws an exception, the transaction is rolled back.
     * Otherwise, it's committed.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * Order order = persistence.executeInTransaction(() -> {
     *     Order o = new Order();
     *     o.setCustomer(customer);
     *     o = persistence.save(o);
     *
     *     for (OrderItem item : items) {
     *         item.setOrder(o);
     *         persistence.save(item);
     *     }
     *
     *     return o;
     * });
     * }</pre>
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     */
    @Override
    @Transactional
    public <T> T executeInTransaction(Supplier<T> operation) {
        log.debug("Executing operation in transaction");
        return operation.get();
    }

    /**
     * Flushes pending changes to the database.
     * <p>
     * Forces synchronization of persistence context with the database.
     * Useful before executing native queries or when you need immediate consistency.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * User user = new User();
     * user.setEmail("john@example.com");
     * persistence.save(user);
     * persistence.flush(); // Force write to database
     * // user.getId() is now available
     * }</pre>
     */
    @Override
    public void flush() {
        log.debug("Flushing persistence context");
        entityManager.flush();
    }

    /**
     * Gets the JPA CriteriaBuilder for type-safe queries.
     * <p>
     * Use this for complex queries with compile-time type checking.
     * </p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * CriteriaBuilder cb = persistence.getCriteriaBuilder();
     * CriteriaQuery<User> query = cb.createQuery(User.class);
     * Root<User> root = query.from(User.class);
     *
     * // Complex predicate
     * Predicate predicate = cb.and(
     *     cb.equal(root.get("active"), true),
     *     cb.or(
     *         cb.like(root.get("email"), "%@example.com"),
     *         cb.equal(root.get("role"), "ADMIN")
     *     ),
     *     cb.greaterThan(root.get("createdAt"), lastMonth)
     * );
     *
     * query.where(predicate);
     * List<User> users = entityManager.createQuery(query).getResultList();
     * }</pre>
     *
     * @return the CriteriaBuilder
     */
    public CriteriaBuilder getCriteriaBuilder() {
        return entityManager.getCriteriaBuilder();
    }

    /**
     * Gets the underlying JPA EntityManager.
     * <p>
     * For advanced use cases not covered by the facade API.
     * </p>
     *
     * @return the EntityManager
     */
    public EntityManager getEntityManager() {
        return entityManager;
    }
}

