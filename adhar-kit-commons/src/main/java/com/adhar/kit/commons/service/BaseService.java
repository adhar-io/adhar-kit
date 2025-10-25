package com.adhar.kit.commons.service;

import com.adhar.kit.commons.exception.ResourceNotFoundException;
import com.adhar.kit.commons.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Base service class providing common functionality for all services.
 * Includes standard CRUD operations, exception handling, and logging.
 *
 * @param <T> the entity type
 * @param <ID> the entity ID type
 */
@Slf4j
public abstract class BaseService<T, ID> {

    /**
     * Finds an entity by ID.
     *
     * @param id the entity ID
     * @return Optional containing the entity if found
     */
    public abstract Optional<T> findById(ID id);

    /**
     * Saves an entity.
     *
     * @param entity the entity to save
     * @return the saved entity
     */
    public abstract T save(T entity);

    /**
     * Deletes an entity by ID.
     *
     * @param id the entity ID
     */
    public abstract void deleteById(ID id);

    /**
     * Checks if an entity exists by ID.
     *
     * @param id the entity ID
     * @return true if entity exists
     */
    public abstract boolean existsById(ID id);

    /**
     * Gets an entity by ID, throwing exception if not found.
     *
     * @param id the entity ID
     * @return the entity
     * @throws ResourceNotFoundException if entity not found
     */
    public T getById(ID id) {
        return findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
    }

    /**
     * Gets an entity by ID with custom error message.
     *
     * @param id the entity ID
     * @param errorMessage custom error message
     * @return the entity
     * @throws ResourceNotFoundException if entity not found
     */
    public T getById(ID id, String errorMessage) {
        return findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(errorMessage));
    }

    /**
     * Validates that an entity exists before performing operations.
     *
     * @param id the entity ID
     * @throws ResourceNotFoundException if entity doesn't exist
     */
    protected void validateEntityExists(ID id) {
        if (!existsById(id)) {
            throw new ResourceNotFoundException(getEntityName(), id);
        }
    }

    /**
     * Performs a safe operation with exception handling and logging.
     *
     * @param operation the operation to perform
     * @param operationName the name of the operation for logging
     * @param <R> the return type
     * @return the operation result
     * @throws ServiceException if operation fails
     */
    protected <R> R performSafeOperation(SafeOperation<R> operation, String operationName) {
        try {
            log.debug("Starting operation: {}", operationName);
            R result = operation.execute();
            log.debug("Successfully completed operation: {}", operationName);
            return result;
        } catch (Exception e) {
            log.error("Operation failed: {} - {}", operationName, e.getMessage(), e);
            throw new ServiceException("Operation failed: " + operationName, e);
        }
    }

    /**
     * Performs a safe void operation with exception handling and logging.
     *
     * @param operation the operation to perform
     * @param operationName the name of the operation for logging
     * @throws ServiceException if operation fails
     */
    protected void performSafeVoidOperation(SafeVoidOperation operation, String operationName) {
        try {
            log.debug("Starting operation: {}", operationName);
            operation.execute();
            log.debug("Successfully completed operation: {}", operationName);
        } catch (Exception e) {
            log.error("Operation failed: {} - {}", operationName, e.getMessage(), e);
            throw new ServiceException("Operation failed: " + operationName, e);
        }
    }

    /**
     * Gets the name of the entity type for error messages.
     * Override this method to provide a custom entity name.
     *
     * @return the entity name
     */
    protected String getEntityName() {
        return this.getClass().getSimpleName().replace("Service", "");
    }

    /**
     * Functional interface for safe operations that return a result.
     *
     * @param <R> the return type
     */
    @FunctionalInterface
    protected interface SafeOperation<R> {
        R execute() throws Exception;
    }

    /**
     * Functional interface for safe void operations.
     */
    @FunctionalInterface
    protected interface SafeVoidOperation {
        void execute() throws Exception;
    }
}
