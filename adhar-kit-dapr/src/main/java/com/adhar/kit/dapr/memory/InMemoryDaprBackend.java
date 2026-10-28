package com.adhar.kit.dapr.memory;

import com.adhar.kit.dapr.DaprError;
import com.adhar.kit.dapr.DaprMode;
import com.adhar.kit.dapr.DaprSubscription;
import com.adhar.kit.dapr.api.StateOperation;
import com.adhar.kit.dapr.backend.DaprBackend;
import com.adhar.kit.dapr.configuration.ConfigItem;
import com.adhar.kit.dapr.lock.UnlockStatus;
import com.adhar.kit.dapr.pubsub.BulkPublishResult;
import com.adhar.kit.dapr.pubsub.DispatchStatus;
import com.adhar.kit.dapr.pubsub.PublishOptions;
import com.adhar.kit.dapr.pubsub.TopicEventListener;
import com.adhar.kit.dapr.serialization.DaprJsonSerializer;
import com.adhar.kit.dapr.state.Concurrency;
import com.adhar.kit.dapr.state.DeleteOptions;
import com.adhar.kit.dapr.state.GetOptions;
import com.adhar.kit.dapr.state.SaveOptions;
import com.adhar.kit.dapr.state.StateEntry;
import com.adhar.kit.dapr.state.StateItem;
import com.adhar.kit.dapr.state.StateQueryResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.client.domain.ComponentMetadata;
import io.dapr.client.domain.DaprMetadata;
import io.dapr.client.domain.SubscriptionMetadata;
import io.dapr.utils.TypeRef;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The building blocks without a sidecar ({@code ADHAR_DAPR_MODE=memory}): state (ETags, TTL,
 * first-write concurrency, transactions, queries), pub/sub fanned out to this process's own
 * subscribers, distributed lock, configuration with change notifications and secrets, so a
 * service starts, runs and is tested with no Dapr at all.
 *
 * <p>Values are stored serialized, exactly as the sidecar would store them, so a read returns a
 * copy converted to the requested type and a value that would not survive the round trip fails
 * here first. Events are delivered on the publishing thread: a test can assert on a subscriber
 * right after {@code publish} returns. A handler answering {@code RETRY} is retried
 * {@value #DELIVERY_ATTEMPTS} times before the event goes to the subscription's dead-letter topic
 * (or is dropped with a warning).</p>
 *
 * <p>Secrets and configuration are seeded from {@code adhar.dapr.memory.*}; configuration can
 * be changed at runtime with {@link #setConfiguration} to exercise subscribers.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
public class InMemoryDaprBackend implements DaprBackend {

    /** How many times a RETRY answer is redelivered before dead-lettering. */
    public static final int DELIVERY_ATTEMPTS = 3;

    private final DaprJsonSerializer serializer;
    private final String appId;
    private final Clock clock;
    private final AtomicLong etags = new AtomicLong();
    private final Map<String, Map<String, StoredValue>> stores = new ConcurrentHashMap<>();
    private final Map<String, Map<String, LockHolder>> locks = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ConfigItem>> configuration = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, String>>> secrets = new ConcurrentHashMap<>();
    private final List<TopicListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ConfigWatcher> watchers = new CopyOnWriteArrayList<>();

    public InMemoryDaprBackend(DaprJsonSerializer serializer, String appId) {
        this(serializer, appId, Clock.systemUTC());
    }

    public InMemoryDaprBackend(DaprJsonSerializer serializer, String appId, Clock clock) {
        this.serializer = serializer == null ? new DaprJsonSerializer() : serializer;
        this.appId = appId == null ? "unknown" : appId;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public DaprMode mode() {
        return DaprMode.MEMORY;
    }

    /**
     * The one warning memory mode logs when a service starts: nothing it stores or publishes
     * leaves the process, which is right for tests and local runs and wrong anywhere else.
     *
     * @param connection the resolved connection
     */
    public static void warnStartup(com.adhar.kit.dapr.DaprConnectionDetails connection) {
        log.warn("No Dapr sidecar configured (ADHAR_DAPR_MODE={}, DAPR_HTTP_PORT/DAPR_HTTP_ENDPOINT unset): state, "
                        + "cache, pub/sub, lock, configuration and secrets run in memory for app '{}'. Nothing is "
                        + "shared between replicas or kept across restarts.",
                connection.requestedMode().name().toLowerCase(java.util.Locale.ROOT), connection.appId());
    }

    // ---------------------------------------------------------------- seeding

    /**
     * Adds (or replaces) a secret in a secret store.
     *
     * @param store  the secret store name
     * @param name   the secret name
     * @param values the secret's key/value pairs
     */
    public void putSecret(String store, String name, Map<String, String> values) {
        secrets.computeIfAbsent(store, s -> new ConcurrentHashMap<>()).put(name, Map.copyOf(values));
    }

    /**
     * Sets a configuration value and notifies subscribers of the key; {@code null} deletes it.
     *
     * @param store the configuration store name
     * @param key   the key
     * @param value the value, or {@code null}
     */
    public void setConfiguration(String store, String key, String value) {
        Map<String, ConfigItem> items = configuration.computeIfAbsent(store, s -> new ConcurrentHashMap<>());
        ConfigItem item = new ConfigItem(key, value, String.valueOf(etags.incrementAndGet()), Map.of());
        if (value == null) {
            items.remove(key);
        } else {
            items.put(key, item);
        }
        for (ConfigWatcher watcher : watchers) {
            if (watcher.active.get() && watcher.store.equals(store)
                    && (watcher.keys.isEmpty() || watcher.keys.contains(key))) {
                try {
                    watcher.onChange.accept(Map.of(key, item));
                } catch (RuntimeException e) {
                    log.warn("Configuration subscriber for '{}' failed: {}", key, e.getMessage());
                }
            }
        }
    }

    // ---------------------------------------------------------------- state

    @Override
    public <T> StateEntry<T> getState(String store, String key, TypeRef<T> type, GetOptions options) {
        StoredValue stored = live(store, key);
        if (stored == null) {
            return new StateEntry<>(key, null, null);
        }
        return new StateEntry<>(key, serializer.fromBytes(stored.bytes, type), stored.etag);
    }

    @Override
    public <T> List<StateEntry<T>> getBulkState(String store, List<String> keys, TypeRef<T> type,
                                                Map<String, String> metadata) {
        List<StateEntry<T>> entries = new ArrayList<>(keys.size());
        for (String key : keys) {
            entries.add(getState(store, key, type, GetOptions.none()));
        }
        return entries;
    }

    @Override
    public void saveState(String store, String key, Object value, SaveOptions options) {
        Map<String, StoredValue> values = store(store);
        synchronized (values) {
            checkWrite(store, key, options.etag(), options.concurrency(), values);
            values.put(key, stored(value, options));
        }
    }

    @Override
    public void saveBulkState(String store, List<StateItem> items) {
        // Dapr's bulk save is not atomic: each item succeeds or fails on its own.
        for (StateItem item : items) {
            saveState(store, item.key(), item.value(), item.options());
        }
    }

    @Override
    public void deleteState(String store, String key, DeleteOptions options) {
        Map<String, StoredValue> values = store(store);
        synchronized (values) {
            if (options.etag() != null) {
                StoredValue current = liveLocked(values, key);
                if (current == null || !current.etag.equals(options.etag())) {
                    throw etagMismatch(store, key);
                }
            }
            values.remove(key);
        }
    }

    @Override
    public void executeStateTransaction(String store, List<StateOperation> operations, Map<String, String> metadata) {
        Map<String, StoredValue> values = store(store);
        synchronized (values) {
            // Validate every ETag first: a transaction either applies whole or not at all.
            for (StateOperation op : operations) {
                StoredValue current = liveLocked(values, op.getKey());
                if (op.getEtag() != null) {
                    if (current == null || !current.etag.equals(op.getEtag())) {
                        throw etagMismatch(store, op.getKey());
                    }
                } else if (op.getConcurrency() == Concurrency.FIRST_WRITE && current != null
                        && op.getOperation() != StateOperation.OperationType.DELETE) {
                    throw etagMismatch(store, op.getKey());
                }
            }
            for (StateOperation op : operations) {
                if (op.getOperation() == StateOperation.OperationType.DELETE) {
                    values.remove(op.getKey());
                } else if (op.getMetadata() == null || !"true".equals(op.getMetadata().get("outbox.projection"))) {
                    Map<String, String> md = op.getMetadata() == null ? Map.of() : op.getMetadata();
                    values.put(op.getKey(), stored(op.getValue(), new SaveOptions(null, ttlOf(md), null, null, md)));
                }
            }
        }
    }

    @Override
    public <T> StateQueryResult<T> queryState(String store, String query, TypeRef<T> type,
                                              Map<String, String> metadata) {
        JsonNode queryNode;
        try {
            queryNode = query == null || query.isBlank() ? serializer.objectMapper().createObjectNode()
                    : serializer.objectMapper().readTree(query);
        } catch (IOException e) {
            throw new DaprError("Failed to query state: invalid query", 400, "ERR_MALFORMED_REQUEST", e.getMessage());
        }
        ObjectMapper mapper = serializer.objectMapper();
        List<Map.Entry<String, JsonNode>> matches = new ArrayList<>();
        Map<String, StoredValue> values = store(store);
        synchronized (values) {
            for (String key : new ArrayList<>(values.keySet())) {
                StoredValue stored = liveLocked(values, key);
                if (stored == null) {
                    continue;
                }
                JsonNode json;
                try {
                    json = mapper.readTree(stored.bytes);
                } catch (IOException e) {
                    continue;
                }
                if (InMemoryStateQuery.matches(queryNode.get("filter"), json)) {
                    matches.add(Map.entry(key, json));
                }
            }
        }
        Comparator<Map.Entry<String, JsonNode>> order = InMemoryStateQuery.sortOrder(queryNode.get("sort"));
        if (order != null) {
            matches.sort(order);
        }
        int offset = InMemoryStateQuery.pageToken(queryNode.get("page"));
        int limit = InMemoryStateQuery.pageLimit(queryNode.get("page"));
        int end = limit > 0 ? Math.min(matches.size(), offset + limit) : matches.size();
        List<StateEntry<T>> results = new ArrayList<>();
        for (int i = Math.min(offset, matches.size()); i < end; i++) {
            Map.Entry<String, JsonNode> match = matches.get(i);
            StoredValue stored = values.get(match.getKey());
            results.add(new StateEntry<>(match.getKey(), mapper.convertValue(match.getValue(),
                    mapper.constructType(type.getType())), stored == null ? null : stored.etag));
        }
        String token = limit > 0 && end < matches.size() ? String.valueOf(end) : null;
        return new StateQueryResult<>(results, Map.of(), token, Map.of());
    }

    // ---------------------------------------------------------------- pub/sub

    @Override
    public void publishEvent(String pubsub, String topic, Object data, PublishOptions options) {
        Map<String, Object> event = envelope(pubsub, topic, data, options);
        deliver(pubsub, topic, event, 0);
    }

    @Override
    public <T> BulkPublishResult publishEvents(String pubsub, String topic, List<T> events, PublishOptions options) {
        for (T event : events) {
            publishEvent(pubsub, topic, event, options);
        }
        return new BulkPublishResult(events.size(), List.of());
    }

    @Override
    public DaprSubscription subscribeTopic(String pubsub, String topic, String deadLetterTopic,
                                           Map<String, String> metadata, TopicEventListener listener) {
        TopicListener registration = new TopicListener(pubsub, topic, deadLetterTopic, listener);
        listeners.add(registration);
        return registration;
    }

    /** The topics something in this process subscribes to, as "pubsub/topic". */
    public List<String> subscribedTopics() {
        return listeners.stream().map(l -> l.pubsub + "/" + l.topic).distinct().toList();
    }

    private void deliver(String pubsub, String topic, Map<String, Object> event, int depth) {
        for (TopicListener listener : listeners) {
            if (!listener.pubsub.equals(pubsub) || !listener.topic.equals(topic)) {
                continue;
            }
            DispatchStatus status = DispatchStatus.RETRY;
            for (int attempt = 1; attempt <= DELIVERY_ATTEMPTS && status == DispatchStatus.RETRY; attempt++) {
                try {
                    status = listener.listener.onEvent(event);
                    if (status == null) {
                        status = DispatchStatus.SUCCESS;
                    }
                } catch (RuntimeException e) {
                    log.debug("Subscriber of {}/{} failed (attempt {}/{}): {}", pubsub, topic, attempt,
                            DELIVERY_ATTEMPTS, e.getMessage());
                    status = DispatchStatus.RETRY;
                }
            }
            if (status == DispatchStatus.RETRY) {
                if (listener.deadLetterTopic != null && depth == 0) {
                    log.warn("Event {} on {}/{} still failing after {} attempts; moving it to dead-letter topic '{}'",
                            event.get("id"), pubsub, topic, DELIVERY_ATTEMPTS, listener.deadLetterTopic);
                    Map<String, Object> deadLetter = new LinkedHashMap<>(event);
                    deadLetter.put("topic", listener.deadLetterTopic);
                    deliver(pubsub, listener.deadLetterTopic, deadLetter, depth + 1);
                } else {
                    log.warn("Event {} on {}/{} still failing after {} attempts; dropped (no dead-letter topic)",
                            event.get("id"), pubsub, topic, DELIVERY_ATTEMPTS);
                }
            } else if (status == DispatchStatus.DROP) {
                log.warn("Subscriber of {}/{} dropped event {}", pubsub, topic, event.get("id"));
            }
        }
    }

    private Map<String, Object> envelope(String pubsub, String topic, Object data, PublishOptions options) {
        Map<String, String> md = options.metadata();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", md.getOrDefault(PublishOptions.CLOUDEVENT_ID, UUID.randomUUID().toString()));
        event.put("source", md.getOrDefault(PublishOptions.CLOUDEVENT_SOURCE, appId));
        event.put("type", md.getOrDefault(PublishOptions.CLOUDEVENT_TYPE, "com.dapr.event.sent"));
        event.put("specversion", "1.0");
        event.put("datacontenttype", options.contentType() != null ? options.contentType() : "application/json");
        event.put("time", Instant.now(clock).toString());
        event.put("topic", topic);
        event.put("pubsubname", pubsub);
        if (md.containsKey(PublishOptions.CLOUDEVENT_TRACEPARENT)) {
            event.put("traceparent", md.get(PublishOptions.CLOUDEVENT_TRACEPARENT));
        }
        // Round-trip through JSON: subscribers see what they would receive from a broker.
        byte[] bytes = serializer.toBytes(data);
        Object payload;
        try {
            payload = bytes == null ? null : serializer.objectMapper().readValue(bytes, Object.class);
        } catch (IOException e) {
            payload = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        event.put("data", payload);
        return event;
    }

    // ---------------------------------------------------------------- secrets

    @Override
    public Map<String, String> getSecret(String store, String name, Map<String, String> metadata) {
        Map<String, String> secret = secrets.getOrDefault(store, Map.of()).get(name);
        if (secret == null) {
            throw new DaprError("Failed to get secret: secret '" + name + "' not found in memory store '" + store + "'",
                    404, "ERR_SECRET_GET", "secret " + name + " not found");
        }
        return secret;
    }

    @Override
    public Map<String, Map<String, String>> getBulkSecret(String store, Map<String, String> metadata) {
        return Map.copyOf(secrets.getOrDefault(store, Map.of()));
    }

    // ---------------------------------------------------------------- configuration

    @Override
    public Map<String, ConfigItem> getConfiguration(String store, List<String> keys, Map<String, String> metadata) {
        Map<String, ConfigItem> items = configuration.getOrDefault(store, Map.of());
        if (keys == null || keys.isEmpty()) {
            return Map.copyOf(items);
        }
        Map<String, ConfigItem> result = new LinkedHashMap<>();
        for (String key : keys) {
            ConfigItem item = items.get(key);
            if (item != null) {
                result.put(key, item);
            }
        }
        return result;
    }

    @Override
    public DaprSubscription subscribeConfiguration(String store, List<String> keys, Map<String, String> metadata,
                                                   Consumer<Map<String, ConfigItem>> onChange,
                                                   Consumer<Throwable> onError) {
        ConfigWatcher watcher = new ConfigWatcher(store, keys == null ? List.of() : List.copyOf(keys), onChange);
        watchers.add(watcher);
        return watcher;
    }

    // ---------------------------------------------------------------- lock

    @Override
    public boolean tryLock(String store, String resourceId, String owner, int expirySeconds) {
        Map<String, LockHolder> held = locks.computeIfAbsent(store, s -> new ConcurrentHashMap<>());
        Instant now = Instant.now(clock);
        synchronized (held) {
            LockHolder current = held.get(resourceId);
            if (current != null && current.expiresAt.isAfter(now)) {
                return false;
            }
            held.put(resourceId, new LockHolder(owner, now.plusSeconds(Math.max(1, expirySeconds))));
            return true;
        }
    }

    @Override
    public UnlockStatus unlock(String store, String resourceId, String owner) {
        Map<String, LockHolder> held = locks.computeIfAbsent(store, s -> new ConcurrentHashMap<>());
        synchronized (held) {
            LockHolder current = held.get(resourceId);
            if (current == null || !current.expiresAt.isAfter(Instant.now(clock))) {
                held.remove(resourceId);
                return UnlockStatus.LOCK_DOES_NOT_EXIST;
            }
            if (!current.owner.equals(owner)) {
                return UnlockStatus.LOCK_BELONGS_TO_OTHERS;
            }
            held.remove(resourceId);
            return UnlockStatus.SUCCESS;
        }
    }

    // ---------------------------------------------------------------- runtime

    @Override
    public DaprMetadata metadata() {
        List<ComponentMetadata> components = new ArrayList<>();
        stores.keySet().forEach(name -> components.add(new ComponentMetadata(name, "state.in-memory", "v1", List.of())));
        locks.keySet().forEach(name -> components.add(new ComponentMetadata(name, "lock.in-memory", "v1", List.of())));
        configuration.keySet().forEach(name ->
                components.add(new ComponentMetadata(name, "configuration.in-memory", "v1", List.of())));
        secrets.keySet().forEach(name ->
                components.add(new ComponentMetadata(name, "secretstores.in-memory", "v1", List.of())));
        List<SubscriptionMetadata> subscriptions = new ArrayList<>();
        listeners.forEach(l -> subscriptions.add(
                new SubscriptionMetadata(l.pubsub, l.topic, Map.of(), List.of(), l.deadLetterTopic)));
        return new DaprMetadata(appId, "memory", List.of(), List.of(), Map.of("mode", "memory"), components,
                List.of(), subscriptions, null);
    }

    @Override
    public boolean probe(Duration timeout) {
        return true;
    }

    @Override
    public void close() {
        listeners.clear();
        watchers.forEach(w -> w.active.set(false));
        watchers.clear();
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, StoredValue> store(String store) {
        return stores.computeIfAbsent(store, s -> new HashMap<>());
    }

    private StoredValue live(String store, String key) {
        Map<String, StoredValue> values = store(store);
        synchronized (values) {
            return liveLocked(values, key);
        }
    }

    private StoredValue liveLocked(Map<String, StoredValue> values, String key) {
        StoredValue stored = values.get(key);
        if (stored != null && stored.expiresAt != null && !stored.expiresAt.isAfter(Instant.now(clock))) {
            values.remove(key);
            return null;
        }
        return stored;
    }

    private void checkWrite(String store, String key, String etag, Concurrency concurrency,
                            Map<String, StoredValue> values) {
        StoredValue current = liveLocked(values, key);
        if (etag != null) {
            if (current == null || !current.etag.equals(etag)) {
                throw etagMismatch(store, key);
            }
        } else if (concurrency == Concurrency.FIRST_WRITE && current != null) {
            // First-write without an ETag means "insert": it must not overwrite someone else's value.
            throw etagMismatch(store, key);
        }
    }

    private StoredValue stored(Object value, SaveOptions options) {
        Duration ttl = options.ttl() != null ? options.ttl() : ttlOf(options.metadata());
        Instant expiresAt = ttl == null || ttl.isZero() || ttl.isNegative() ? null : Instant.now(clock).plus(ttl);
        return new StoredValue(serializer.toBytes(value), String.valueOf(etags.incrementAndGet()), expiresAt);
    }

    private static Duration ttlOf(Map<String, String> metadata) {
        String ttl = metadata == null ? null : metadata.get("ttlInSeconds");
        if (ttl == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(ttl.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static DaprError etagMismatch(String store, String key) {
        return new DaprError("Failed to save state: possible etag mismatch for key '" + key + "' in store '"
                + store + "'", 409, "DAPR_STATE_ETAG_MISMATCH", "possible etag mismatch. error from state store");
    }

    /** A stored, serialized value. */
    private record StoredValue(byte[] bytes, String etag, Instant expiresAt) {
    }

    private record LockHolder(String owner, Instant expiresAt) {
    }

    private final class TopicListener implements DaprSubscription {
        private final String pubsub;
        private final String topic;
        private final String deadLetterTopic;
        private final TopicEventListener listener;
        private final String id = UUID.randomUUID().toString();
        private final AtomicBoolean active = new AtomicBoolean(true);

        private TopicListener(String pubsub, String topic, String deadLetterTopic, TopicEventListener listener) {
            this.pubsub = pubsub;
            this.topic = topic;
            this.deadLetterTopic = deadLetterTopic == null || deadLetterTopic.isBlank() ? null : deadLetterTopic;
            this.listener = listener;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void unsubscribe() {
            if (active.getAndSet(false)) {
                listeners.remove(this);
            }
        }

        @Override
        public boolean isActive() {
            return active.get();
        }
    }

    private final class ConfigWatcher implements DaprSubscription {
        private final String store;
        private final List<String> keys;
        private final Consumer<Map<String, ConfigItem>> onChange;
        private final String id = UUID.randomUUID().toString();
        private final AtomicBoolean active = new AtomicBoolean(true);

        private ConfigWatcher(String store, List<String> keys, Consumer<Map<String, ConfigItem>> onChange) {
            this.store = store;
            this.keys = keys;
            this.onChange = onChange;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void unsubscribe() {
            if (active.getAndSet(false)) {
                watchers.remove(this);
            }
        }

        @Override
        public boolean isActive() {
            return active.get();
        }
    }

    /** Removes expired values everywhere (they are also dropped lazily on read). */
    public void evictExpired() {
        Instant now = Instant.now(clock);
        for (Map<String, StoredValue> values : stores.values()) {
            synchronized (values) {
                Iterator<Map.Entry<String, StoredValue>> it = values.entrySet().iterator();
                while (it.hasNext()) {
                    StoredValue v = it.next().getValue();
                    if (v.expiresAt != null && !v.expiresAt.isAfter(now)) {
                        it.remove();
                    }
                }
            }
        }
    }
}
