package com.adhar.kit.dapr.backend;

import com.adhar.kit.dapr.DaprError;
import com.adhar.kit.dapr.DaprMode;
import com.adhar.kit.dapr.DaprSubscription;
import com.adhar.kit.dapr.api.StateOperation;
import com.adhar.kit.dapr.bindings.BindingResponse;
import com.adhar.kit.dapr.configuration.ConfigItem;
import com.adhar.kit.dapr.http.DaprHttpClient;
import com.adhar.kit.dapr.http.DaprHttpResponse;
import com.adhar.kit.dapr.invoke.InvokeResponse;
import com.adhar.kit.dapr.jobs.JobDetails;
import com.adhar.kit.dapr.jobs.JobOptions;
import com.adhar.kit.dapr.lock.UnlockStatus;
import com.adhar.kit.dapr.observation.DaprTraceContext;
import com.adhar.kit.dapr.pubsub.BulkPublishResult;
import com.adhar.kit.dapr.pubsub.DispatchStatus;
import com.adhar.kit.dapr.pubsub.PublishOptions;
import com.adhar.kit.dapr.pubsub.TopicEventListener;
import com.adhar.kit.dapr.serialization.DaprJsonSerializer;
import com.adhar.kit.dapr.state.DeleteOptions;
import com.adhar.kit.dapr.state.GetOptions;
import com.adhar.kit.dapr.state.SaveOptions;
import com.adhar.kit.dapr.state.StateEntry;
import com.adhar.kit.dapr.state.StateItem;
import com.adhar.kit.dapr.state.StateOptionsSupport;
import com.adhar.kit.dapr.state.StateQueryResult;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprPreviewClient;
import io.dapr.client.domain.BulkPublishEntry;
import io.dapr.client.domain.BulkPublishRequest;
import io.dapr.client.domain.BulkPublishResponse;
import io.dapr.client.domain.ConfigurationItem;
import io.dapr.client.domain.DaprMetadata;
import io.dapr.client.domain.DecryptRequestAlpha1;
import io.dapr.client.domain.DeleteJobRequest;
import io.dapr.client.domain.DeleteStateRequest;
import io.dapr.client.domain.EncryptRequestAlpha1;
import io.dapr.client.domain.ExecuteStateTransactionRequest;
import io.dapr.client.domain.GetBulkSecretRequest;
import io.dapr.client.domain.GetBulkStateRequest;
import io.dapr.client.domain.GetConfigurationRequest;
import io.dapr.client.domain.GetJobRequest;
import io.dapr.client.domain.GetJobResponse;
import io.dapr.client.domain.GetSecretRequest;
import io.dapr.client.domain.GetStateRequest;
import io.dapr.client.domain.InvokeBindingRequest;
import io.dapr.client.domain.JobSchedule;
import io.dapr.client.domain.LockRequest;
import io.dapr.client.domain.PublishEventRequest;
import io.dapr.client.domain.QueryStateItem;
import io.dapr.client.domain.QueryStateResponse;
import io.dapr.client.domain.ScheduleJobRequest;
import io.dapr.client.domain.State;
import io.dapr.client.domain.SubscribeConfigurationRequest;
import io.dapr.client.domain.SubscribeConfigurationResponse;
import io.dapr.client.domain.TransactionalStateOperation;
import io.dapr.client.domain.UnlockRequest;
import io.dapr.utils.TypeRef;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Runs the building blocks on the Dapr sidecar: the SDK's gRPC client for state, pub/sub, secrets,
 * configuration, lock, bindings, jobs, crypto and metadata, and the sidecar's HTTP API for service
 * invocation (every verb, headers and query, without the SDK's deprecated {@code invokeMethod}),
 * actors and output-binding response metadata.
 *
 * <p>Every gRPC call carries the caller's {@code traceparent}/{@code tracestate} in its Reactor
 * context, which the SDK's tracing interceptor forwards to the sidecar, so Dapr's spans join the
 * application's trace.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
public class SidecarDaprBackend implements DaprBackend {

    private static final String METADATA_HEADER_PREFIX = "metadata.";

    private final DaprClient client;
    private final DaprPreviewClient preview;
    private final DaprHttpClient http;
    private final DaprJsonSerializer serializer;
    private final DaprTraceContext traceContext;
    private final boolean ownsClient;

    /**
     * @param client       the SDK client (also used as the preview client when it implements it)
     * @param preview      the preview client, or {@code null}
     * @param http         the sidecar HTTP client, or {@code null} when only gRPC is available
     * @param serializer   the payload serializer the client was built with
     * @param traceContext the trace context to forward
     * @param ownsClient   close the client when this backend closes
     */
    public SidecarDaprBackend(DaprClient client, DaprPreviewClient preview, DaprHttpClient http,
                              DaprJsonSerializer serializer, DaprTraceContext traceContext, boolean ownsClient) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.preview = preview;
        this.http = http;
        this.serializer = serializer == null ? new DaprJsonSerializer() : serializer;
        this.traceContext = traceContext == null ? DaprTraceContext.NONE : traceContext;
        this.ownsClient = ownsClient;
    }

    @Override
    public DaprMode mode() {
        return DaprMode.SIDECAR;
    }

    /** The SDK client, for callers that need an SDK API the kit does not wrap. */
    public DaprClient client() {
        return client;
    }

    /** The preview client, or {@code null}. */
    public DaprPreviewClient previewClient() {
        return preview;
    }

    // ---------------------------------------------------------------- state

    @Override
    public <T> StateEntry<T> getState(String store, String key, TypeRef<T> type, GetOptions options) {
        GetStateRequest request = new GetStateRequest(store, key)
                .setMetadata(options.metadata())
                .setStateOptions(StateOptionsSupport.toSdk(options.consistency(), null));
        State<T> state = block(client.getState(request, type));
        if (state == null) {
            return new StateEntry<>(key, null, null);
        }
        return new StateEntry<>(key, state.getValue(), emptyToNull(state.getEtag()));
    }

    @Override
    public <T> List<StateEntry<T>> getBulkState(String store, List<String> keys, TypeRef<T> type,
                                                Map<String, String> metadata) {
        List<State<T>> states = block(client.getBulkState(
                new GetBulkStateRequest(store, keys).setMetadata(metadata), type));
        if (states == null) {
            return List.of();
        }
        List<StateEntry<T>> entries = new ArrayList<>(states.size());
        for (State<T> state : states) {
            if (state.getError() != null && !state.getError().isEmpty()) {
                log.debug("Bulk get of key '{}' in store '{}' failed: {}", state.getKey(), store, state.getError());
                continue;
            }
            entries.add(new StateEntry<>(state.getKey(), state.getValue(), emptyToNull(state.getEtag())));
        }
        return entries;
    }

    @Override
    public void saveState(String store, String key, Object value, SaveOptions options) {
        block(client.saveState(store, key, options.etag(), value, options.requestMetadata(),
                StateOptionsSupport.toSdk(options.consistency(), options.concurrency())));
    }

    @Override
    public void saveBulkState(String store, List<StateItem> items) {
        List<State<?>> states = new ArrayList<>(items.size());
        for (StateItem item : items) {
            SaveOptions options = item.options();
            states.add(new State<>(item.key(), item.value(), options.etag(), options.requestMetadata(),
                    StateOptionsSupport.toSdk(options.consistency(), options.concurrency())));
        }
        block(client.saveBulkState(store, states));
    }

    @Override
    public void deleteState(String store, String key, DeleteOptions options) {
        block(client.deleteState(new DeleteStateRequest(store, key)
                .setEtag(options.etag())
                .setMetadata(options.metadata())
                .setStateOptions(StateOptionsSupport.toSdk(options.consistency(), options.concurrency()))));
    }

    @Override
    public void executeStateTransaction(String store, List<StateOperation> operations, Map<String, String> metadata) {
        List<TransactionalStateOperation<?>> sdkOperations = new ArrayList<>(operations.size());
        for (StateOperation op : operations) {
            boolean upsert = op.getOperation() != StateOperation.OperationType.DELETE;
            State<Object> state = new State<>(op.getKey(), upsert ? op.getValue() : null, op.getEtag(),
                    op.getMetadata() == null ? Map.of() : op.getMetadata(),
                    StateOptionsSupport.toSdk(null, op.getConcurrency()));
            sdkOperations.add(new TransactionalStateOperation<>(
                    upsert ? TransactionalStateOperation.OperationType.UPSERT
                            : TransactionalStateOperation.OperationType.DELETE,
                    state));
        }
        block(client.executeStateTransaction(new ExecuteStateTransactionRequest(store)
                .setOperations(sdkOperations)
                .setMetadata(metadata)));
    }

    @Override
    public <T> StateQueryResult<T> queryState(String store, String query, TypeRef<T> type,
                                              Map<String, String> metadata) {
        QueryStateResponse<T> response = block(requirePreview("state query")
                .queryState(store, query, metadata, type));
        if (response == null) {
            return new StateQueryResult<>(List.of(), Map.of(), null, Map.of());
        }
        List<StateEntry<T>> results = new ArrayList<>();
        Map<String, String> errors = new LinkedHashMap<>();
        if (response.getResults() != null) {
            for (QueryStateItem<T> item : response.getResults()) {
                if (item.getError() != null && !item.getError().isEmpty()) {
                    errors.put(item.getKey(), item.getError());
                } else {
                    results.add(new StateEntry<>(item.getKey(), item.getValue(), emptyToNull(item.getEtag())));
                }
            }
        }
        return new StateQueryResult<>(results, errors, emptyToNull(response.getToken()), response.getMetadata());
    }

    // ---------------------------------------------------------------- pub/sub

    @Override
    public void publishEvent(String pubsub, String topic, Object data, PublishOptions options) {
        block(client.publishEvent(new PublishEventRequest(pubsub, topic, data)
                .setMetadata(options.metadata())
                .setContentType(options.contentType())));
    }

    @Override
    public <T> BulkPublishResult publishEvents(String pubsub, String topic, List<T> events, PublishOptions options) {
        List<BulkPublishEntry<T>> entries = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            // A null entry content type keeps the kit serializer: the SDK switches to its own
            // converter (no java.time support) whenever an entry names one.
            entries.add(new BulkPublishEntry<>(String.valueOf(i), events.get(i), options.contentType(), Map.of()));
        }
        BulkPublishResponse<T> response = block(client.publishEvents(
                new BulkPublishRequest<>(pubsub, topic, entries, options.metadata())));
        List<BulkPublishResult.FailedEntry> failed = new ArrayList<>();
        if (response != null && response.getFailedEntries() != null) {
            response.getFailedEntries().forEach(entry -> failed.add(new BulkPublishResult.FailedEntry(
                    entry.getEntry().getEntryId(), entry.getEntry().getEvent(), entry.getErrorMessage())));
        }
        return new BulkPublishResult(events.size() - failed.size(), failed);
    }

    @Override
    @SuppressWarnings("deprecation") // the listener API is the only one where the handler decides
    // SUCCESS/RETRY/DROP; the Flux variant acknowledges SUCCESS before the consumer has run.
    public DaprSubscription subscribeTopic(String pubsub, String topic, String deadLetterTopic,
                                           Map<String, String> metadata, TopicEventListener listener) {
        if (metadata != null && !metadata.isEmpty()) {
            log.debug("Streaming subscription to {}/{} ignores metadata {}: the SDK's listener API cannot send it",
                    pubsub, topic, metadata.keySet());
        }
        io.dapr.client.SubscriptionListener<byte[]> sdkListener = new io.dapr.client.SubscriptionListener<>() {
            @Override
            public Mono<Status> onEvent(io.dapr.client.domain.CloudEvent<byte[]> event) {
                DispatchStatus status;
                try {
                    status = listener.onEvent(toEnvelope(event));
                } catch (RuntimeException e) {
                    log.warn("Listener for {}/{} failed on event {}; asking Dapr to retry", pubsub, topic,
                            event.getId(), e);
                    status = DispatchStatus.RETRY;
                }
                return Mono.just(switch (status == null ? DispatchStatus.SUCCESS : status) {
                    case SUCCESS -> Status.SUCCESS;
                    case DROP -> Status.DROP;
                    case RETRY -> Status.RETRY;
                });
            }

            @Override
            public void onError(RuntimeException exception) {
                log.warn("Streaming subscription to {}/{} reported an error: {}", pubsub, topic, exception.getMessage());
            }
        };
        io.dapr.client.Subscription<byte[]> subscription = requirePreview("streaming subscriptions")
                .subscribeToEvents(pubsub, topic, deadLetterTopic, sdkListener, TypeRef.BYTE_ARRAY);
        AtomicBoolean active = new AtomicBoolean(true);
        return new DaprSubscription() {
            @Override
            public String id() {
                return pubsub + "/" + topic;
            }

            @Override
            public void unsubscribe() {
                if (active.getAndSet(false)) {
                    subscription.close();
                }
            }

            @Override
            public boolean isActive() {
                return active.get();
            }
        };
    }

    private Map<String, Object> toEnvelope(io.dapr.client.domain.CloudEvent<byte[]> event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", event.getId());
        envelope.put("source", event.getSource());
        envelope.put("type", event.getType());
        envelope.put("specversion", event.getSpecversion());
        envelope.put("datacontenttype", event.getDatacontenttype());
        envelope.put("topic", event.getTopic());
        envelope.put("pubsubname", event.getPubsubName());
        envelope.put("data", decodeData(event.getData(), event.getDatacontenttype()));
        return envelope;
    }

    private Object decodeData(byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            return null;
        }
        String type = contentType == null ? "application/json" : contentType.toLowerCase(java.util.Locale.ROOT);
        if (type.contains("json")) {
            try {
                return serializer.objectMapper().readValue(data, Object.class);
            } catch (java.io.IOException e) {
                return new String(data, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        if (type.startsWith("text/") || type.contains("xml")) {
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        }
        return data;
    }

    // ---------------------------------------------------------------- secrets

    @Override
    public Map<String, String> getSecret(String store, String name, Map<String, String> metadata) {
        Map<String, String> secret = block(client.getSecret(new GetSecretRequest(store, name).setMetadata(metadata)));
        return secret == null ? Map.of() : secret;
    }

    @Override
    public Map<String, Map<String, String>> getBulkSecret(String store, Map<String, String> metadata) {
        Map<String, Map<String, String>> secrets = block(client.getBulkSecret(
                new GetBulkSecretRequest(store).setMetadata(metadata)));
        return secrets == null ? Map.of() : secrets;
    }

    // ---------------------------------------------------------------- configuration

    @Override
    public Map<String, ConfigItem> getConfiguration(String store, List<String> keys, Map<String, String> metadata) {
        Map<String, ConfigurationItem> items = block(client.getConfiguration(
                new GetConfigurationRequest(store, keys).setMetadata(metadata)));
        return toConfigItems(items);
    }

    @Override
    public DaprSubscription subscribeConfiguration(String store, List<String> keys, Map<String, String> metadata,
                                                   Consumer<Map<String, ConfigItem>> onChange,
                                                   Consumer<Throwable> onError) {
        AtomicReference<String> subscriptionId = new AtomicReference<>();
        AtomicBoolean active = new AtomicBoolean(true);
        Flux<SubscribeConfigurationResponse> flux = client.subscribeConfiguration(
                new SubscribeConfigurationRequest(store, keys).setMetadata(metadata));
        Disposable disposable = withTrace(flux).subscribe(response -> {
            if (response.getSubscriptionId() != null && !response.getSubscriptionId().isEmpty()) {
                subscriptionId.compareAndSet(null, response.getSubscriptionId());
            }
            Map<String, ConfigItem> changed = toConfigItems(response.getItems());
            // The first message only carries the subscription id.
            if (!changed.isEmpty()) {
                onChange.accept(changed);
            }
        }, error -> {
            active.set(false);
            onError.accept(DaprError.from("Configuration subscription to store '" + store + "' failed", error));
        });
        return new DaprSubscription() {
            @Override
            public String id() {
                String id = subscriptionId.get();
                return id != null ? id : "pending";
            }

            @Override
            public void unsubscribe() {
                if (!active.getAndSet(false)) {
                    return;
                }
                String id = subscriptionId.get();
                try {
                    if (id != null) {
                        block(client.unsubscribeConfiguration(id, store));
                    }
                } catch (RuntimeException e) {
                    log.debug("Unsubscribing configuration subscription {} failed: {}", id, e.getMessage());
                } finally {
                    disposable.dispose();
                }
            }

            @Override
            public boolean isActive() {
                return active.get() && !disposable.isDisposed();
            }
        };
    }

    // ---------------------------------------------------------------- lock

    @Override
    public boolean tryLock(String store, String resourceId, String owner, int expirySeconds) {
        Boolean acquired = block(requirePreview("distributed lock")
                .tryLock(new LockRequest(store, resourceId, owner, expirySeconds)));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public UnlockStatus unlock(String store, String resourceId, String owner) {
        return UnlockStatus.of(block(requirePreview("distributed lock")
                .unlock(new UnlockRequest(store, resourceId, owner))));
    }

    // ---------------------------------------------------------------- bindings

    @Override
    public byte[] invokeBinding(String name, String operation, Object data, Map<String, String> metadata) {
        // Text goes out as-is, as the sidecar's HTTP API sends it: the SDK would JSON-quote it,
        // and an email body or a file would arrive wrapped in quotes.
        Object payload = data instanceof String text ? text.getBytes(java.nio.charset.StandardCharsets.UTF_8) : data;
        InvokeBindingRequest request = new InvokeBindingRequest(name, operation)
                .setData(payload)
                .setMetadata(metadata);
        return block(client.invokeBinding(request, TypeRef.BYTE_ARRAY));
    }

    @Override
    public BindingResponse invokeBindingWithResponse(String name, String operation, Object data,
                                                     Map<String, String> metadata) {
        // The SDK drops the binding's response metadata; the HTTP API returns it as headers.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operation", operation);
        if (data != null) {
            body.put("data", data instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : data);
        }
        if (metadata != null && !metadata.isEmpty()) {
            body.put("metadata", metadata);
        }
        DaprHttpResponse response = requireHttp().send("POST", "/v1.0/bindings/" + DaprHttpClient.encodeSegment(name),
                Map.of(), Map.of(), serializer.toBytes(body), "application/json", null);
        if (!response.isSuccessful()) {
            throw DaprHttpClient.toError("Failed to invoke binding '" + name + "'", response);
        }
        Map<String, String> responseMetadata = new LinkedHashMap<>();
        response.headers().forEach((header, values) -> {
            if (header.startsWith(METADATA_HEADER_PREFIX) && !values.isEmpty()) {
                responseMetadata.put(header.substring(METADATA_HEADER_PREFIX.length()), values.getFirst());
            }
        });
        return new BindingResponse(response.body(), responseMetadata);
    }

    // ---------------------------------------------------------------- jobs

    @Override
    public void scheduleJob(String name, JobOptions options) {
        ScheduleJobRequest request = options.schedule() != null && !options.schedule().isBlank()
                ? new ScheduleJobRequest(name, JobSchedule.fromString(options.schedule()))
                : new ScheduleJobRequest(name, options.dueTime());
        if (options.schedule() != null && options.dueTime() != null) {
            request.setDueTime(options.dueTime());
        }
        if (options.repeats() != null) {
            request.setRepeat(options.repeats());
        }
        if (options.ttl() != null) {
            request.setTtl(options.ttl());
        }
        if (options.data() != null) {
            request.setData(serializer.toBytes(options.data()));
        }
        request.setOverwrite(options.overwrite());
        block(client.scheduleJob(request));
    }

    @Override
    public JobDetails getJob(String name) {
        GetJobResponse job = block(client.getJob(new GetJobRequest(name)));
        if (job == null) {
            return null;
        }
        return new JobDetails(job.getName(), job.getSchedule() == null ? null : job.getSchedule().getExpression(),
                job.getDueTime(), job.getRepeats(), job.getTtl(), job.getData());
    }

    @Override
    public void deleteJob(String name) {
        block(client.deleteJob(new DeleteJobRequest(name)));
    }

    // ---------------------------------------------------------------- invocation and actors (HTTP)

    @Override
    public InvokeResponse invoke(String appId, String method, InvokeRequest request) {
        String path = "/v1.0/invoke/" + DaprHttpClient.encodeSegment(appId) + "/method/"
                + DaprHttpClient.encodePath(stripLeadingSlash(method));
        DaprHttpResponse response = requireHttp().send(request.verb(), path, request.query(), request.headers(),
                request.body(), request.contentType(), request.timeout());
        if (!response.isSuccessful()) {
            throw DaprHttpClient.toError("Invocation of " + request.verb() + " " + appId + "/"
                    + stripLeadingSlash(method) + " failed", response);
        }
        return new InvokeResponse(response.status(), response.headers(), response.body());
    }

    @Override
    public byte[] invokeActor(String actorType, String actorId, String method, byte[] data) {
        DaprHttpResponse response = requireHttp().send("PUT", actorPath(actorType, actorId) + "/method/"
                + DaprHttpClient.encodeSegment(method), Map.of(), Map.of(), data, "application/json", null);
        if (!response.isSuccessful()) {
            throw DaprHttpClient.toError("Failed to invoke actor " + actorType + "/" + actorId + "." + method, response);
        }
        return response.body();
    }

    @Override
    public byte[] getActorState(String actorType, String actorId, String key) {
        DaprHttpResponse response = requireHttp().get(actorPath(actorType, actorId) + "/state/"
                + DaprHttpClient.encodeSegment(key), null);
        if (response.status() == 204) {
            return null;
        }
        if (!response.isSuccessful()) {
            throw DaprHttpClient.toError("Failed to read state of actor " + actorType + "/" + actorId, response);
        }
        return response.body();
    }

    @Override
    public void executeActorStateTransaction(String actorType, String actorId, List<Map<String, Object>> operations) {
        DaprHttpResponse response = requireHttp().send("POST", actorPath(actorType, actorId) + "/state", Map.of(),
                Map.of(), serializer.toBytes(operations), "application/json", null);
        if (!response.isSuccessful()) {
            throw DaprHttpClient.toError("Failed to save state of actor " + actorType + "/" + actorId, response);
        }
    }

    @Override
    public void registerActorReminder(String actorType, String actorId, String name, Duration dueTime,
                                      Duration period, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dueTime", goDuration(dueTime));
        if (period != null && !period.isZero()) {
            body.put("period", goDuration(period));
        }
        if (data != null) {
            body.put("data", data);
        }
        DaprHttpResponse response = requireHttp().send("POST", actorPath(actorType, actorId) + "/reminders/"
                + DaprHttpClient.encodeSegment(name), Map.of(), Map.of(), serializer.toBytes(body), "application/json", null);
        if (!response.isSuccessful()) {
            throw DaprHttpClient.toError("Failed to register reminder '" + name + "' on actor " + actorType, response);
        }
    }

    @Override
    public void registerActorTimer(String actorType, String actorId, String name, Duration dueTime,
                                   Duration period, String callback, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dueTime", goDuration(dueTime));
        if (period != null && !period.isZero()) {
            body.put("period", goDuration(period));
        }
        if (callback != null) {
            body.put("callback", callback);
        }
        if (data != null) {
            body.put("data", data);
        }
        DaprHttpResponse response = requireHttp().send("POST", actorPath(actorType, actorId) + "/timers/"
                + DaprHttpClient.encodeSegment(name), Map.of(), Map.of(), serializer.toBytes(body), "application/json", null);
        if (!response.isSuccessful()) {
            throw DaprHttpClient.toError("Failed to register timer '" + name + "' on actor " + actorType, response);
        }
    }

    // ---------------------------------------------------------------- crypto

    @Override
    public byte[] encrypt(String component, String keyName, String keyWrapAlgorithm, byte[] plaintext) {
        return concat(requirePreview("cryptography").encrypt(
                new EncryptRequestAlpha1(component, Flux.just(plaintext), keyName, keyWrapAlgorithm)));
    }

    @Override
    public byte[] decrypt(String component, byte[] ciphertext, String keyName) {
        DecryptRequestAlpha1 request = new DecryptRequestAlpha1(component, Flux.just(ciphertext));
        if (keyName != null) {
            request.setKeyName(keyName);
        }
        return concat(requirePreview("cryptography").decrypt(request));
    }

    // ---------------------------------------------------------------- runtime

    @Override
    public DaprMetadata metadata() {
        return block(client.getMetadata());
    }

    @Override
    public boolean probe(Duration timeout) {
        if (http == null) {
            return false;
        }
        try {
            return http.get("/v1.0/healthz/outbound", timeout).isSuccessful();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void shutdownSidecar() {
        block(client.shutdown());
    }

    @Override
    public void close() {
        if (!ownsClient) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Closing the Dapr client failed: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- helpers

    private <T> T block(Mono<T> mono) {
        if (mono == null) {
            return null;
        }
        Map<String, String> trace = traceContext.current();
        if (trace.isEmpty()) {
            return mono.block();
        }
        return mono.contextWrite(context -> put(context, trace)).block();
    }

    private <T> Flux<T> withTrace(Flux<T> flux) {
        Map<String, String> trace = traceContext.current();
        return trace.isEmpty() ? flux : flux.contextWrite(context -> put(context, trace));
    }

    private static Context put(Context context, Map<String, String> trace) {
        Context result = context;
        for (Map.Entry<String, String> entry : trace.entrySet()) {
            result = result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private byte[] concat(Flux<byte[]> chunks) {
        List<byte[]> parts = withTrace(chunks).collectList().block();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (parts != null) {
            parts.forEach(out::writeBytes);
        }
        return out.toByteArray();
    }

    private DaprPreviewClient requirePreview(String what) {
        if (preview == null) {
            throw new IllegalStateException("Dapr " + what + " requires a DaprPreviewClient; build the facade "
                    + "from DaprClientBuilder (its client implements both) or pass one explicitly");
        }
        return preview;
    }

    private DaprHttpClient requireHttp() {
        if (http == null) {
            throw new IllegalStateException("This Dapr facade has no sidecar HTTP client; build it with "
                    + "DaprFacade.builder() to use service invocation, actors and binding metadata");
        }
        return http;
    }

    private static Map<String, ConfigItem> toConfigItems(Map<String, ConfigurationItem> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Map<String, ConfigItem> result = new LinkedHashMap<>();
        items.forEach((key, item) -> {
            if (item != null) {
                result.put(key, new ConfigItem(key, emptyToNull(item.getValue()), item.getVersion(), item.getMetadata()));
            }
        });
        return result;
    }

    private static String actorPath(String actorType, String actorId) {
        return "/v1.0/actors/" + DaprHttpClient.encodeSegment(actorType) + "/" + DaprHttpClient.encodeSegment(actorId);
    }

    /** Go's {@code time.ParseDuration} accepts milliseconds, which is all the precision Dapr keeps. */
    static String goDuration(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return "0s";
        }
        return duration.toMillis() + "ms";
    }

    private static String stripLeadingSlash(String method) {
        String m = method == null ? "" : method;
        while (m.startsWith("/")) {
            m = m.substring(1);
        }
        return m;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    @Override
    public String toString() {
        return "SidecarDaprBackend[" + (http == null ? "grpc only" : http.endpoint()) + "]";
    }
}
