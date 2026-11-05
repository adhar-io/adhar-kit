package com.adhar.kit.batch.repository;

import com.adhar.kit.batch.runtime.ExitCodes;
import org.springframework.boot.ExitCodeGenerator;

/**
 * The JobRepository cannot be used safely: it is in-memory where it must persist, or its
 * schema is missing or incomplete.
 *
 * <p>Thrown while the application context starts, so the run never begins. As an
 * {@link ExitCodeGenerator}, it makes {@code SpringApplication} exit with
 * {@link ExitCodes#INVALID_REQUEST} (2): a configuration problem that a pod restart cannot fix,
 * which the platform's pod failure policy fails without a retry.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class BatchRepositoryException extends IllegalStateException implements ExitCodeGenerator {

    /**
     * @param message what is wrong and how to fix it
     */
    public BatchRepositoryException(String message) {
        super(message);
    }

    /**
     * @param message what is wrong and how to fix it
     * @param cause   the underlying failure
     */
    public BatchRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int getExitCode() {
        return ExitCodes.INVALID_REQUEST;
    }
}
