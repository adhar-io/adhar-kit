package com.adhar.kit.dapr.config;

import com.adhar.kit.dapr.DaprComponents;
import com.adhar.kit.dapr.DaprMode;
import com.adhar.kit.dapr.resilience.ResilienceSettings;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for all Dapr building blocks.
 *
 * <p>Nothing needs to be set on the platform: the Dapr injector sets {@code DAPR_HTTP_PORT},
 * {@code DAPR_GRPC_PORT}, {@code DAPR_API_TOKEN} and {@code APP_ID} on every annotated pod, and the
 * platform provides components with the default names in every environment namespace. Every value
 * here can also come from the environment ({@code ADHAR_DAPR_MODE}, {@code ADHAR_DAPR_PUBSUB},
 * {@code ADHAR_DAPR_STATESTORE}, {@code ADHAR_DAPR_SECRETSTORE}, {@code ADHAR_DAPR_CONFIGSTORE},
 * {@code ADHAR_DAPR_LOCKSTORE}).</p>
 *
 * <p><b>Example - application.yml:</b></p>
 * <pre>{@code
 * adhar:
 *   dapr:
 *     mode: auto            # auto | sidecar | memory
 *     state-store: statestore
 *     pubsub: pubsub
 *     sidecar:
 *       wait-timeout: 30s
 *     service-invocation:
 *       timeout: 10000
 *       retries: 3
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
     * How the building blocks are reached: auto (sidecar when DAPR_HTTP_PORT or DAPR_HTTP_ENDPOINT
     * is set, memory otherwise), sidecar, or memory (in-process, no Dapr).
     */
    private DaprMode mode = DaprMode.AUTO;

    /**
     * Application ID for service discovery. Defaults to APP_ID, DAPR_APP_ID, ADHAR_APP_NAME, then
     * spring.application.name.
     */
    private String appId;

    /**
     * Application port.
     */
    private int appPort = 8080;

    /**
     * Sidecar HTTP base URL. Defaults to DAPR_HTTP_ENDPOINT, else http://127.0.0.1:${DAPR_HTTP_PORT:-3500}.
     */
    private String httpEndpoint;

    /**
     * Sidecar gRPC endpoint. Defaults to DAPR_GRPC_ENDPOINT, else the SDK default on DAPR_GRPC_PORT.
     */
    private String grpcEndpoint;

    /**
     * Dapr API token. Defaults to DAPR_API_TOKEN.
     */
    private String apiToken;

    /**
     * Dapr sidecar port (alias of dapr-http-port).
     */
    private Integer daprPort;

    /**
     * Dapr sidecar HTTP port. Defaults to DAPR_HTTP_PORT, else 3500.
     */
    private Integer daprHttpPort;

    /**
     * Dapr sidecar gRPC port. Defaults to DAPR_GRPC_PORT, else 50001.
     */
    private Integer daprGrpcPort;

    /**
     * Default state store name.
     */
    private String stateStore = DaprComponents.DEFAULT_STATE_STORE;

    /**
     * Default pub/sub component name.
     */
    private String pubsub = DaprComponents.DEFAULT_PUBSUB;

    /**
     * Default secret store name.
     */
    private String secretStore = DaprComponents.DEFAULT_SECRET_STORE;

    /**
     * Default configuration store name (ADHAR_DAPR_CONFIGSTORE).
     */
    private String configStore = DaprComponents.DEFAULT_CONFIG_STORE;

    /**
     * Default configuration store name; superseded by config-store.
     */
    private String configurationStore;

    /**
     * Default lock store name.
     */
    private String lockStore = DaprComponents.DEFAULT_LOCK_STORE;

    /**
     * Startup wait for the sidecar.
     */
    private SidecarConfig sidecar = new SidecarConfig();

    /**
     * Values for memory mode (no sidecar).
     */
    private MemoryConfig memory = new MemoryConfig();

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
        /** Superseded by adhar.dapr.state-store. */
        private String componentName;
        private String consistency = "eventual";
        private String concurrency = "last-write";
    }

    /**
     * Pub/Sub configuration.
     */
    @Data
    public static class PubSubConfig {
        private boolean enabled = true;
        /** Superseded by adhar.dapr.pubsub. */
        private String componentName;
        /** Dead-letter topic for subscriptions that do not name one; none when unset. */
        private String deadLetterTopic;
    }

    /**
     * Service invocation configuration.
     */
    @Data
    public static class ServiceInvocationConfig {
        private boolean enabled = true;
        /** Timeout of one invocation, in milliseconds. */
        private long timeout = 60000;
        /** Attempts per invocation, including the first (idempotent verbs only). */
        private int retries = 3;
        /** Base backoff between attempts (attempt n waits n times this). */
        private Duration retryBackoff = Duration.ofMillis(100);
        /** Consecutive failures that open the breaker of a target app. */
        private int failureThreshold = 5;
        /** How long an open breaker rejects calls before a trial call. */
        private Duration openStateDuration = Duration.ofSeconds(30);

        /** The resilience settings each target app id starts with. */
        public ResilienceSettings toResilienceSettings() {
            return new ResilienceSettings(Math.max(1, retries), retryBackoff, Duration.ofMillis(timeout),
                    Math.max(1, failureThreshold), openStateDuration);
        }
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
        /** Superseded by adhar.dapr.secret-store. */
        private String storeName;
    }

    /**
     * Configuration API configuration.
     */
    @Data
    public static class ConfigurationConfig {
        private boolean enabled = true;
        /** Superseded by adhar.dapr.config-store. */
        private String storeName;
        /** Whether @DaprConfiguration(subscribe = true) keeps values live. */
        private boolean subscribeEnabled = true;
    }

    /**
     * Distributed lock configuration.
     */
    @Data
    public static class LockConfig {
        private boolean enabled = true;
        /** Superseded by adhar.dapr.lock-store. */
        private String storeName;
        private long defaultTimeout = 30000;
    }

    /**
     * Workflow configuration.
     */
    @Data
    public static class WorkflowConfig {
        private boolean enabled = true;
        /** Register Workflow and WorkflowActivity beans with the workflow runtime and start it. */
        private boolean registerBeans = true;
    }

    /**
     * Actors configuration.
     */
    @Data
    public static class ActorsConfig {
        private boolean enabled = true;
        private Duration actorIdleTimeout = Duration.ofMinutes(60);
        private Duration actorScanInterval = Duration.ofSeconds(30);
        private Duration drainOngoingCallTimeout = Duration.ofSeconds(60);
        private boolean drainRebalancedActors = true;
        /** Packages scanned for @DaprActor classes; defaults to the application's packages. */
        private List<String> scanPackages = new ArrayList<>();
    }

    /**
     * Cryptography configuration.
     */
    @Data
    public static class CryptoConfig {
        private boolean enabled = true;
        private String defaultComponent = DaprComponents.DEFAULT_CRYPTO;
        /** Key used by the three-argument encrypt/decrypt of the deprecated AdharDaprClient. */
        private String defaultKey;
    }

    /**
     * Transactional outbox configuration. Disabled by default; enabling it wires an
     * {@code OutboxPublisher} plus a scheduled relay.
     */
    @Data
    public static class OutboxConfig {
        private boolean enabled = false;
        private String stateStore = DaprComponents.DEFAULT_STATE_STORE;
        private String pubsub = DaprComponents.DEFAULT_PUBSUB;
        private int maxAttempts = 5;
        private long relayIntervalMs = 5000;
        /** Lock store guarding the relay so one replica relays at a time; empty to run unguarded. */
        private String lockStore = DaprComponents.DEFAULT_LOCK_STORE;
    }

    /**
     * Startup wait for the sidecar.
     */
    @Data
    public static class SidecarConfig {
        /** Wait for the sidecar (GET /v1.0/healthz/outbound) before the application serves traffic. */
        private boolean waitOnStartup = true;
        /** How long to wait. */
        private Duration waitTimeout = Duration.ofSeconds(30);
        /** Fail startup when the sidecar is not ready in time (otherwise log an error and continue). */
        private boolean failOnTimeout = false;
    }

    /**
     * Values for memory mode.
     */
    @Data
    public static class MemoryConfig {
        /** Secrets by name, each a map of key to value, served from the default secret store. */
        private Map<String, Map<String, String>> secrets = new LinkedHashMap<>();
        /** Configuration values served from the default configuration store. */
        private Map<String, String> configuration = new LinkedHashMap<>();
    }

    /**
     * The default component names, honouring the superseded nested names when only they were set.
     *
     * @return the component names
     */
    public DaprComponents toComponents() {
        return new DaprComponents(
                prefer(pubsub, DaprComponents.DEFAULT_PUBSUB, pubSubConfig.getComponentName()),
                prefer(stateStore, DaprComponents.DEFAULT_STATE_STORE, stateStoreConfig.getComponentName()),
                prefer(secretStore, DaprComponents.DEFAULT_SECRET_STORE, secrets.getStoreName()),
                prefer(prefer(configStore, DaprComponents.DEFAULT_CONFIG_STORE, configurationStore),
                        DaprComponents.DEFAULT_CONFIG_STORE, configuration.getStoreName()),
                prefer(lockStore, DaprComponents.DEFAULT_LOCK_STORE, lock.getStoreName()),
                crypto.getDefaultComponent());
    }

    @DeprecatedConfigurationProperty(replacement = "adhar.dapr.config-store")
    public String getConfigurationStore() {
        return configurationStore;
    }

    private static String prefer(String value, String defaultValue, String superseded) {
        boolean explicit = value != null && !value.equals(defaultValue);
        if (!explicit && superseded != null && !superseded.isBlank()) {
            return superseded;
        }
        return value;
    }
}

