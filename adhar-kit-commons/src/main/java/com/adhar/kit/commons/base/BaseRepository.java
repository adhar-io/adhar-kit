package com.adhar.kit.commons.base;

import java.io.Serializable;

/**
 * Base interface for all repositories.
 *
 * <p>Defines standard CRUD operations for domain repositories.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Repository
 * public interface UserRepository extends BaseRepository<User, String> {
 *
 *     Optional<User> findByUsername(String username);
 *
 *     Optional<User> findByEmail(String email);
 *
 *     List<User> findByRole(String role);
 * }
 * }</pre>
 *
 * @param <T> the entity type
 * @param <ID> the entity ID type
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface BaseRepository<T, ID extends Serializable> {

    /**
     * Saves an entity.
     *
     * @param entity the entity to save
     * @return the saved entity
     */
    T save(T entity);

    /**
     * Finds an entity by ID.
     *
     * @param id the entity ID
     * @return the entity if found
     */
    java.util.Optional<T> findById(ID id);

    /**
     * Finds all entities.
     *
     * @return list of all entities
     */
    java.util.List<T> findAll();

    /**
     * Deletes an entity by ID.
     *
     * @param id the entity ID
     */
    void deleteById(ID id);

    /**
     * Checks if entity exists.
     *
     * @param id the entity ID
     * @return true if exists
     */
    boolean existsById(ID id);

    /**
     * Counts all entities.
     *
     * @return total count
     */
    long count();
}

