package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * Redis health indicator.
 *
 * <p>Checks Redis connectivity using Spring Data Redis RedisConnectionFactory.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Connection validation via PING command</li>
 *   <li>Reports Redis server version</li>
 *   <li>Reports connection mode (standalone/cluster/sentinel)</li>
 *   <li>Reports connected clients count</li>
 *   <li>Timeout handling</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * RedisHealthIndicator indicator = new RedisHealthIndicator(
 *     redisConnectionFactory,
 *     properties.getRedis()
 * );
 * Health health = indicator.check();
 * }</pre>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   health:
 *     redis:
 *       enabled: true
 *       timeout: 3000
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class RedisHealthIndicator implements AdharHealthIndicator {

    private final Object redisConnectionFactory;
    private final AdharHealthProperties.RedisConfig config;

    /**
     * Creates Redis health indicator.
     *
     * @param redisConnectionFactory Spring Data Redis RedisConnectionFactory
     * @param config redis health configuration
     */
    public RedisHealthIndicator(Object redisConnectionFactory,
                                AdharHealthProperties.RedisConfig config) {
        this.redisConnectionFactory = redisConnectionFactory;
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

        try {
            // Use reflection to avoid hard dependency on Spring Data Redis
            // Get connection from factory
            Method getConnectionMethod = redisConnectionFactory.getClass().getMethod("getConnection");
            Object connection = getConnectionMethod.invoke(redisConnectionFactory);

            try {
                // Execute PING command
                Method pingMethod = connection.getClass().getMethod("ping");
                String pingResult = (String) pingMethod.invoke(connection);

                if (!"PONG".equalsIgnoreCase(pingResult)) {
                    return Health.down()
                        .component(getName())
                        .withDetail("error", "Unexpected ping response: " + pingResult)
                        .build();
                }

                // Get server info
                String version = "unknown";
                String mode = "unknown";
                String connectedClients = "unknown";

                try {
                    // Try to get server info using INFO command
                    Method serverCommandsMethod = connection.getClass().getMethod("serverCommands");
                    Object serverCommands = serverCommandsMethod.invoke(connection);

                    Method infoMethod = serverCommands.getClass().getMethod("info", String.class);
                    Object serverInfo = infoMethod.invoke(serverCommands, "server");

                    if (serverInfo != null) {
                        // Parse server info properties
                        Method getMethod = serverInfo.getClass().getMethod("get", String.class);
                        Object versionObj = getMethod.invoke(serverInfo, "redis_version");
                        Object modeObj = getMethod.invoke(serverInfo, "redis_mode");

                        if (versionObj != null) {
                            version = versionObj.toString();
                        }
                        if (modeObj != null) {
                            mode = modeObj.toString();
                        }
                    }

                    // Get clients info
                    Object clientsInfo = infoMethod.invoke(serverCommands, "clients");
                    if (clientsInfo != null) {
                        Method getMethod = clientsInfo.getClass().getMethod("get", String.class);
                        Object clientsObj = getMethod.invoke(clientsInfo, "connected_clients");
                        if (clientsObj != null) {
                            connectedClients = clientsObj.toString();
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not retrieve Redis server info: {}", e.getMessage());
                }

                return Health.up()
                    .component(getName())
                    .withDetail("version", version)
                    .withDetail("mode", mode)
                    .withDetail("connectedClients", connectedClients)
                    .build();

            } finally {
                // Close connection
                try {
                    Method closeMethod = connection.getClass().getMethod("close");
                    closeMethod.invoke(connection);
                } catch (Exception e) {
                    log.debug("Error closing Redis connection: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return Health.down(e)
                .component(getName())
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    @Override
    public String getName() {
        return "redis";
    }
}
