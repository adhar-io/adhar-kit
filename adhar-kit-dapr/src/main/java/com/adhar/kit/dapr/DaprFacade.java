package com.adhar.kit.dapr;

import com.adhar.kit.dapr.api.ConfigurationCallback;
import com.adhar.kit.dapr.api.StateOperation;
import com.adhar.kit.dapr.api.StateWithETag;
import com.adhar.kit.dapr.backend.DaprBackend;
import com.adhar.kit.dapr.backend.SidecarDaprBackend;
import com.adhar.kit.dapr.bindings.BindingResponse;
import com.adhar.kit.dapr.configuration.ConfigItem;
import com.adhar.kit.dapr.http.DaprHttpClient;
import com.adhar.kit.dapr.invoke.HttpVerb;
import com.adhar.kit.dapr.invoke.InvokeOptions;
import com.adhar.kit.dapr.invoke.InvokeResponse;
import com.adhar.kit.dapr.jobs.JobDetails;
import com.adhar.kit.dapr.jobs.JobOptions;
import com.adhar.kit.dapr.lock.UnlockStatus;
import com.adhar.kit.dapr.memory.InMemoryDaprBackend;
import com.adhar.kit.dapr.observation.DaprObservations;
import com.adhar.kit.dapr.observation.DaprTraceContext;
import com.adhar.kit.dapr.pubsub.BulkPublishResult;
import com.adhar.kit.dapr.pubsub.DaprCloudEvent;
import com.adhar.kit.dapr.pubsub.DaprTopicHandler;
import com.adhar.kit.dapr.pubsub.DispatchStatus;
import com.adhar.kit.dapr.pubsub.DropEventException;
import com.adhar.kit.dapr.pubsub.EventDataConverter;
import com.adhar.kit.dapr.pubsub.ProgrammaticSubscriptions;
import com.adhar.kit.dapr.pubsub.PublishOptions;
import com.adhar.kit.dapr.pubsub.SubscribeOptions;
import com.adhar.kit.dapr.pubsub.TopicEventListener;
import com.adhar.kit.dapr.resilience.DaprInvocationResilience;
import com.adhar.kit.dapr.resilience.DaprResilienceRegistry;
import com.adhar.kit.dapr.resilience.ResilienceSettings;
import com.adhar.kit.dapr.serialization.DaprJsonSerializer;
import com.adhar.kit.dapr.state.DeleteOptions;
import com.adhar.kit.dapr.state.GetOptions;
import com.adhar.kit.dapr.state.SaveOptions;
import com.adhar.kit.dapr.state.StateEntry;
import com.adhar.kit.dapr.state.StateItem;
import com.adhar.kit.dapr.state.StateQueryResult;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.dapr.client.DaprPreviewClient;
import io.dapr.client.domain.ComponentMetadata;
import io.dapr.client.domain.DaprMetadata;
import io.dapr.utils.TypeRef;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The one way a service talks to Dapr: every building block through a single sidecar client
 * built from {@code adhar.dapr.*} (or the environment the Dapr injector sets), with the same
 * concepts and names as the adhar-kit for TypeScript, Go and Python.
 *
 * <p>In a Spring application the facade is a bean and {@link #getInstance()} returns that bean;
 * outside Spring {@link #getInstance()} builds one from the environment. With no sidecar
 * ({@code ADHAR_DAPR_MODE=memory}, or {@code auto} without {@code DAPR_HTTP_PORT}) state, cache,
 * pub/sub, lock, configuration and secrets run in-process (see {@link InMemoryDaprBackend}) and
 * one warning is logged; the remaining building blocks fail with a {@link DaprError}.</p>
 *
 * <p>Two ways to call it:</p>
 * <ul>
 *   <li>by building block, mirroring the other kits:
 *       {@code dapr.state().get("cart:42", Cart.class)}, {@code dapr.publish("orders", order)},
 *       {@code dapr.invoke("inventory", "api/v1/items", InvokeOptions.get())},
 *       {@code dapr.secrets().get("db")}, {@code dapr.lock().tryLock("report", owner, 60)}
 *       &mdash; the default components ({@code statestore}, {@code pubsub}, ...) are the ones the
 *       platform provides in every environment;</li>
 *   <li>with explicit component names, the original API: {@code dapr.getState("statestore",
 *       "cart:42", Cart.class)}, {@code dapr.publishEvent("pubsub", "orders", order)}.</li>
 * </ul>
 *
 * <p>Every call is a Micrometer observation ({@code adhar.dapr}: building block, operation,
 * component, outcome) and carries the caller's trace context to the sidecar. Failures are
 * {@link DaprError}s with Dapr's status, error code, message and details.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DaprFacade implements AutoCloseable {

    private static final ExecutorService ASYNC = Executors.newVirtualThreadPerTaskExecutor();
    private static final Duration PROBE_INTERVAL = Duration.ofMillis(250);

    private static volatile DaprFacade instance;

    private final DaprBackend backend;
    private final DaprConnectionDetails connection;
    private final DaprComponents components;
    private final DaprJsonSerializer serializer;
    private final DaprObservations observations;
    private final DaprResilienceRegistry resilience;
    private final EventDataConverter eventData;
    private final AtomicBoolean connectedLogged = new AtomicBoolean();
    private volatile boolean available;
    private volatile ProgrammaticSubscriptions programmaticSubscriptions;

    private final DaprStateOperations stateOperations;
    private final DaprCacheOperations cacheOperations;
    private final DaprSecretOperations secretOperations;
    private final DaprConfigurationOperations configurationOperations;
    private final DaprBindingOperations bindingOperations;
    private final DaprLockOperations lockOperations;
    private final DaprJobOperations jobOperations;
    private final DaprActorOperations actorOperations;

    /**
     * Wraps an existing SDK client (typically a mock in tests). Service invocation and actor calls
     * need the sidecar's HTTP API and use the endpoint resolved from the environment.
     *
     * <p>No {@link DaprPreviewClient} is used unless one is passed, so the distributed-lock methods
     * fail with {@link IllegalStateException}; use {@link #DaprFacade(DaprClient, DaprPreviewClient)}
     * to enable them.</p>
     *
     * @param daprClient the Dapr client
     */
    public DaprFacade(DaprClient daprClient) {
        this(daprClient, null);
    }

    /**
     * Wraps existing SDK clients. The preview client backs lock, state query, crypto and
     * streaming subscriptions.
     *
     * @param daprClient    the Dapr client
     * @param previewClient the preview client (may be {@code null} to disable those)
     */
    public DaprFacade(DaprClient daprClient, DaprPreviewClient previewClient) {
        this(legacyBackend(daprClient, previewClient), DaprConnectionDetails.fromEnvironment(),
                DaprComponents.fromEnvironment(), new DaprJsonSerializer(), DaprObservations.NOOP,
                new DaprResilienceRegistry());
        this.available = true;
    }

    /**
     * The full constructor; prefer {@link #builder()}.
     *
     * @param backend      what the building blocks run on
     * @param connection   the resolved sidecar connection
     * @param components   the default component names
     * @param serializer   the payload serializer
     * @param observations the observation helper
     * @param resilience   per-target invocation resilience
     */
    public DaprFacade(DaprBackend backend, DaprConnectionDetails connection, DaprComponents components,
                      DaprJsonSerializer serializer, DaprObservations observations,
                      DaprResilienceRegistry resilience) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        this.connection = connection != null ? connection : DaprConnectionDetails.fromEnvironment();
        this.components = components != null ? components : DaprComponents.defaults();
        this.serializer = serializer != null ? serializer : new DaprJsonSerializer();
        this.observations = observations != null ? observations : DaprObservations.NOOP;
        this.resilience = resilience != null ? resilience : new DaprResilienceRegistry();
        this.eventData = new EventDataConverter(this.serializer);
        this.available = backend.mode() == DaprMode.MEMORY;
        this.stateOperations = new DaprStateOperations(this, this.components.stateStore());
        this.cacheOperations = new DaprCacheOperations(this, this.components.stateStore());
        this.secretOperations = new DaprSecretOperations(this, this.components.secretStore());
        this.configurationOperations = new DaprConfigurationOperations(this, this.components.configStore());
        this.bindingOperations = new DaprBindingOperations(this);
        this.lockOperations = new DaprLockOperations(this, this.components.lockStore());
        this.jobOperations = new DaprJobOperations(this);
        this.actorOperations = new DaprActorOperations(this);
    }

    private static DaprBackend legacyBackend(DaprClient client, DaprPreviewClient preview) {
        DaprConnectionDetails connection = DaprConnectionDetails.fromEnvironment();
        return new SidecarDaprBackend(client, preview,
                new DaprHttpClient(connection, Duration.ofSeconds(60), DaprTraceContext.NONE),
                new DaprJsonSerializer(), DaprTraceContext.NONE, false);
    }

    /**
     * A builder for a facade with explicit settings.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The shared facade: the Spring bean when running in a Spring context, otherwise one built
     * from the environment on first use.
     *
     * @return the shared facade
     */
    public static DaprFacade getInstance() {
        DaprFacade current = instance;
        if (current == null) {
            synchronized (DaprFacade.class) {
                if (instance == null) {
                    instance = builder().build();
                }
                current = instance;
            }
        }
        return current;
    }

    /**
     * Makes {@code facade} the one {@link #getInstance()} returns (the Spring auto-configuration
     * registers its bean here so code calling {@code getInstance()} shares the bean's client).
     *
     * @param facade the facade, or {@code null} to clear
     */
    public static void setInstance(DaprFacade facade) {
        synchronized (DaprFacade.class) {
            instance = facade;
        }
    }

    // ==================== Building-block views (contract names) ====================

    /** State on the default state store ({@code statestore}). */
    public DaprStateOperations state() {
        return stateOperations;
    }

    /** A TTL cache on the default state store, keys namespaced {@code cache:}. */
    public DaprCacheOperations cache() {
        return cacheOperations;
    }

    /** Secrets from the default secret store ({@code secretstore}). */
    public DaprSecretOperations secrets() {
        return secretOperations;
    }

    /** Configuration from the default configuration store ({@code configstore}). */
    public DaprConfigurationOperations configuration() {
        return configurationOperations;
    }

    /** Output bindings. */
    public DaprBindingOperations bindings() {
        return bindingOperations;
    }

    /** Distributed lock on the default lock store ({@code lockstore}). */
    public DaprLockOperations lock() {
        return lockOperations;
    }

    /** Dapr Jobs (scheduler). */
    public DaprJobOperations jobs() {
        return jobOperations;
    }

    /** Actor calls from outside the actor. */
    public DaprActorOperations actors() {
        return actorOperations;
    }

    // ==================== State Management ====================

    public <T> void saveState(String storeName, String key, T value) {
        saveState(storeName, key, value, SaveOptions.none());
    }

    /**
     * Saves a value with options (ETag, TTL, concurrency, consistency, metadata). A conflicting
     * ETag fails with a {@link DaprError} whose {@link DaprError#isConflict()} is {@code true}.
     */
    public <T> void saveState(String storeName, String key, T value, SaveOptions options) {
        SaveOptions opts = options == null ? SaveOptions.none() : options;
        call("state", "save", storeName, "Failed to save state", () -> {
            backend.saveState(storeName, key, value, opts);
            return null;
        });
        log.debug("Saved state: store={}, key={}", storeName, key);
    }

    public <T> boolean saveStateWithETag(String storeName, String key, T value, String etag) {
        try {
            saveState(storeName, key, value, SaveOptions.etag(etag));
            return true;
        } catch (DaprError e) {
            if (isEtagConflict(e)) {
                log.warn("ETag mismatch for state: store={}, key={}", storeName, key);
                return false;
            }
            throw e;
        }
    }

    public <T> void saveStateWithTTL(String storeName, String key, T value, Duration ttl) {
        saveState(storeName, key, value, ttl == null || ttl.isZero() || ttl.isNegative()
                ? SaveOptions.none() : SaveOptions.ttl(ttl));
    }

    public void saveBulkState(String storeName, Map<String, ?> values) {
        List<StateItem> items = new ArrayList<>(values.size());
        values.forEach((key, value) -> items.add(StateItem.of(key, value)));
        saveBulkState(storeName, items);
    }

    /** Saves many values in one call (not atomic: use a transaction for all-or-nothing). */
    public void saveBulkState(String storeName, List<StateItem> items) {
        if (items.isEmpty()) {
            return;
        }
        call("state", "saveBulk", storeName, "Failed to save state in bulk", () -> {
            backend.saveBulkState(storeName, items);
            return null;
        });
    }

    public <T> T getState(String storeName, String key, Class<T> clazz) {
        return getState(storeName, key, TypeRef.get(clazz), GetOptions.none());
    }

    /** Reads a value of a generic type, e.g. {@code new TypeRef<List<Order>>() {}}. */
    public <T> T getState(String storeName, String key, TypeRef<T> type) {
        return getState(storeName, key, type, GetOptions.none());
    }

    public <T> T getState(String storeName, String key, Class<T> clazz, GetOptions options) {
        return getState(storeName, key, TypeRef.get(clazz), options);
    }

    public <T> T getState(String storeName, String key, TypeRef<T> type, GetOptions options) {
        T value = getStateEntry(storeName, key, type, options).value();
        log.debug("Retrieved state: store={}, key={}, found={}", storeName, key, value != null);
        return value;
    }

    /** Reads a value with its ETag. */
    public <T> StateEntry<T> getStateEntry(String storeName, String key, TypeRef<T> type, GetOptions options) {
        GetOptions opts = options == null ? GetOptions.none() : options;
        return call("state", "get", storeName, "Failed to get state",
                () -> backend.getState(storeName, key, type, opts));
    }

    public <T> StateWithETag<T> getStateWithETag(String storeName, String key, Class<T> clazz) {
        StateEntry<T> entry = getStateEntry(storeName, key, TypeRef.get(clazz), GetOptions.none());
        return new StateWithETag<>(entry.value(), entry.etag());
    }

    public <T> Map<String, T> getBulkState(String storeName, List<String> keys, Class<T> clazz) {
        Map<String, T> values = new LinkedHashMap<>();
        for (StateEntry<T> entry : getBulkStateEntries(storeName, keys, TypeRef.get(clazz))) {
            if (entry.value() != null) {
                values.put(entry.key(), entry.value());
            }
        }
        return values;
    }

    /** Reads many keys in one call, with ETags; missing keys come back with a {@code null} value. */
    public <T> List<StateEntry<T>> getBulkStateEntries(String storeName, List<String> keys, TypeRef<T> type) {
        if (keys.isEmpty()) {
            return List.of();
        }
        return call("state", "getBulk", storeName, "Failed to get bulk state",
                () -> backend.getBulkState(storeName, keys, type, Map.of()));
    }

    public void deleteState(String storeName, String key) {
        deleteState(storeName, key, DeleteOptions.none());
    }

    public void deleteState(String storeName, String key, DeleteOptions options) {
        DeleteOptions opts = options == null ? DeleteOptions.none() : options;
        call("state", "delete", storeName, "Failed to delete state", () -> {
            backend.deleteState(storeName, key, opts);
            return null;
        });
        log.debug("Deleted state: store={}, key={}", storeName, key);
    }

    public boolean deleteStateWithETag(String storeName, String key, String etag) {
        try {
            deleteState(storeName, key, DeleteOptions.etag(etag));
            return true;
        } catch (DaprError e) {
            if (isEtagConflict(e)) {
                log.warn("ETag mismatch for delete: store={}, key={}", storeName, key);
                return false;
            }
            throw e;
        }
    }

    public void executeStateTransaction(String storeName, List<StateOperation> operations) {
        executeStateTransaction(storeName, operations, Map.of());
    }

    /**
     * Applies upserts and deletes atomically. On a store configured with Dapr's transactional
     * outbox ({@code outboxPublishPubsub}), the same transaction publishes the change: add a
     * {@link StateOperation#projection} to choose what subscribers receive.
     */
    public void executeStateTransaction(String storeName, List<StateOperation> operations,
                                        Map<String, String> metadata) {
        call("state", "transact", storeName, "Failed to execute state transaction", () -> {
            backend.executeStateTransaction(storeName, operations, metadata == null ? Map.of() : metadata);
            return null;
        });
        log.debug("Executed state transaction: store={}, operations={}", storeName, operations.size());
    }

    /**
     * Runs a state query (Dapr alpha query API) and returns the matching values of the first page.
     *
     * @param query the query JSON ({@code {"filter": {"EQ": {"status": "open"}}, "page": {"limit": 10}}})
     */
    public <T> List<T> queryState(String storeName, String query, Class<T> clazz) {
        return queryStatePage(storeName, query, TypeRef.get(clazz), Map.of()).values();
    }

    /** Runs a state query and returns one page with keys, ETags and the continuation token. */
    public <T> StateQueryResult<T> queryStatePage(String storeName, String query, TypeRef<T> type,
                                                  Map<String, String> metadata) {
        return call("state", "query", storeName, "Failed to query state",
                () -> backend.queryState(storeName, query, type, metadata == null ? Map.of() : metadata));
    }

    // ==================== Pub/Sub ====================

    /** Publishes to the default pub/sub component. */
    public void publish(String topic, Object data) {
        publish(topic, data, PublishOptions.none());
    }

    /** Publishes with options (component, CloudEvent attributes, TTL, partition key, ...). */
    public void publish(String topic, Object data, PublishOptions options) {
        PublishOptions opts = options == null ? PublishOptions.none() : options;
        String pubsub = opts.pubsub() != null ? opts.pubsub() : components.pubsub();
        call("pubsub", "publish", pubsub, "Failed to publish event", () -> {
            backend.publishEvent(pubsub, topic, data, opts);
            return null;
        });
        log.debug("Published event: pubsub={}, topic={}", pubsub, topic);
    }

    public <T> void publishEvent(String pubsubName, String topicName, T event) {
        publish(topicName, event, PublishOptions.none().withPubsub(pubsubName));
    }

    public <T> void publishEvent(String pubsubName, String topicName, T event, Map<String, String> metadata) {
        publish(topicName, event, new PublishOptions(pubsubName, metadata, null));
    }

    /** Publishes many events in one broker call; each entry is accepted or rejected on its own. */
    public BulkPublishResult publishBulk(String topic, List<?> events) {
        return publishBulk(topic, events, PublishOptions.none());
    }

    public BulkPublishResult publishBulk(String topic, List<?> events, PublishOptions options) {
        PublishOptions opts = options == null ? PublishOptions.none() : options;
        String pubsub = opts.pubsub() != null ? opts.pubsub() : components.pubsub();
        if (events.isEmpty()) {
            return new BulkPublishResult(0, List.of());
        }
        return call("pubsub", "publishBulk", pubsub, "Failed to publish events in bulk",
                () -> backend.publishEvents(pubsub, topic, events, opts));
    }

    /**
     * Publishes many events with the broker's native bulk publish.
     *
     * @throws DaprError when the broker rejected any entry (the count is on the message)
     */
    public <T> void publishBulkEvents(String pubsubName, String topicName, List<T> events) {
        BulkPublishResult result = publishBulk(topicName, events, PublishOptions.none().withPubsub(pubsubName));
        if (!result.allSucceeded()) {
            throw new DaprError("Failed to publish bulk events: " + result.failed().size() + " of " + events.size()
                    + " rejected (first: " + result.failed().getFirst().error() + ")", 500,
                    "ERR_PUBSUB_PUBLISH_MESSAGE", result.failed().getFirst().error());
        }
        log.debug("Published {} events: pubsub={}, topic={}", events.size(), pubsubName, topicName);
    }

    /**
     * Subscribes a handler to a topic on the default pub/sub component.
     *
     * @see #subscribe(String, TypeRef, DaprTopicHandler, SubscribeOptions)
     */
    public <T> DaprSubscription subscribe(String topic, Class<T> type, DaprTopicHandler<T> handler) {
        return subscribe(topic, TypeRef.get(type), handler, SubscribeOptions.none());
    }

    /**
     * Subscribes a handler. In a Spring MVC application the handler is advertised to the sidecar
     * on {@code GET /dapr/subscribe} like an annotated one (register it while the application
     * starts: Dapr reads the list once); elsewhere it becomes a streaming subscription. In memory
     * mode it receives this process's own publishes.
     */
    public <T> DaprSubscription subscribe(String topic, TypeRef<T> type, DaprTopicHandler<T> handler,
                                          SubscribeOptions options) {
        SubscribeOptions opts = options == null ? SubscribeOptions.none() : options;
        String pubsub = opts.pubsub() != null ? opts.pubsub() : components.pubsub();
        SubscribeOptions resolved = opts.withPubsub(pubsub);
        ProgrammaticSubscriptions programmatic = programmaticSubscriptions;
        if (programmatic != null) {
            return programmatic.register(topic, type, handler, resolved);
        }
        return subscribeStreaming(pubsub, topic, resolved.deadLetterTopic(), resolved.metadata(),
                envelope -> dispatch(envelope, type, handler));
    }

    /**
     * Subscribes a raw listener at runtime (a streaming subscription on the sidecar, a local one
     * in memory mode), for subscribers whose topics are only known after startup.
     */
    public DaprSubscription subscribeStreaming(String pubsubName, String topic, String deadLetterTopic,
                                               Map<String, String> metadata, TopicEventListener listener) {
        return call("pubsub", "subscribe", pubsubName, "Failed to subscribe to " + topic,
                () -> backend.subscribeTopic(pubsubName, topic, deadLetterTopic,
                        metadata == null ? Map.of() : metadata, listener));
    }

    /**
     * Installs where {@link #subscribe} registers handlers (done by the Spring MVC integration).
     *
     * @param subscriptions the registration target, or {@code null}
     */
    public void setProgrammaticSubscriptions(ProgrammaticSubscriptions subscriptions) {
        this.programmaticSubscriptions = subscriptions;
    }

    private <T> DispatchStatus dispatch(Map<String, Object> envelope, TypeRef<T> type, DaprTopicHandler<T> handler) {
        try {
            @SuppressWarnings("unchecked")
            T data = (T) eventData.data(envelope, type.getType());
            DispatchStatus status = handler.handle(DaprCloudEvent.of(envelope, data));
            return status == null ? DispatchStatus.SUCCESS : status;
        } catch (DropEventException e) {
            log.warn("Dropping event {} on topic {}: {}", envelope.get("id"), envelope.get("topic"), e.getMessage());
            return DispatchStatus.DROP;
        } catch (Exception e) {
            log.warn("Handler for topic {} failed on event {}; asking for redelivery", envelope.get("topic"),
                    envelope.get("id"), e);
            return DispatchStatus.RETRY;
        }
    }

    // ==================== Service Invocation ====================

    /**
     * Invokes a method of another app through the sidecar's HTTP API
     * ({@code /v1.0/invoke/<appId>/method/<method>}) with any verb, headers, query and timeout.
     * Calls are guarded by a circuit breaker per target app id, and idempotent verbs are retried
     * on connection failures and 502/503/504.
     *
     * @return the 2xx response; any other status throws a {@link DaprError} carrying it and the body
     */
    public InvokeResponse invoke(String appId, String method, InvokeOptions options) {
        InvokeOptions opts = options == null ? InvokeOptions.of(HttpVerb.POST) : options;
        DaprBackend.InvokeRequest request = toInvokeRequest(opts);
        DaprInvocationResilience target = resilience.forTarget(appId);
        return call("invoke", opts.verb().name().toLowerCase(Locale.ROOT), appId,
                "Failed to invoke " + appId + "/" + method,
                () -> target.call(() -> backend.invoke(appId, method, request),
                        failure -> opts.verb().isIdempotent() && isTransient(failure)));
    }

    /** Invokes and reads the JSON response as {@code responseType}. */
    public <T> T invoke(String appId, String method, InvokeOptions options, Class<T> responseType) {
        return readBody(invoke(appId, method, options), TypeRef.get(responseType));
    }

    /** Invokes and reads the JSON response as a generic type. */
    public <T> T invoke(String appId, String method, InvokeOptions options, TypeRef<T> responseType) {
        return readBody(invoke(appId, method, options), responseType);
    }

    /** Invokes with {@code POST}. */
    public <T, R> R invokeService(String appId, String methodName, T request, Class<R> responseClass) {
        return invokeService(appId, methodName, "POST", request, responseClass);
    }

    public <T, R> CompletableFuture<R> invokeServiceAsync(String appId, String methodName, T request,
                                                          Class<R> responseClass) {
        return CompletableFuture.supplyAsync(
                () -> invokeService(appId, methodName, "POST", request, responseClass), ASYNC);
    }

    /**
     * Invokes with the given verb ({@code GET}, {@code POST}, {@code PUT}, {@code PATCH},
     * {@code DELETE}, {@code HEAD}, {@code OPTIONS}); an unknown verb is rejected.
     */
    public <T, R> R invokeService(String appId, String methodName, String httpMethod, T request,
                                  Class<R> responseClass) {
        HttpVerb verb = HttpVerb.parse(httpMethod);
        InvokeOptions options = InvokeOptions.of(verb);
        if (request != null && verb != HttpVerb.GET && verb != HttpVerb.HEAD) {
            options = options.withBody(request);
        }
        R result = invoke(appId, methodName, options, responseClass);
        log.debug("Invoked service: appId={}, method={}, httpMethod={}", appId, methodName, verb);
        return result;
    }

    /**
     * Invokes with retry, per-attempt timeout and the target's circuit breaker applied, throwing on
     * final failure.
     */
    public <T, R> R invokeServiceResilient(String appId, String methodName, T request, Class<R> responseClass) {
        return invokeServiceResilient(appId, methodName, "POST", request, responseClass, null);
    }

    /**
     * Invokes with retry (every verb), per-attempt timeout and the target's circuit breaker
     * applied, falling back to {@code fallback} instead of throwing on final failure (or while
     * the breaker is open).
     */
    public <T, R> R invokeServiceResilient(String appId, String methodName, String httpMethod, T request,
                                           Class<R> responseClass, Function<Throwable, R> fallback) {
        return resilience.forTarget(appId).execute(
                () -> invokeService(appId, methodName, httpMethod, request, responseClass), fallback);
    }

    /** The per-target resilience (breakers) used for invocation. */
    public DaprResilienceRegistry resilience() {
        return resilience;
    }

    private DaprBackend.InvokeRequest toInvokeRequest(InvokeOptions options) {
        byte[] body = null;
        String contentType = options.contentType();
        Object raw = options.body();
        if (raw instanceof byte[] bytes) {
            body = bytes;
            contentType = contentType != null ? contentType : "application/octet-stream";
        } else if (raw instanceof String text) {
            body = text.getBytes(StandardCharsets.UTF_8);
            contentType = contentType != null ? contentType : "text/plain; charset=utf-8";
        } else if (raw != null) {
            body = serializer.toBytes(raw);
            contentType = contentType != null ? contentType : "application/json";
        }
        return new DaprBackend.InvokeRequest(options.verb().name(), body, contentType, options.headers(),
                options.query(), options.timeout());
    }

    private <T> T readBody(InvokeResponse response, TypeRef<T> type) {
        if (type.getType() == Void.class || type.getType() == void.class || response.body().length == 0) {
            return null;
        }
        if (type.getType() == byte[].class) {
            @SuppressWarnings("unchecked")
            T bytes = (T) response.body();
            return bytes;
        }
        if (type.getType() == String.class && !isJson(response.header("content-type"))) {
            @SuppressWarnings("unchecked")
            T text = (T) response.bodyAsString();
            return text;
        }
        try {
            return serializer.fromBytes(response.body(), type);
        } catch (RuntimeException e) {
            throw new DaprError("Failed to read the invocation response as " + type.getType().getTypeName(),
                    response.status(), "ERR_DESERIALIZE", e.getMessage(), Map.of(), response.body(), e);
        }
    }

    private static boolean isJson(String contentType) {
        return contentType == null || contentType.toLowerCase(Locale.ROOT).contains("json");
    }

    private static boolean isTransient(Throwable failure) {
        return failure instanceof DaprError error && error.isUnavailable();
    }

    // ==================== Bindings ====================

    public <T> void invokeBinding(String bindingName, String operation, T data) {
        invokeBinding(bindingName, operation, data, Collections.emptyMap());
    }

    public <T> void invokeBinding(String bindingName, String operation, T data, Map<String, String> metadata) {
        call("bindings", "invoke", bindingName, "Failed to invoke binding", () ->
                backend.invokeBinding(bindingName, operation, data, metadata == null ? Map.of() : metadata));
        log.debug("Invoked binding: binding={}, operation={}", bindingName, operation);
    }

    /** Invokes an output binding and reads its response data as {@code responseType}. */
    public <T> T invokeBinding(String bindingName, String operation, Object data, Map<String, String> metadata,
                               Class<T> responseType) {
        return invokeBinding(bindingName, operation, data, metadata, TypeRef.get(responseType));
    }

    public <T> T invokeBinding(String bindingName, String operation, Object data, Map<String, String> metadata,
                               TypeRef<T> responseType) {
        byte[] response = call("bindings", "invoke", bindingName, "Failed to invoke binding", () ->
                backend.invokeBinding(bindingName, operation, data, metadata == null ? Map.of() : metadata));
        return response == null || response.length == 0 ? null : serializer.fromBytes(response, responseType);
    }

    /** Invokes an output binding and returns its data together with its response metadata. */
    public BindingResponse invokeBindingWithResponse(String bindingName, String operation, Object data,
                                                     Map<String, String> metadata) {
        return call("bindings", "invoke", bindingName, "Failed to invoke binding", () ->
                backend.invokeBindingWithResponse(bindingName, operation, data, metadata == null ? Map.of() : metadata));
    }

    // ==================== Secrets ====================

    /**
     * The value of a secret: the entry named like the secret (local file and env stores), or the
     * only entry of a single-key secret.
     */
    public String getSecret(String secretStoreName, String secretKey) {
        return getSecret(secretStoreName, secretKey, Collections.emptyMap());
    }

    public String getSecret(String secretStoreName, String secretKey, Map<String, String> metadata) {
        Map<String, String> secret = getSecretValues(secretStoreName, secretKey, metadata);
        String value = secret.get(secretKey);
        if (value == null && secret.size() == 1) {
            value = secret.values().iterator().next();
        }
        log.debug("Retrieved secret: store={}, key={}, found={}", secretStoreName, secretKey, value != null);
        return value;
    }

    /** Every key/value pair of a secret (a Kubernetes secret carries several). */
    public Map<String, String> getSecretValues(String secretStoreName, String secretName) {
        return getSecretValues(secretStoreName, secretName, Map.of());
    }

    public Map<String, String> getSecretValues(String secretStoreName, String secretName,
                                               Map<String, String> metadata) {
        return call("secrets", "get", secretStoreName, "Failed to get secret", () ->
                backend.getSecret(secretStoreName, secretName, metadata == null ? Map.of() : metadata));
    }

    /**
     * Every secret of a store, flattened losslessly: a single-entry secret keeps its name, a
     * multi-entry secret contributes one {@code name.key} entry per value.
     */
    public Map<String, String> getBulkSecrets(String secretStoreName) {
        Map<String, String> result = new LinkedHashMap<>();
        getBulkSecretsNested(secretStoreName).forEach((name, valueMap) -> {
            if (valueMap == null || valueMap.isEmpty()) {
                return;
            }
            if (valueMap.size() == 1) {
                result.put(name, valueMap.values().iterator().next());
            } else {
                valueMap.forEach((k, v) -> result.put(name + "." + k, v));
            }
        });
        return result;
    }

    /** Every secret in the store with its full key/value map. */
    public Map<String, Map<String, String>> getBulkSecretsNested(String secretStoreName) {
        return call("secrets", "getBulk", secretStoreName, "Failed to get bulk secrets",
                () -> backend.getBulkSecret(secretStoreName, Map.of()));
    }

    // ==================== Configuration ====================

    public String getConfiguration(String configStoreName, String key) {
        return getConfiguration(configStoreName, List.of(key)).get(key);
    }

    public Map<String, String> getConfiguration(String configStoreName, List<String> keys) {
        Map<String, String> values = new LinkedHashMap<>();
        getConfigurationItems(configStoreName, keys).forEach((key, item) -> {
            if (item.value() != null) {
                values.put(key, item.value());
            }
        });
        return values;
    }

    /** Configuration items with their versions and metadata. */
    public Map<String, ConfigItem> getConfigurationItems(String configStoreName, List<String> keys) {
        return call("configuration", "get", configStoreName, "Failed to get configuration",
                () -> backend.getConfiguration(configStoreName, keys, Map.of()));
    }

    /**
     * Subscribes to changes of {@code keys} (all keys when empty). The callback receives the
     * changed keys; a deleted key arrives with a {@code null} value.
     *
     * @return the subscription; {@link DaprSubscription#unsubscribe()} ends it
     */
    public DaprSubscription subscribeConfiguration(String configStoreName, List<String> keys,
                                                   ConfigurationCallback callback) {
        DaprSubscription subscription = call("configuration", "subscribe", configStoreName,
                "Failed to subscribe to configuration", () -> backend.subscribeConfiguration(configStoreName,
                        keys, Map.of(), changed -> {
                            Map<String, String> values = new HashMap<>();
                            changed.forEach((key, item) -> values.put(key, item.value()));
                            callback.onConfigurationChange(values);
                        },
                        error -> log.warn("Configuration subscription on store '{}' ended: {}",
                                configStoreName, error.getMessage())));
        log.debug("Subscribed to configuration changes: store={}, keys={}", configStoreName, keys);
        return subscription;
    }

    /**
     * Subscribes to changes with the full items (value, version, metadata).
     *
     * @return the subscription; {@link DaprSubscription#unsubscribe()} ends it
     */
    public DaprSubscription subscribeConfigurationItems(String configStoreName, List<String> keys,
                                                        java.util.function.Consumer<Map<String, ConfigItem>> onChange) {
        return call("configuration", "subscribe", configStoreName, "Failed to subscribe to configuration",
                () -> backend.subscribeConfiguration(configStoreName, keys, Map.of(), onChange,
                        error -> log.warn("Configuration subscription on store '{}' ended: {}",
                                configStoreName, error.getMessage())));
    }

    // ==================== Distributed Lock ====================

    /**
     * Tries to take a lock for {@code expiryInSeconds}; returns immediately with {@code false} if
     * someone else holds it. Locks are not re-entrant.
     */
    public boolean tryLock(String storeName, String resourceId, String lockOwner, int expiryInSeconds) {
        boolean acquired = call("lock", "tryLock", storeName, "Failed to acquire lock",
                () -> backend.tryLock(storeName, resourceId, lockOwner, expiryInSeconds));
        log.debug("tryLock: store={}, resource={}, owner={}, acquired={}", storeName, resourceId, lockOwner, acquired);
        return acquired;
    }

    /** Releases a lock; {@code true} only when it was held by {@code lockOwner}. */
    public boolean unlock(String storeName, String resourceId, String lockOwner) {
        return unlockStatus(storeName, resourceId, lockOwner) == UnlockStatus.SUCCESS;
    }

    /** Releases a lock and reports why it was or was not released. */
    public UnlockStatus unlockStatus(String storeName, String resourceId, String lockOwner) {
        UnlockStatus status = call("lock", "unlock", storeName, "Failed to release lock",
                () -> backend.unlock(storeName, resourceId, lockOwner));
        log.debug("unlock: store={}, resource={}, owner={}, status={}", storeName, resourceId, lockOwner, status);
        return status;
    }

    // ==================== Jobs ====================

    /** Schedules (or with {@link JobOptions#overwriting()} replaces) a Dapr job. */
    public void scheduleJob(String name, JobOptions options) {
        call("jobs", "schedule", name, "Failed to schedule job '" + name + "'", () -> {
            backend.scheduleJob(name, options);
            return null;
        });
    }

    /** The job, or {@code null} when no job of that name exists. */
    public JobDetails getJob(String name) {
        try {
            return call("jobs", "get", name, "Failed to get job '" + name + "'", () -> backend.getJob(name));
        } catch (DaprError e) {
            if (e.isNotFound() || containsIgnoreCase(e.getDaprMessage(), "not found")) {
                return null;
            }
            throw e;
        }
    }

    public void deleteJob(String name) {
        call("jobs", "delete", name, "Failed to delete job '" + name + "'", () -> {
            backend.deleteJob(name);
            return null;
        });
    }

    // ==================== Actors ====================

    /** Invokes an actor method from outside the actor; the data and response are JSON. */
    public <T, R> R invokeActor(String actorType, String actorId, String methodName, T data, Class<R> responseClass) {
        byte[] response = call("actors", "invoke", actorType, "Failed to invoke actor " + actorType,
                () -> backend.invokeActor(actorType, actorId, methodName, data == null ? null : serializer.toBytes(data)));
        if (response == null || response.length == 0 || responseClass == Void.class) {
            return null;
        }
        return serializer.fromBytes(response, TypeRef.get(responseClass));
    }

    /** Saves actor state. Dapr only accepts this from the app hosting the actor type. */
    public <T> void saveActorState(String actorType, String actorId, String stateName, T value) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operation", "upsert");
        operation.put("request", Map.of("key", stateName, "value", value));
        call("actors", "saveState", actorType, "Failed to save actor state", () -> {
            backend.executeActorStateTransaction(actorType, actorId, List.of(operation));
            return null;
        });
    }

    /** Reads actor state, or {@code null}. Dapr only accepts this from the app hosting the actor type. */
    public <T> T getActorState(String actorType, String actorId, String stateName, Class<T> clazz) {
        byte[] state = call("actors", "getState", actorType, "Failed to get actor state",
                () -> backend.getActorState(actorType, actorId, stateName));
        return state == null || state.length == 0 ? null : serializer.fromBytes(state, TypeRef.get(clazz));
    }

    /** Registers a persistent reminder on an actor. */
    public void registerActorReminder(String actorType, String actorId, String reminderName,
                                      Duration dueTime, Duration period, Object data) {
        call("actors", "registerReminder", actorType, "Failed to register actor reminder", () -> {
            backend.registerActorReminder(actorType, actorId, reminderName, dueTime, period, data);
            return null;
        });
    }

    /** Registers a timer on an actor. Dapr only accepts this from the app hosting the actor type. */
    public void registerActorTimer(String actorType, String actorId, String timerName,
                                   Duration dueTime, Duration period, String callback) {
        call("actors", "registerTimer", actorType, "Failed to register actor timer", () -> {
            backend.registerActorTimer(actorType, actorId, timerName, dueTime, period, callback, null);
            return null;
        });
    }

    // ==================== Cryptography ====================

    /**
     * Encrypts with a key held by a Dapr cryptography component (the key never reaches the app).
     *
     * @param component        the crypto component
     * @param keyName          the key name in that component
     * @param keyWrapAlgorithm the key wrap algorithm (for example {@code RSA-OAEP-256}, {@code A256KW})
     * @param plaintext        the data
     * @return the ciphertext (Dapr's envelope format; decrypt with {@link #decrypt})
     */
    public byte[] encrypt(String component, String keyName, String keyWrapAlgorithm, byte[] plaintext) {
        return call("crypto", "encrypt", component, "Failed to encrypt",
                () -> backend.encrypt(component, keyName, keyWrapAlgorithm, plaintext));
    }

    /**
     * Decrypts data produced by {@link #encrypt}.
     *
     * @param keyName the key name, or {@code null} to use the one recorded in the ciphertext
     */
    public byte[] decrypt(String component, byte[] ciphertext, String keyName) {
        return call("crypto", "decrypt", component, "Failed to decrypt",
                () -> backend.decrypt(component, ciphertext, keyName));
    }

    // ==================== Runtime ====================

    /**
     * Polls the sidecar's outbound health ({@code GET /v1.0/healthz/outbound}) until it answers
     * or {@code timeout} passes. Logs once when the sidecar is first reached.
     *
     * @return {@code true} when the sidecar is ready (always in memory mode)
     */
    public boolean waitForSidecar(Duration timeout) {
        if (backend.mode() == DaprMode.MEMORY) {
            available = true;
            return true;
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            long remaining = Math.max(1, (deadline - System.nanoTime()) / 1_000_000);
            if (backend.probe(Duration.ofMillis(Math.min(remaining, 2000)))) {
                available = true;
                if (connectedLogged.compareAndSet(false, true)) {
                    log.info("Dapr sidecar connected successfully ({}, app id '{}')", connection.httpEndpoint(),
                            connection.appId());
                }
                return true;
            }
            try {
                Thread.sleep(Math.min(PROBE_INTERVAL.toMillis(), remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (System.nanoTime() < deadline);
        available = false;
        return false;
    }

    /**
     * Whether the sidecar was reachable the last time the kit looked (startup wait, health check
     * or the last call). Never blocks.
     */
    public boolean isAvailable() {
        return available;
    }

    /** Probes the sidecar once ({@code /v1.0/healthz/outbound}) and records the result. */
    public boolean checkSidecar(Duration timeout) {
        boolean healthy = backend.probe(timeout);
        available = healthy;
        return healthy;
    }

    /** The sidecar's metadata: app id, runtime version, components, subscriptions, ... */
    public DaprMetadata metadata() {
        return call("metadata", "get", "sidecar", "Failed to get Dapr metadata", backend::metadata);
    }

    /**
     * The sidecar's metadata as a plain map (for diagnostics endpoints). Never throws: an
     * unreachable sidecar yields {@code available=false} and the error.
     */
    public Map<String, Object> getMetadata() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", backend.mode().name().toLowerCase(Locale.ROOT));
        result.put("appId", connection.appId());
        result.put("httpEndpoint", connection.httpEndpoint());
        try {
            DaprMetadata metadata = metadata();
            result.put("available", true);
            result.put("id", metadata.getId());
            result.put("runtimeVersion", metadata.getRuntimeVersion());
            result.put("enabledFeatures", metadata.getEnabledFeatures());
            List<Map<String, Object>> componentList = new ArrayList<>();
            for (ComponentMetadata component : metadata.getComponents()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("name", component.getName());
                c.put("type", component.getType());
                c.put("version", component.getVersion());
                c.put("capabilities", component.getCapabilities());
                componentList.add(c);
            }
            result.put("components", componentList);
            List<Map<String, Object>> subscriptionList = new ArrayList<>();
            metadata.getSubscriptions().forEach(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("pubsubname", s.getPubsubname());
                m.put("topic", s.getTopic());
                m.put("deadLetterTopic", s.getDeadLetterTopic());
                subscriptionList.add(m);
            });
            result.put("subscriptions", subscriptionList);
        } catch (RuntimeException e) {
            result.put("available", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /** Asks the sidecar to shut down gracefully ({@code POST /v1.0/shutdown}); used by jobs that end. */
    public void shutdownSidecar() {
        call("runtime", "shutdown", "sidecar", "Failed to shut down the Dapr sidecar", () -> {
            backend.shutdownSidecar();
            return null;
        });
    }

    /** The effective mode: sidecar or memory. */
    public DaprMode mode() {
        return backend.mode();
    }

    /** The resolved sidecar connection. */
    public DaprConnectionDetails connection() {
        return connection;
    }

    /** The default component names. */
    public DaprComponents components() {
        return components;
    }

    /** The serializer used for payloads. */
    public DaprJsonSerializer serializer() {
        return serializer;
    }

    /** What the building blocks run on. */
    public DaprBackend backend() {
        return backend;
    }

    /**
     * The underlying SDK client, for an SDK API the kit does not wrap.
     *
     * @throws IllegalStateException in memory mode
     */
    public DaprClient client() {
        if (backend instanceof SidecarDaprBackend sidecar) {
            return sidecar.client();
        }
        throw new IllegalStateException("No Dapr client in " + backend.mode() + " mode");
    }

    /** The in-memory backend (memory mode only), to seed secrets or change configuration in tests. */
    public InMemoryDaprBackend memory() {
        if (backend instanceof InMemoryDaprBackend memory) {
            return memory;
        }
        throw new IllegalStateException("Dapr runs in " + backend.mode() + " mode, not memory");
    }

    /**
     * Closes the client this facade created (a client handed in by Spring is closed by Spring).
     * Does not touch the sidecar: see {@link #shutdownSidecar()}.
     */
    @Override
    public void close() {
        available = false;
        try {
            backend.close();
            log.info("Dapr client shut down successfully");
        } catch (RuntimeException e) {
            log.error("Error shutting down Dapr client", e);
        }
        synchronized (DaprFacade.class) {
            if (instance == this) {
                instance = null;
            }
        }
    }

    /**
     * Closes the local client.
     *
     * @deprecated renamed {@link #close()} to make clear it does not stop the sidecar
     *     (that is {@link #shutdownSidecar()}).
     */
    @Deprecated(since = "0.2.0")
    public void shutdown() {
        close();
    }

    // ==================== Internals ====================

    <T> T call(String api, String operation, String component, String failure, Supplier<T> body) {
        try {
            T result = observations.observe(api, operation, component, () -> {
                try {
                    return body.get();
                } catch (DaprError | IllegalArgumentException | IllegalStateException
                         | UnsupportedOperationException e) {
                    throw e;
                } catch (RuntimeException e) {
                    throw DaprError.from(failure, e);
                }
            });
            if (backend.mode() == DaprMode.SIDECAR) {
                available = true;
            }
            return result;
        } catch (DaprError e) {
            if (e.getStatus() == DaprError.NO_RESPONSE) {
                available = false;
            }
            log.debug("{} (store/target '{}'): {}", failure, component, e.getMessage());
            throw e;
        }
    }

    static boolean isEtagConflict(DaprError e) {
        return e.isConflict() || containsIgnoreCase(e.getDaprMessage(), "etag")
                || containsIgnoreCase(e.getMessage(), "etag");
    }

    private static boolean containsIgnoreCase(String text, String part) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(part);
    }

    /**
     * Custom exception for Dapr operations. Failures are raised as its subclass {@link DaprError},
     * which carries what Dapr reported.
     */
    public static class DaprException extends RuntimeException {
        public DaprException(String message) {
            super(message);
        }

        public DaprException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Builds a facade. Without explicit settings it resolves everything from the environment: the
     * connection ({@link DaprConnectionDetails#fromEnvironment()}), the component names, and the
     * mode, building one SDK client (which is both the client and the preview client) in sidecar
     * mode.
     */
    public static final class Builder {
        private DaprConnectionDetails connection;
        private DaprComponents components;
        private DaprClient client;
        private DaprPreviewClient previewClient;
        private DaprBackend backend;
        private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
        private DaprTraceContext traceContext = DaprTraceContext.NONE;
        private ResilienceSettings resilienceSettings = ResilienceSettings.defaults();
        private DaprJsonSerializer serializer;
        private Duration httpTimeout = Duration.ofSeconds(60);
        private final Map<String, Map<String, Map<String, String>>> memorySecrets = new LinkedHashMap<>();
        private final Map<String, Map<String, String>> memoryConfiguration = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder connection(DaprConnectionDetails value) {
            this.connection = value;
            return this;
        }

        public Builder components(DaprComponents value) {
            this.components = value;
            return this;
        }

        /** Uses an existing client (not closed by the facade). Implies sidecar mode. */
        public Builder client(DaprClient value) {
            this.client = value;
            return this;
        }

        public Builder previewClient(DaprPreviewClient value) {
            this.previewClient = value;
            return this;
        }

        /** Uses a ready backend, ignoring client settings. */
        public Builder backend(DaprBackend value) {
            this.backend = value;
            return this;
        }

        public Builder observationRegistry(ObservationRegistry value) {
            this.observationRegistry = value == null ? ObservationRegistry.NOOP : value;
            return this;
        }

        public Builder traceContext(DaprTraceContext value) {
            this.traceContext = value == null ? DaprTraceContext.NONE : value;
            return this;
        }

        public Builder resilience(ResilienceSettings value) {
            this.resilienceSettings = value == null ? ResilienceSettings.defaults() : value;
            return this;
        }

        public Builder serializer(DaprJsonSerializer value) {
            this.serializer = value;
            return this;
        }

        /** Default timeout of sidecar HTTP API calls (invocation, actors) without their own. */
        public Builder httpTimeout(Duration value) {
            this.httpTimeout = value;
            return this;
        }

        /** Seeds a secret for memory mode. */
        public Builder memorySecret(String store, String name, Map<String, String> values) {
            memorySecrets.computeIfAbsent(store, s -> new LinkedHashMap<>()).put(name, values);
            return this;
        }

        /** Seeds a configuration value for memory mode. */
        public Builder memoryConfiguration(String store, String key, String value) {
            memoryConfiguration.computeIfAbsent(store, s -> new LinkedHashMap<>()).put(key, value);
            return this;
        }

        public DaprFacade build() {
            DaprConnectionDetails conn = connection != null ? connection : DaprConnectionDetails.fromEnvironment();
            DaprComponents comps = components != null ? components : DaprComponents.fromEnvironment();
            DaprJsonSerializer json = serializer != null ? serializer : new DaprJsonSerializer();
            DaprBackend effective = backend;
            if (effective == null) {
                if (client == null && conn.isMemory()) {
                    InMemoryDaprBackend memory = new InMemoryDaprBackend(json, conn.appId());
                    memorySecrets.forEach((store, secrets) -> secrets.forEach((name, values) ->
                            memory.putSecret(store, name, values)));
                    memoryConfiguration.forEach((store, values) -> values.forEach((key, value) ->
                            memory.setConfiguration(store, key, value)));
                    log.warn("No Dapr sidecar configured (ADHAR_DAPR_MODE={}, DAPR_HTTP_PORT/DAPR_HTTP_ENDPOINT unset): "
                                    + "state, cache, pub/sub, lock, configuration and secrets run in memory for app '{}'. "
                                    + "Nothing is shared between replicas or kept across restarts.",
                            conn.requestedMode().name().toLowerCase(Locale.ROOT), conn.appId());
                    effective = memory;
                } else {
                    boolean owned = client == null;
                    DaprClient sdkClient = client != null ? client : newClient(conn, json);
                    DaprPreviewClient preview = previewClient != null ? previewClient
                            : sdkClient instanceof DaprPreviewClient p ? p : null;
                    effective = new SidecarDaprBackend(sdkClient, preview,
                            new DaprHttpClient(conn, httpTimeout, traceContext), json, traceContext, owned);
                }
            }
            return new DaprFacade(effective, conn, comps, json, new DaprObservations(observationRegistry),
                    new DaprResilienceRegistry(resilienceSettings));
        }

        /**
         * Builds the SDK client for a connection: the kit serializer for objects and state, and
         * the resolved endpoint and token as SDK property overrides.
         *
         * @param connection the connection
         * @param serializer the serializer
         * @return a client that also implements {@link DaprPreviewClient}
         */
        public static DaprClient newClient(DaprConnectionDetails connection, DaprJsonSerializer serializer) {
            return new DaprClientBuilder()
                    .withObjectSerializer(serializer)
                    .withStateSerializer(serializer)
                    .withPropertyOverrides(connection.sdkOverrides())
                    .build();
        }
    }
}
