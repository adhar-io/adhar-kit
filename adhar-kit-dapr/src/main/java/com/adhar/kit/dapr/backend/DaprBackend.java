package com.adhar.kit.dapr.backend;

import com.adhar.kit.dapr.DaprError;
import com.adhar.kit.dapr.DaprMode;
import com.adhar.kit.dapr.DaprSubscription;
import com.adhar.kit.dapr.api.StateOperation;
import com.adhar.kit.dapr.bindings.BindingResponse;
import com.adhar.kit.dapr.configuration.ConfigItem;
import com.adhar.kit.dapr.invoke.InvokeResponse;
import com.adhar.kit.dapr.jobs.JobDetails;
import com.adhar.kit.dapr.jobs.JobOptions;
import com.adhar.kit.dapr.lock.UnlockStatus;
import com.adhar.kit.dapr.pubsub.BulkPublishResult;
import com.adhar.kit.dapr.pubsub.PublishOptions;
import com.adhar.kit.dapr.pubsub.TopicEventListener;
import com.adhar.kit.dapr.state.DeleteOptions;
import com.adhar.kit.dapr.state.GetOptions;
import com.adhar.kit.dapr.state.SaveOptions;
import com.adhar.kit.dapr.state.StateEntry;
import com.adhar.kit.dapr.state.StateItem;
import com.adhar.kit.dapr.state.StateQueryResult;
import io.dapr.client.domain.DaprMetadata;
import io.dapr.utils.TypeRef;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What {@link com.adhar.kit.dapr.DaprFacade} runs its building blocks on: the sidecar
 * ({@link SidecarDaprBackend}) or the in-process implementation used in memory mode
 * ({@link com.adhar.kit.dapr.memory.InMemoryDaprBackend}).
 *
 * <p>The facade owns everything that must be identical in both modes (argument checks, defaults,
 * observations, error translation); a backend only moves data. Building blocks a backend cannot
 * provide keep the default implementation, which fails with a {@link DaprError} naming the mode.
 * Internal SPI: it may change between kit releases.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public interface DaprBackend extends AutoCloseable {

    /** {@link DaprMode#SIDECAR} or {@link DaprMode#MEMORY}. */
    DaprMode mode();

    // ---------------------------------------------------------------- state

    <T> StateEntry<T> getState(String store, String key, TypeRef<T> type, GetOptions options);

    <T> List<StateEntry<T>> getBulkState(String store, List<String> keys, TypeRef<T> type,
                                         Map<String, String> metadata);

    void saveState(String store, String key, Object value, SaveOptions options);

    void saveBulkState(String store, List<StateItem> items);

    void deleteState(String store, String key, DeleteOptions options);

    void executeStateTransaction(String store, List<StateOperation> operations, Map<String, String> metadata);

    default <T> StateQueryResult<T> queryState(String store, String query, TypeRef<T> type,
                                               Map<String, String> metadata) {
        throw unsupported("state query");
    }

    // ---------------------------------------------------------------- pub/sub

    void publishEvent(String pubsub, String topic, Object data, PublishOptions options);

    <T> BulkPublishResult publishEvents(String pubsub, String topic, List<T> events, PublishOptions options);

    /**
     * Subscribes a listener at runtime: a streaming subscription on the sidecar, a local
     * registration in memory mode. (Subscriptions declared with {@code @DaprSubscribe} are
     * advertised to the sidecar over HTTP instead.)
     *
     * @param pubsub          the pub/sub component
     * @param topic           the topic
     * @param deadLetterTopic where undeliverable messages go, or {@code null}
     * @param metadata        subscription metadata
     * @param listener        the listener
     * @return the subscription
     */
    DaprSubscription subscribeTopic(String pubsub, String topic, String deadLetterTopic,
                                    Map<String, String> metadata, TopicEventListener listener);

    // ---------------------------------------------------------------- secrets

    Map<String, String> getSecret(String store, String name, Map<String, String> metadata);

    Map<String, Map<String, String>> getBulkSecret(String store, Map<String, String> metadata);

    // ---------------------------------------------------------------- configuration

    Map<String, ConfigItem> getConfiguration(String store, List<String> keys, Map<String, String> metadata);

    DaprSubscription subscribeConfiguration(String store, List<String> keys, Map<String, String> metadata,
                                            Consumer<Map<String, ConfigItem>> onChange,
                                            Consumer<Throwable> onError);

    // ---------------------------------------------------------------- lock

    boolean tryLock(String store, String resourceId, String owner, int expirySeconds);

    UnlockStatus unlock(String store, String resourceId, String owner);

    // ---------------------------------------------------------------- bindings

    default byte[] invokeBinding(String name, String operation, Object data, Map<String, String> metadata) {
        throw unsupported("output bindings");
    }

    default BindingResponse invokeBindingWithResponse(String name, String operation, Object data,
                                                      Map<String, String> metadata) {
        throw unsupported("output bindings");
    }

    // ---------------------------------------------------------------- jobs

    default void scheduleJob(String name, JobOptions options) {
        throw unsupported("jobs");
    }

    default JobDetails getJob(String name) {
        throw unsupported("jobs");
    }

    default void deleteJob(String name) {
        throw unsupported("jobs");
    }

    // ---------------------------------------------------------------- invocation and actors (HTTP)

    default InvokeResponse invoke(String appId, String method, InvokeRequest request) {
        throw unsupported("service invocation");
    }

    default byte[] invokeActor(String actorType, String actorId, String method, byte[] data) {
        throw unsupported("actors");
    }

    default byte[] getActorState(String actorType, String actorId, String key) {
        throw unsupported("actors");
    }

    default void executeActorStateTransaction(String actorType, String actorId, List<Map<String, Object>> operations) {
        throw unsupported("actors");
    }

    default void registerActorReminder(String actorType, String actorId, String name, Duration dueTime,
                                       Duration period, Object data) {
        throw unsupported("actors");
    }

    default void registerActorTimer(String actorType, String actorId, String name, Duration dueTime,
                                    Duration period, String callback, Object data) {
        throw unsupported("actors");
    }

    // ---------------------------------------------------------------- crypto

    default byte[] encrypt(String component, String keyName, String keyWrapAlgorithm, byte[] plaintext) {
        throw unsupported("cryptography");
    }

    default byte[] decrypt(String component, byte[] ciphertext, String keyName) {
        throw unsupported("cryptography");
    }

    // ---------------------------------------------------------------- runtime

    DaprMetadata metadata();

    /**
     * One probe of the sidecar's outbound readiness ({@code GET /v1.0/healthz/outbound}).
     *
     * @param timeout how long the probe may take
     * @return {@code true} when the sidecar answered 2xx
     */
    boolean probe(Duration timeout);

    default void shutdownSidecar() {
        throw unsupported("sidecar shutdown");
    }

    @Override
    void close();

    /**
     * The error raised by a building block the current mode cannot provide.
     *
     * @param what the building block
     * @return the error
     */
    default DaprError unsupported(String what) {
        return new DaprError("Dapr " + what + " needs a sidecar; this service runs Dapr in " + mode().name().toLowerCase()
                + " mode (set ADHAR_DAPR_MODE=sidecar, or run with a Dapr sidecar)",
                501, "ERR_MODE_UNSUPPORTED", what + " is not available in " + mode() + " mode");
    }

    /**
     * A service invocation, already serialized.
     *
     * @param verb        the HTTP verb
     * @param body        the body, or {@code null}
     * @param contentType the body content type, or {@code null}
     * @param headers     request headers
     * @param query       query parameters
     * @param timeout     the timeout, or {@code null}
     */
    record InvokeRequest(String verb, byte[] body, String contentType, Map<String, String> headers,
                         Map<String, List<String>> query, Duration timeout) {
    }
}
