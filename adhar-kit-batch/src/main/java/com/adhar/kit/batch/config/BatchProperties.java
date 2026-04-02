package com.adhar.kit.batch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Adhar Batch module.
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * adhar:
 *   batch:
 *     enabled: true
 *     table-prefix: BATCH_
 *     max-concurrent-jobs: 5
 *     retry-on-failure: true
 *     max-retries: 3
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.batch")
public class BatchProperties {

    /**
     * Enable the batch module.
     */
    private boolean enabled = true;

    /**
     * Table prefix for Spring Batch metadata tables.
     */
    private String tablePrefix = "BATCH_";

    /**
     * Maximum number of concurrent jobs that can run simultaneously.
     */
    private int maxConcurrentJobs = 5;

    /**
     * Whether to automatically retry failed jobs.
     */
    private boolean retryOnFailure = true;

    /**
     * Maximum number of retry attempts for failed jobs.
     */
    private int maxRetries = 3;

    /**
     * Default chunk size for step processing.
     */
    private int defaultChunkSize = 100;

    /**
     * Default page size for paginated item readers.
     */
    private int defaultPageSize = 50;
}
