package com.adhar.kit.dapr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for all Dapr building blocks.
 *
 * <p><b>Example - application.yml:</b></p>
 * <pre>{@code
 * adhar:
 *   dapr:
 *     enabled: true
 *     app-id: my-service
 *     state-store: statestore
 *     pubsub: pubsub
 *     actors:
 *       enabled: true
 *     workflow:
 *       enabled: true
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.dapr")
public class DaprProperties {

    /**
     * Enable Dapr integration.
     */
    private boolean enabled = true;

    /**
     * Application ID for service discovery.
     */
    private String appId;

    /**
     * Application port.
     */
    private int appPort = 8080;

    /**
     * Dapr sidecar port.
     */
    private int daprPort = 3500;

    /**
     * Dapr sidecar HTTP port.
     */
    private int daprHttpPort = 3500;

    /**
     * Dapr sidecar gRPC port.
     */
    private int daprGrpcPort = 50001;

    /**
     * Default state store name.
     */
    private String stateStore = "statestore";

    /**
     * Default pub/sub component name.
     */
    private String pubsub = "pubsub";

    /**
     * Default secret store name.
     */
    private String secretStore = "secretstore";

    /**
     * Default configuration store name.
     */
    private String configurationStore = "configstore";

    /**
     * Default lock store name.
     */
    private String lockStore = "lockstore";

    /**
     * State store configuration.
     */
    private StateStoreConfig stateStoreConfig = new StateStoreConfig();

    /**
     * Pub/Sub configuration.
     */
    private PubSubConfig pubSubConfig = new PubSubConfig();

    /**
     * Service invocation configuration.
     */
    private ServiceInvocationConfig serviceInvocation = new ServiceInvocationConfig();

    /**
     * Bindings configuration.
     */
    private BindingsConfig bindings = new BindingsConfig();

    /**
     * Secrets configuration.
     */
    private SecretsConfig secrets = new SecretsConfig();

    /**
     * Configuration API configuration.
     */
    private ConfigurationConfig configuration = new ConfigurationConfig();

    /**
     * Distributed lock configuration.
     */
    private LockConfig lock = new LockConfig();

    /**
     * Workflow configuration.
     */
    private WorkflowConfig workflow = new WorkflowConfig();

    /**
     * Actors configuration.
     */
    private ActorsConfig actors = new ActorsConfig();

    /**
     * Cryptography configuration.
     */
    private CryptoConfig crypto = new CryptoConfig();

    /**
     * Transactional outbox configuration.
     */
    private OutboxConfig outbox = new OutboxConfig();

    /**
     * State store configuration.
     */
    @Data
    public static class StateStoreConfig {
        private boolean enabled = true;
        private String componentName = "statestore";
        private String consistency = "eventual";
        private String concurrency = "first-write";
    }

    /**
     * Pub/Sub configuration.
     */
    @Data
    public static class PubSubConfig {
        private boolean enabled = true;
        private String componentName = "pubsub";
        private String deadLetterTopic = "dead-letter";
    }

    /**
     * Service invocation configuration.
     */
    @Data
    public static class ServiceInvocationConfig {
        private boolean enabled = true;
        private long timeout = 60000;
        private int retries = 3;
    }

    /**
     * Bindings configuration.
     */
    @Data
    public static class BindingsConfig {
        private boolean enabled = true;
        private List<String> inputBindings = new ArrayList<>();
        private List<String> outputBindings = new ArrayList<>();
    }

    /**
     * Secrets configuration.
     */
    @Data
    public static class SecretsConfig {
        private boolean enabled = true;
        private String storeName = "secretstore";
    }

    /**
     * Configuration API configuration.
     */
    @Data
    public static class ConfigurationConfig {
        private boolean enabled = true;
        private String storeName = "configstore";
        private boolean subscribeEnabled = true;
    }

    /**
     * Distributed lock configuration.
     */
    @Data
    public static class LockConfig {
        private boolean enabled = true;
        private String storeName = "lockstore";
        private long defaultTimeout = 30000;
    }

    /**
     * Workflow configuration.
     */
    @Data
    public static class WorkflowConfig {
        private boolean enabled = true;
    }

    /**
     * Actors configuration.
     */
    @Data
    public static class ActorsConfig {
        private boolean enabled = true;
        private String actorIdleTimeout = "60m";
        private String actorScanInterval = "30s";
        private String drainOngoingCallTimeout = "60s";
        private boolean drainRebalancedActors = true;
    }

    /**
     * Cryptography configuration.
     */
    @Data
    public static class CryptoConfig {
        private boolean enabled = true;
        private String defaultComponent = "crypto";
    }

    /**
     * Transactional outbox configuration. Disabled by default; enabling it wires an
     * {@code OutboxPublisher} plus a scheduled relay.
     */
    @Data
    public static class OutboxConfig {
        private boolean enabled = false;
        private String stateStore = "statestore";
        private String pubsub = "pubsub";
        private int maxAttempts = 5;
        private long relayIntervalMs = 5000;
    }
}

