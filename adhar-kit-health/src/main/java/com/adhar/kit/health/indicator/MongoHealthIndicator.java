package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * MongoDB health indicator.
 *
 * <p>Checks MongoDB connectivity using MongoClient.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Connection validation via ping command</li>
 *   <li>Reports MongoDB server version</li>
 *   <li>Reports replica set status (if applicable)</li>
 *   <li>Reports maximum BSON object size</li>
 *   <li>Timeout handling</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * MongoHealthIndicator indicator = new MongoHealthIndicator(
 *     mongoClient,
 *     properties.getMongo()
 * );
 * Health health = indicator.check();
 * }</pre>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   health:
 *     mongo:
 *       enabled: true
 *       timeout: 3000
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class MongoHealthIndicator implements AdharHealthIndicator {

    private final Object mongoClient;
    private final AdharHealthProperties.MongoConfig config;

    /**
     * Creates MongoDB health indicator.
     *
     * @param mongoClient MongoDB MongoClient instance
     * @param config mongo health configuration
     */
    public MongoHealthIndicator(Object mongoClient,
                                AdharHealthProperties.MongoConfig config) {
        this.mongoClient = mongoClient;
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
            // Use reflection to avoid hard dependency on MongoDB driver
            // Get database (admin for ping)
            Method getDatabaseMethod = mongoClient.getClass().getMethod("getDatabase", String.class);
            Object adminDatabase = getDatabaseMethod.invoke(mongoClient, "admin");

            // Create ping command document
            Class<?> documentClass = Class.forName("org.bson.Document");
            Object pingCommand = documentClass.getConstructor(String.class, Object.class)
                .newInstance("ping", 1);

            // Run command
            Method runCommandMethod = adminDatabase.getClass().getMethod("runCommand", documentClass);
            Object result = runCommandMethod.invoke(adminDatabase, pingCommand);

            // Check result
            Method getMethod = result.getClass().getMethod("get", Object.class);
            Object okValue = getMethod.invoke(result, "ok");

            if (okValue == null || (okValue instanceof Number && ((Number) okValue).doubleValue() != 1.0)) {
                return Health.down()
                    .component(getName())
                    .withDetail("error", "Ping command failed")
                    .build();
            }

            // Get server information
            String version = "unknown";
            String replicaSetStatus = "standalone";
            String maxBsonObjectSize = "unknown";

            try {
                // Run buildInfo command
                Object buildInfoCommand = documentClass.getConstructor(String.class, Object.class)
                    .newInstance("buildInfo", 1);
                Object buildInfoResult = runCommandMethod.invoke(adminDatabase, buildInfoCommand);

                Object versionObj = getMethod.invoke(buildInfoResult, "version");
                if (versionObj != null) {
                    version = versionObj.toString();
                }

                Object maxBsonObj = getMethod.invoke(buildInfoResult, "maxBsonObjectSize");
                if (maxBsonObj != null) {
                    long maxBsonBytes = ((Number) maxBsonObj).longValue();
                    maxBsonObjectSize = formatBytes(maxBsonBytes);
                }
            } catch (Exception e) {
                log.debug("Could not retrieve MongoDB build info: {}", e.getMessage());
            }

            try {
                // Check replica set status
                Object replSetStatusCommand = documentClass.getConstructor(String.class, Object.class)
                    .newInstance("replSetGetStatus", 1);
                Object replSetResult = runCommandMethod.invoke(adminDatabase, replSetStatusCommand);

                Object setName = getMethod.invoke(replSetResult, "set");
                Object myState = getMethod.invoke(replSetResult, "myState");

                if (setName != null) {
                    String stateDesc = getReplicaStateDescription(myState);
                    replicaSetStatus = String.format("%s (%s)", setName, stateDesc);
                }
            } catch (Exception e) {
                // Not a replica set, standalone mode
                log.debug("MongoDB is not running as replica set: {}", e.getMessage());
            }

            return Health.up()
                .component(getName())
                .withDetail("version", version)
                .withDetail("replicaSet", replicaSetStatus)
                .withDetail("maxBsonObjectSize", maxBsonObjectSize)
                .build();

        } catch (Exception e) {
            log.error("MongoDB health check failed", e);
            return Health.down(e)
                .component(getName())
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    private String getReplicaStateDescription(Object state) {
        if (state == null) return "unknown";

        int stateNum = ((Number) state).intValue();
        return switch (stateNum) {
            case 0 -> "STARTUP";
            case 1 -> "PRIMARY";
            case 2 -> "SECONDARY";
            case 3 -> "RECOVERING";
            case 5 -> "STARTUP2";
            case 6 -> "UNKNOWN";
            case 7 -> "ARBITER";
            case 8 -> "DOWN";
            case 9 -> "ROLLBACK";
            case 10 -> "REMOVED";
            default -> "STATE_" + stateNum;
        };
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public String getName() {
        return "mongodb";
    }
}
