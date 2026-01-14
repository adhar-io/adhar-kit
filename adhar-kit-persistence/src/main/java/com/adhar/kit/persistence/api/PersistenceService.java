package com.adhar.kit.persistence.api;

import java.util.List;
import java.util.Optional;

/**
 * Framework-agnostic Persistence Service API.
 *
 * <p>This interface provides a unified data access abstraction across
 * Spring Boot, Quarkus, and Micronaut frameworks with support for JPA,
 * MongoDB, and multi-tenancy patterns.</p>
 *
 * <p><b>Supported Features:</b></p>
 * <ul>
 *   <li>JPA/Hibernate for relational databases</li>
 *   <li>MongoDB for document storage</li>
 *   <li>Multi-tenancy (schema/database/discriminator)</li>
 *   <li>Auditing (created/modified timestamps)</li>
 *   <li>Soft delete support</li>
 *   <li>Pagination and sorting</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * PersistenceService persistence = PersistenceFacade.getInstance();
 *
 * // Save entity
 * User user = new User("john@example.com");
 * user = persistence.save(user);
 *
 * // Find by ID
 * Optional<User> found = persistence.findById(User.class, user.getId());
 *
 * // Find all
 * List<User> users = persistence.findAll(User.class);
 *
 * // Custom query
 * List<User> activeUsers = persistence.query(User.class,
 *     "SELECT u FROM User u WHERE u.active = true");
 *
 * // Delete
 * persistence.delete(user);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see com.adhar.kit.persistence.PersistenceFacade
 */
public interface PersistenceService {

    /**
     * Saves an entity to the database.
     *
     * @param entity the entity to save
     * @param <T> the entity type
     * @return the saved entity with generated ID
     */
    <T> T save(T entity);

    /**
     * Saves multiple entities in a batch.
     *
     * @param entities the entities to save
     * @param <T> the entity type
     * @return the saved entities
     */
    <T> List<T> saveAll(Iterable<T> entities);

    /**
     * Finds an entity by its ID.
     *
     * @param entityClass the entity class
     * @param id the entity ID
     * @param <T> the entity type
     * @param <ID> the ID type
     * @return Optional containing the entity if found
     */
    <T, ID> Optional<T> findById(Class<T> entityClass, ID id);

    /**
     * Finds all entities of a given type.
     *
     * @param entityClass the entity class
     * @param <T> the entity type
     * @return list of all entities
     */
    <T> List<T> findAll(Class<T> entityClass);

    /**
     * Executes a custom query.
     *
     * @param entityClass the entity class
     * @param query the query string (JPQL or MongoDB query)
     * @param <T> the entity type
     * @return list of query results
     */
    <T> List<T> query(Class<T> entityClass, String query);

    /**
     * Executes a custom query with parameters.
     *
     * @param entityClass the entity class
     * @param query the query string
     * @param params query parameters
     * @param <T> the entity type
     * @return list of query results
     */
    <T> List<T> query(Class<T> entityClass, String query, Object... params);

    /**
     * Deletes an entity.
     *
     * @param entity the entity to delete
     * @param <T> the entity type
     */
    <T> void delete(T entity);

    /**
     * Deletes an entity by ID.
     *
     * @param entityClass the entity class
     * @param id the entity ID
     * @param <T> the entity type
     * @param <ID> the ID type
     */
    <T, ID> void deleteById(Class<T> entityClass, ID id);

    /**
     * Checks if an entity exists by ID.
     *
     * @param entityClass the entity class
     * @param id the entity ID
     * @param <T> the entity type
     * @param <ID> the ID type
     * @return true if exists, false otherwise
     */
    <T, ID> boolean existsById(Class<T> entityClass, ID id);

    /**
     * Counts entities of a given type.
     *
     * @param entityClass the entity class
     * @param <T> the entity type
     * @return total count
     */
    <T> long count(Class<T> entityClass);

    /**
     * Executes code within a transaction.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the operation result
     */
    <T> T executeInTransaction(java.util.function.Supplier<T> operation);

    /**
     * Flushes pending changes to the database.
     */
    void flush();
}

