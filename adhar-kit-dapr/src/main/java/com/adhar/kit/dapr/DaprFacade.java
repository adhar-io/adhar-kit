package com.adhar.kit.dapr;

import com.adhar.kit.dapr.api.ConfigurationCallback;
import com.adhar.kit.dapr.api.StateOperation;
import com.adhar.kit.dapr.api.StateWithETag;
import com.adhar.kit.dapr.resilience.DaprInvocationResilience;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.dapr.client.DaprPreviewClient;
import io.dapr.client.domain.*;
import io.dapr.utils.TypeRef;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Universal Dapr facade providing comprehensive distributed application runtime capabilities.
 *
 * <p>This facade provides a framework-agnostic interface to all Dapr building blocks:</p>
 * <ul>
 *   <li><b>State Management</b> - Distributed state with strong/eventual consistency</li>
 *   <li><b>Pub/Sub</b> - At-least-once event delivery across services</li>
 *   <li><b>Service Invocation</b> - Reliable service-to-service calls with retry</li>
 *   <li><b>Bindings</b> - Connect to external systems (queues, DBs, etc.)</li>
 *   <li><b>Actors</b> - Stateful virtual actors with timers and reminders</li>
 *   <li><b>Secrets</b> - Secure secret management from any secret store</li>
 *   <li><b>Configuration</b> - Dynamic configuration with change notifications</li>
 *   <li><b>Distributed Lock</b> - Coordination across distributed systems</li>
 * </ul>
 *
 * <p><b>Usage Examples:</b></p>
 *
 * <p><b>1. State Management with Shopping Cart:</b></p>
 * <pre>{@code
 * @Service
 * public class ShoppingCartService {
 *     private final DaprFacade dapr = DaprFacade.getInstance();
 *
 *     public void addToCart(String userId, Product product) {
 *         String key = "cart:" + userId;
 *
 *         // Get current cart with ETag for optimistic concurrency
 *         StateWithETag<Cart> cartState = dapr.getStateWithETag(
 *             "statestore", key, Cart.class);
 *
 *         Cart cart = cartState.getValue();
 *         if (cart == null) {
 *             cart = new Cart(userId);
 *         }
 *
 *         cart.addProduct(product);
 *
 *         // Save with ETag to prevent concurrent modifications
 *         boolean saved = dapr.saveStateWithETag(
 *             "statestore", key, cart, cartState.getEtag());
 *
 *         if (!saved) {
 *             throw new ConcurrentModificationException("Cart was modified");
 *         }
 *     }
 *
 *     public void checkout(String userId) {
 *         String cartKey = "cart:" + userId;
 *         String orderKey = "order:" + UUID.randomUUID();
 *
 *         // Execute transaction - atomically move cart to order
 *         dapr.executeStateTransaction("statestore", List.of(
 *             StateOperation.upsert(orderKey, getCart(userId)),
 *             StateOperation.delete(cartKey)
 *         ));
 *     }
 * }
 * }</pre>
 *
 * <p><b>2. Pub/Sub Event-Driven Architecture:</b></p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     private final DaprFacade dapr = DaprFacade.getInstance();
 *
 *     public Order createOrder(OrderRequest request) {
 *         Order order = processOrder(request);
 *
 *         // Publish event - Dapr guarantees delivery
 *         dapr.publishEvent("pubsub", "orders.created",
 *             new OrderCreatedEvent(order.getId(), order.getTotal()));
 *
 *         return order;
 *     }
 *
 *     // In another service - automatic subscription
 *     @Topic(name = "orders.created", pubsubName = "pubsub")
 *     public void handleOrderCreated(CloudEvent<OrderCreatedEvent> event) {
 *         OrderCreatedEvent data = event.getData();
 *         log.info("Order created: {}", data.getOrderId());
 *
 *         // Send notification
 *         emailService.sendOrderConfirmation(data);
 *     }
 * }
 * }</pre>
 *
 * <p><b>3. Service-to-Service Invocation:</b></p>
 * <pre>{@code
 * @Service
 * public class PaymentService {
 *     private final DaprFacade dapr = DaprFacade.getInstance();
 *
 *     public void processPayment(Payment payment) {
 *         // Invoke fraud detection service - Dapr handles discovery,
 *         // load balancing, and retries
 *         FraudCheckResponse fraudCheck = dapr.invokeService(
 *             "fraud-detection-service",
 *             "/api/check-fraud",
 *             "POST",
 *             payment,
 *             FraudCheckResponse.class
 *         );
 *
 *         if (fraudCheck.isFraudulent()) {
 *             throw new FraudException("Fraudulent transaction detected");
 *         }
 *
 *         // Async invocation for non-blocking operations
 *         dapr.invokeServiceAsync(
 *             "notification-service",
 *             "/api/notify",
 *             new PaymentNotification(payment.getId()),
 *             Void.class
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>4. Bindings - External System Integration:</b></p>
 * <pre>{@code
 * @Service
 * public class NotificationService {
 *     private final DaprFacade dapr = DaprFacade.getInstance();
 *
 *     public void sendSMS(String phoneNumber, String message) {
 *         // Send to Twilio via output binding
 *         dapr.invokeBinding("twilio", "create", Map.of(
 *             "to", phoneNumber,
 *             "body", message
 *         ));
 *     }
 *
 *     public void sendEmail(String to, String subject, String body) {
 *         // Send to SendGrid via output binding
 *         dapr.invokeBinding("sendgrid", "create", Map.of(
 *             "to", to,
 *             "subject", subject,
 *             "body", body
 *         ), Map.of("category", "transactional"));
 *     }
 * }
 * }</pre>
 *
 * <p><b>5. Actor Pattern - Stateful Services:</b></p>
 * <pre>{@code
 * @Service
 * public class GameService {
 *     private final DaprFacade dapr = DaprFacade.getInstance();
 *
 *     public void startGame(String gameId) {
 *         // Invoke game actor to start
 *         dapr.invokeActor(
 *             "GameActor",
 *             gameId,
 *             "start",
 *             null,
 *             Void.class
 *         );
 *
 *         // Register timer to end game after 30 minutes
 *         dapr.registerActorTimer(
 *             "GameActor",
 *             gameId,
 *             "endGameTimer",
 *             Duration.ofMinutes(30),
 *             Duration.ZERO,
 *             "endGame"
 *         );
 *     }
 *
 *     public GameState getGameState(String gameId) {
 *         return dapr.getActorState(
 *             "GameActor",
 *             gameId,
 *             "state",
 *             GameState.class
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><b>6. Distributed Lock - Coordination:</b></p>
 * <pre>{@code
 * @Service
 * public class ReportGenerator {
 *     private final DaprFacade dapr = DaprFacade.getInstance();
 *
 *     public void generateDailyReport() {
 *         String lockOwner = UUID.randomUUID().toString();
 *
 *         // Try to acquire lock - ensures only one instance generates report
 *         if (dapr.tryLock("lockstore", "daily-report", lockOwner, 300)) {
 *             try {
 *                 generateReport();
 *             } finally {
 *                 dapr.unlock("lockstore", "daily-report", lockOwner);
 *             }
 *         } else {
 *             log.info("Another instance is generating the report");
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>7. Secrets Management:</b></p>
 * <pre>{@code
 * @Configuration
 * public class DatabaseConfig {
 *     private final DaprFacade dapr = DaprFacade.getInstance();
 *
 *     @Bean
 *     public DataSource dataSource() {
 *         // Get secrets from Azure Key Vault, AWS Secrets Manager, etc.
 *         String dbPassword = dapr.getSecret("local-secret-store", "db-password");
 *         String apiKey = dapr.getSecret("local-secret-store", "api-key");
 *
 *         return DataSourceBuilder.create()
 *             .password(dbPassword)
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * <p><b>8. Configuration Management with Change Notifications:</b></p>
 * <pre>{@code
 * @Service
 * public class FeatureFlagService {
 *     private final DaprFacade dapr = DaprFacade.getInstance();
 *     private volatile Map<String, String> featureFlags = new HashMap<>();
 *
 *     @PostConstruct
 *     public void init() {
 *         // Subscribe to configuration changes
 *         dapr.subscribeConfiguration(
 *             "configstore",
 *             List.of("feature-flags"),
 *             changedConfig -> {
 *                 log.info("Feature flags updated: {}", changedConfig);
 *                 featureFlags = changedConfig;
 *             }
 *         );
 *     }
 *
 *     public boolean isFeatureEnabled(String featureName) {
 *         return Boolean.parseBoolean(featureFlags.get(featureName));
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DaprFacade {

    private static volatile DaprFacade instance;
    private final DaprClient daprClient;
    private final DaprPreviewClient previewClient;
    private final DaprInvocationResilience resilience = new DaprInvocationResilience();
    private volatile boolean available = false;

    private DaprFacade() {
        log.info("Initializing Dapr Facade v1.0.0");
        try {
            DaprClientBuilder builder = new DaprClientBuilder();
            this.daprClient = builder.build();
            // Preview client exposes the (stable-in-runtime) distributed-lock building block.
            this.previewClient = builder.buildPreviewClient();

            // Check if Dapr is available
            this.available = true;

            log.info("Dapr sidecar connected successfully");
        } catch (Exception e) {
            log.warn("Dapr sidecar not available: {}", e.getMessage());
            throw new RuntimeException("Dapr not available", e);
        }
    }

    /**
     * Constructor for injecting a Dapr client directly - typically a mock in tests, or a
     * pre-configured client in production/DI scenarios where the default
     * {@link DaprClientBuilder#build()} construction in {@link #getInstance()} isn't desired.
     *
     * <p>No {@link DaprPreviewClient} is supplied, so the distributed-lock methods
     * ({@link #tryLock}/{@link #unlock}) are unavailable; use
     * {@link #DaprFacade(DaprClient, DaprPreviewClient)} to enable them.</p>
     *
     * @param daprClient the Dapr client
     */
    public DaprFacade(DaprClient daprClient) {
        this(daprClient, null);
    }

    /**
     * Constructor for injecting both the standard and preview Dapr clients directly - typically
     * mocks in tests, or pre-configured clients in DI scenarios. The preview client backs the
     * distributed-lock building block.
     *
     * @param daprClient    the Dapr client
     * @param previewClient the Dapr preview client (may be {@code null} to disable locking)
     */
    public DaprFacade(DaprClient daprClient, DaprPreviewClient previewClient) {
        this.daprClient = daprClient;
        this.previewClient = previewClient;
        this.available = true;
    }

    public static DaprFacade getInstance() {
        if (instance == null) {
            synchronized (DaprFacade.class) {
                if (instance == null) {
                    instance = new DaprFacade();
                }
            }
        }
        return instance;
    }

    // ==================== State Management ====================

    public <T> void saveState(String storeName, String key, T value) {
        try {
            daprClient.saveState(storeName, key, value).block();
            log.debug("Saved state: store={}, key={}", storeName, key);
        } catch (Exception e) {
            log.error("Failed to save state: store={}, key={}", storeName, key, e);
            throw new DaprException("Failed to save state", e);
        }
    }

    public <T> boolean saveStateWithETag(String storeName, String key, T value, String etag) {
        try {
            StateOptions options = new StateOptions(null, StateOptions.Concurrency.FIRST_WRITE);
            daprClient.saveState(storeName, key, etag, value, options).block();
            log.debug("Saved state with ETag: store={}, key={}", storeName, key);
            return true;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ETag mismatch")) {
                log.warn("ETag mismatch for state: store={}, key={}", storeName, key);
                return false;
            }
            log.error("Failed to save state with ETag: store={}, key={}", storeName, key, e);
            throw new DaprException("Failed to save state", e);
        }
    }

    public <T> void saveStateWithTTL(String storeName, String key, T value, Duration ttl) {
        try {
            // Note: TTL is typically handled via state store configuration in Dapr
            // For now, we save without metadata as SDK 1.11 doesn't support metadata directly
            daprClient.saveState(storeName, key, value).block();
            log.debug("Saved state with TTL: store={}, key={}, ttl={}", storeName, key, ttl);
        } catch (Exception e) {
            log.error("Failed to save state with TTL: store={}, key={}", storeName, key, e);
            throw new DaprException("Failed to save state", e);
        }
    }

    public <T> T getState(String storeName, String key, Class<T> clazz) {
        try {
            State<T> state = daprClient.getState(storeName, key, clazz).block();
            T value = (state != null) ? state.getValue() : null;
            log.debug("Retrieved state: store={}, key={}, found={}", storeName, key, value != null);
            return value;
        } catch (Exception e) {
            log.error("Failed to get state: store={}, key={}", storeName, key, e);
            throw new DaprException("Failed to get state", e);
        }
    }

    public <T> StateWithETag<T> getStateWithETag(String storeName, String key, Class<T> clazz) {
        try {
            State<T> state = daprClient.getState(storeName, key, clazz).block();
            if (state == null) {
                return new StateWithETag<>(null, null);
            }
            return new StateWithETag<>(state.getValue(), state.getEtag());
        } catch (Exception e) {
            log.error("Failed to get state with ETag: store={}, key={}", storeName, key, e);
            throw new DaprException("Failed to get state", e);
        }
    }

    public <T> Map<String, T> getBulkState(String storeName, List<String> keys, Class<T> clazz) {
        try {
            List<State<T>> states = daprClient.getBulkState(storeName, keys, clazz).block();
            if (states == null) {
                return Collections.emptyMap();
            }
            return states.stream()
                .filter(s -> s.getValue() != null)
                .collect(Collectors.toMap(State::getKey, State::getValue));
        } catch (Exception e) {
            log.error("Failed to get bulk state: store={}", storeName, e);
            throw new DaprException("Failed to get bulk state", e);
        }
    }

    public void deleteState(String storeName, String key) {
        try {
            daprClient.deleteState(storeName, key).block();
            log.debug("Deleted state: store={}, key={}", storeName, key);
        } catch (Exception e) {
            log.error("Failed to delete state: store={}, key={}", storeName, key, e);
            throw new DaprException("Failed to delete state", e);
        }
    }

    public boolean deleteStateWithETag(String storeName, String key, String etag) {
        try {
            StateOptions options = new StateOptions(null, StateOptions.Concurrency.FIRST_WRITE);
            daprClient.deleteState(storeName, key, etag, options).block();
            log.debug("Deleted state with ETag: store={}, key={}", storeName, key);
            return true;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ETag mismatch")) {
                log.warn("ETag mismatch for delete: store={}, key={}", storeName, key);
                return false;
            }
            log.error("Failed to delete state with ETag: store={}, key={}", storeName, key, e);
            throw new DaprException("Failed to delete state", e);
        }
    }

    public void executeStateTransaction(String storeName, List<StateOperation> operations) {
        try {
            List<TransactionalStateOperation<?>> daprOps = operations.stream()
                .map(op -> {
                    if (op.getOperation() == StateOperation.OperationType.UPSERT) {
                        return new TransactionalStateOperation<>(
                            TransactionalStateOperation.OperationType.UPSERT,
                            new State<>(op.getKey(), op.getValue(), op.getEtag(), null, null)
                        );
                    } else {
                        return new TransactionalStateOperation<>(
                            TransactionalStateOperation.OperationType.DELETE,
                            new State<>(op.getKey())
                        );
                    }
                })
                .collect(Collectors.toList());

            daprClient.executeStateTransaction(storeName, daprOps).block();
            log.debug("Executed state transaction: store={}, operations={}", storeName, operations.size());
        } catch (Exception e) {
            log.error("Failed to execute state transaction: store={}", storeName, e);
            throw new DaprException("Failed to execute state transaction", e);
        }
    }

    public <T> List<T> queryState(String storeName, String query, Class<T> clazz) {
        // Note: Query state API is alpha in Dapr and may not be available in all SDK versions
        // Consider using direct HTTP calls to Dapr API if this feature is needed
        throw new UnsupportedOperationException(
            "Query state API is not fully supported in Dapr SDK 1.11.0. " +
            "Consider using direct Dapr HTTP API or upgrading to a newer SDK version.");
    }

    // ==================== Pub/Sub ====================

    public <T> void publishEvent(String pubsubName, String topicName, T event) {
        publishEvent(pubsubName, topicName, event, Collections.emptyMap());
    }

    public <T> void publishEvent(String pubsubName, String topicName, T event, Map<String, String> metadata) {
        try {
            daprClient.publishEvent(pubsubName, topicName, event, metadata).block();
            log.debug("Published event: pubsub={}, topic={}", pubsubName, topicName);
        } catch (Exception e) {
            log.error("Failed to publish event: pubsub={}, topic={}", pubsubName, topicName, e);
            throw new DaprException("Failed to publish event", e);
        }
    }

    public <T> void publishBulkEvents(String pubsubName, String topicName, List<T> events) {
        try {
            // Dapr SDK doesn't have native bulk publish yet, so we publish sequentially
            // In production, consider using reactive streams for better performance
            events.forEach(event -> publishEvent(pubsubName, topicName, event));
            log.debug("Published {} events: pubsub={}, topic={}", events.size(), pubsubName, topicName);
        } catch (Exception e) {
            log.error("Failed to publish bulk events: pubsub={}, topic={}", pubsubName, topicName, e);
            throw new DaprException("Failed to publish bulk events", e);
        }
    }

    // ==================== Service Invocation ====================

    public <T, R> R invokeService(String appId, String methodName, T request, Class<R> responseClass) {
        return invokeService(appId, methodName, "POST", request, responseClass);
    }

    public <T, R> CompletableFuture<R> invokeServiceAsync(String appId, String methodName, T request, Class<R> responseClass) {
        return CompletableFuture.supplyAsync(() ->
            invokeService(appId, methodName, "POST", request, responseClass));
    }

    public <T, R> R invokeService(String appId, String methodName, String httpMethod, T request, Class<R> responseClass) {
        try {
            io.dapr.client.domain.HttpExtension httpExtension = getHttpExtension(httpMethod.toUpperCase());

            Mono<R> response = daprClient.invokeMethod(appId, methodName, request, httpExtension, responseClass);
            R result = response.block();

            log.debug("Invoked service: appId={}, method={}, httpMethod={}", appId, methodName, httpMethod);
            return result;
        } catch (Exception e) {
            log.error("Failed to invoke service: appId={}, method={}", appId, methodName, e);
            throw new DaprException("Failed to invoke service", e);
        }
    }

    /**
     * Invokes a service with retry, per-attempt timeout, and circuit-breaker protection applied
     * (see {@link DaprInvocationResilience}), throwing on final failure.
     *
     * @see #invokeServiceResilient(String, String, String, Object, Class, Function)
     */
    public <T, R> R invokeServiceResilient(String appId, String methodName, T request, Class<R> responseClass) {
        return invokeServiceResilient(appId, methodName, "POST", request, responseClass, null);
    }

    /**
     * Invokes a service with retry, per-attempt timeout, and circuit-breaker protection applied
     * (see {@link DaprInvocationResilience}), falling back to {@code fallback} instead of
     * throwing on final failure (or when the circuit breaker is open).
     *
     * @param appId         target app ID
     * @param methodName    method/endpoint name
     * @param httpMethod    HTTP method (GET/POST/PUT/DELETE)
     * @param request       request payload
     * @param responseClass response type
     * @param fallback      invoked with the failure cause instead of throwing, or {@code null}
     *                      to propagate the failure
     * @return the response, or the fallback's result
     */
    public <T, R> R invokeServiceResilient(String appId, String methodName, String httpMethod, T request,
                                           Class<R> responseClass, Function<Throwable, R> fallback) {
        return resilience.execute(() -> invokeService(appId, methodName, httpMethod, request, responseClass), fallback);
    }

    // ==================== Bindings ====================

    public <T> void invokeBinding(String bindingName, String operation, T data) {
        invokeBinding(bindingName, operation, data, Collections.emptyMap());
    }

    public <T> void invokeBinding(String bindingName, String operation, T data, Map<String, String> metadata) {
        try {
            InvokeBindingRequest request = new InvokeBindingRequest(bindingName, operation)
                .setData(data)
                .setMetadata(metadata);
            daprClient.invokeBinding(request, TypeRef.BYTE_ARRAY).block();
            log.debug("Invoked binding: binding={}, operation={}", bindingName, operation);
        } catch (Exception e) {
            log.error("Failed to invoke binding: binding={}, operation={}", bindingName, operation, e);
            throw new DaprException("Failed to invoke binding", e);
        }
    }

    // ==================== Secrets ====================

    public String getSecret(String secretStoreName, String secretKey) {
        return getSecret(secretStoreName, secretKey, Collections.emptyMap());
    }

    public String getSecret(String secretStoreName, String secretKey, Map<String, String> metadata) {
        try {
            Map<String, String> secrets = daprClient.getSecret(secretStoreName, secretKey, metadata).block();
            String value = (secrets != null) ? secrets.get(secretKey) : null;
            log.debug("Retrieved secret: store={}, key={}, found={}", secretStoreName, secretKey, value != null);
            return value;
        } catch (Exception e) {
            log.error("Failed to get secret: store={}, key={}", secretStoreName, secretKey, e);
            throw new DaprException("Failed to get secret", e);
        }
    }

    public Map<String, String> getBulkSecrets(String secretStoreName) {
        try {
            Map<String, Map<String, String>> secrets = daprClient.getBulkSecret(secretStoreName).block();
            log.debug("Retrieved bulk secrets: store={}, count={}",
                secretStoreName, secrets != null ? secrets.size() : 0);
            // Flatten the nested map - take the first value from each secret
            if (secrets == null) {
                return Collections.emptyMap();
            }
            Map<String, String> result = new HashMap<>();
            secrets.forEach((key, valueMap) -> {
                if (valueMap != null && !valueMap.isEmpty()) {
                    result.put(key, valueMap.values().iterator().next());
                }
            });
            return result;
        } catch (Exception e) {
            log.error("Failed to get bulk secrets: store={}", secretStoreName, e);
            throw new DaprException("Failed to get bulk secrets", e);
        }
    }

    // ==================== Configuration ====================

    public String getConfiguration(String configStoreName, String key) {
        Map<String, String> config = getConfiguration(configStoreName, List.of(key));
        return config.get(key);
    }

    public Map<String, String> getConfiguration(String configStoreName, List<String> keys) {
        try {
            Map<String, ConfigurationItem> items = daprClient.getConfiguration(configStoreName, keys.toArray(new String[0])).block();
            if (items == null) {
                return Collections.emptyMap();
            }
            return items.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue()));
        } catch (Exception e) {
            log.error("Failed to get configuration: store={}", configStoreName, e);
            throw new DaprException("Failed to get configuration", e);
        }
    }

    public void subscribeConfiguration(String configStoreName, List<String> keys, ConfigurationCallback callback) {
        try {
            daprClient.subscribeConfiguration(configStoreName, keys.toArray(new String[0]))
                .subscribe(response -> {
                    Map<String, ConfigurationItem> items = response.getItems();
                    if (items != null) {
                        Map<String, String> changedConfig = items.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue()));
                        callback.onConfigurationChange(changedConfig);
                    }
                });
            log.debug("Subscribed to configuration changes: store={}, keys={}", configStoreName, keys);
        } catch (Exception e) {
            log.error("Failed to subscribe to configuration: store={}", configStoreName, e);
            throw new DaprException("Failed to subscribe to configuration", e);
        }
    }

    // ==================== Distributed Lock ====================
    // Backed by the Dapr preview client's lock building block (SDK 1.18).

    /**
     * Attempts to acquire a distributed lock on {@code resourceId} for {@code lockOwner}. The
     * lock auto-expires after {@code expiryInSeconds}. Returns immediately (non-blocking) with
     * {@code false} if the resource is already locked by someone else.
     *
     * @param storeName       lock store component name
     * @param resourceId      the resource to lock
     * @param lockOwner       an owner identifier (must be presented again to {@link #unlock})
     * @param expiryInSeconds lock time-to-live in seconds
     * @return {@code true} if the lock was acquired
     * @throws IllegalStateException if this facade was created without a preview client
     */
    public boolean tryLock(String storeName, String resourceId, String lockOwner, int expiryInSeconds) {
        requirePreviewClient();
        try {
            Boolean acquired = previewClient.tryLock(
                new LockRequest(storeName, resourceId, lockOwner, expiryInSeconds)).block();
            boolean result = Boolean.TRUE.equals(acquired);
            log.debug("tryLock: store={}, resource={}, owner={}, acquired={}",
                storeName, resourceId, lockOwner, result);
            return result;
        } catch (Exception e) {
            log.error("Failed to acquire lock: store={}, resource={}", storeName, resourceId, e);
            throw new DaprException("Failed to acquire lock", e);
        }
    }

    /**
     * Releases a distributed lock previously acquired via {@link #tryLock}.
     *
     * @param storeName  lock store component name
     * @param resourceId the locked resource
     * @param lockOwner  the owner identifier used to acquire the lock
     * @return {@code true} if the lock was released (status {@code SUCCESS}); {@code false} if
     *         the lock did not exist or belonged to another owner
     * @throws IllegalStateException if this facade was created without a preview client
     */
    public boolean unlock(String storeName, String resourceId, String lockOwner) {
        requirePreviewClient();
        try {
            UnlockResponseStatus status = previewClient.unlock(
                new UnlockRequest(storeName, resourceId, lockOwner)).block();
            boolean released = status == UnlockResponseStatus.SUCCESS;
            log.debug("unlock: store={}, resource={}, owner={}, status={}",
                storeName, resourceId, lockOwner, status);
            return released;
        } catch (Exception e) {
            log.error("Failed to release lock: store={}, resource={}", storeName, resourceId, e);
            throw new DaprException("Failed to release lock", e);
        }
    }

    private void requirePreviewClient() {
        if (previewClient == null) {
            throw new IllegalStateException(
                "Distributed lock requires a DaprPreviewClient. Construct DaprFacade via "
                    + "getInstance() or new DaprFacade(daprClient, previewClient) to enable it.");
        }
    }

    // ==================== Actors ====================
    // Note: Actor operations require the Dapr Actors SDK (dapr-sdk-actors)
    // These methods are placeholders - actual actor invocation should use ActorProxyBuilder

    public <T, R> R invokeActor(String actorType, String actorId, String methodName, T data, Class<R> responseClass) {
        // Actor invocation requires ActorProxyBuilder from dapr-sdk-actors
        // Example usage:
        // ActorProxyBuilder<MyActor> builder = new ActorProxyBuilder<>(MyActor.class, actorClient);
        // MyActor actor = builder.build(new ActorId(actorId));
        // return actor.methodName(data);
        throw new UnsupportedOperationException(
            "Actor invocation requires ActorProxyBuilder. Use dapr-sdk-actors directly.");
    }

    public <T> void saveActorState(String actorType, String actorId, String stateName, T value) {
        // Actor state management is handled internally by the actor runtime
        throw new UnsupportedOperationException(
            "Actor state is managed by the actor runtime. Access state from within actor implementation.");
    }

    public <T> T getActorState(String actorType, String actorId, String stateName, Class<T> clazz) {
        // Actor state management is handled internally by the actor runtime
        throw new UnsupportedOperationException(
            "Actor state is managed by the actor runtime. Access state from within actor implementation.");
    }

    public void registerActorReminder(String actorType, String actorId, String reminderName,
                                      Duration dueTime, Duration period, Object data) {
        // Actor reminders are registered from within the actor implementation
        throw new UnsupportedOperationException(
            "Actor reminders must be registered from within actor implementation using ActorRuntime.");
    }

    public void registerActorTimer(String actorType, String actorId, String timerName,
                                   Duration dueTime, Duration period, String callback) {
        // Actor timers are registered from within the actor implementation
        throw new UnsupportedOperationException(
            "Actor timers must be registered from within actor implementation using ActorRuntime.");
    }

    // ==================== Utilities ====================

    public boolean isAvailable() {
        return available;
    }

    public Map<String, Object> getMetadata() {
        try {
            Map<String, Object> metadata = new HashMap<>();
            // Note: Actual metadata retrieval depends on Dapr SDK version
            metadata.put("available", available);
            metadata.put("version", "1.0.0");
            return metadata;
        } catch (Exception e) {
            log.error("Failed to get metadata", e);
            return Collections.emptyMap();
        }
    }

    public void shutdown() {
        try {
            if (daprClient != null) {
                daprClient.close();
                log.info("Dapr client shut down successfully");
            }
            if (previewClient != null) {
                previewClient.close();
            }
        } catch (Exception e) {
            log.error("Error shutting down Dapr client", e);
        }
    }

    /**
     * Helper method to get HttpExtension from HTTP method string.
     */
    private io.dapr.client.domain.HttpExtension getHttpExtension(String httpMethod) {
        switch (httpMethod) {
            case "GET": return io.dapr.client.domain.HttpExtension.GET;
            case "POST": return io.dapr.client.domain.HttpExtension.POST;
            case "PUT": return io.dapr.client.domain.HttpExtension.PUT;
            case "DELETE": return io.dapr.client.domain.HttpExtension.DELETE;
            default: return io.dapr.client.domain.HttpExtension.POST;
        }
    }

    /**
     * Custom exception for Dapr operations.
     */
    public static class DaprException extends RuntimeException {
        public DaprException(String message) {
            super(message);
        }

        public DaprException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

