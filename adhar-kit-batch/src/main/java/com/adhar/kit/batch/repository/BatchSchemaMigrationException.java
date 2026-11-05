package com.adhar.kit.batch.repository;

/**
 * The kit's JobRepository migration failed, or the migrated schema does not have the objects
 * Spring Batch needs. Exits the application with code 2 (see {@link BatchRepositoryException}).
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class BatchSchemaMigrationException extends BatchRepositoryException {

    /**
     * @param message what failed and how to fix it
     */
    public BatchSchemaMigrationException(String message) {
        super(message);
    }

    /**
     * @param message what failed and how to fix it
     * @param cause   the underlying failure
     */
    public BatchSchemaMigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
