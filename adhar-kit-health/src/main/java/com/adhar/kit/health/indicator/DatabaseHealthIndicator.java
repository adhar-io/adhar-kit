package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Database health indicator.
 *
 * <p>Checks database connectivity and executes validation query.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Connection validation</li>
 *   <li>Custom validation query</li>
 *   <li>Timeout handling</li>
 *   <li>Database product detection</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(
 *     dataSource,
 *     properties
 * );
 * Health health = indicator.check();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DatabaseHealthIndicator implements AdharHealthIndicator {

    private final DataSource dataSource;
    private final AdharHealthProperties.DatabaseConfig config;

    /**
     * Creates database health indicator.
     *
     * @param dataSource data source
     * @param config database configuration
     */
    public DatabaseHealthIndicator(DataSource dataSource,
                                   AdharHealthProperties.DatabaseConfig config) {
        this.dataSource = dataSource;
        this.config = config;
    }

    @Override
    public Health check() {
        if (!config.isEnabled()) {
            return Health.unknown()
                .component(getName())
                .withDetail("status", "disabled")
                .build();
        }

        try (Connection connection = dataSource.getConnection()) {
            // Get database product info
            String product = connection.getMetaData().getDatabaseProductName();
            String version = connection.getMetaData().getDatabaseProductVersion();

            // Execute validation query
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout((int) (config.getTimeout() / 1000));
                ResultSet resultSet = statement.executeQuery(config.getValidationQuery());
                resultSet.close();
            }

            return Health.up()
                .component(getName())
                .withDetail("database", product)
                .withDetail("version", version)
                .withDetail("validationQuery", config.getValidationQuery())
                .build();

        } catch (Exception e) {
            log.error("Database health check failed", e);
            return Health.down(e)
                .component(getName())
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    @Override
    public String getName() {
        return "database";
    }
}

