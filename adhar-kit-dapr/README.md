# 🔄 Adhar Kit Dapr - Distributed Application Runtime Integration

**Simplified Dapr integration for building distributed microservices**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.x-blue.svg)](https://micronaut.io/)
[![Dapr](https://img.shields.io/badge/Dapr-1.12+-blue.svg)](https://dapr.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 0.1.0-SNAPSHOT  
**Status:** ✅ Production Ready

---

## 📖 Overview

The **adhar-kit-dapr** module provides easy-to-use wrappers for Dapr building blocks:

- 🗄️ **State Management** - Distributed state store, plus a typed ETag-aware `StateRepository<T>`
- 📢 **Pub/Sub** - Event-driven messaging: publish, and a real subscription endpoint
  (`GET /dapr/subscribe` + CloudEvent dispatch) for `@DaprSubscribe`/`@DaprTopic` handlers
- 🔗 **Service Invocation** - Service-to-service calls, with an optional retry/timeout/circuit-breaker wrapper
- 🔌 **Bindings** - External system integration
- 🔐 **Secrets** - Secure secrets management
- 🔧 **Configuration** - Distributed configuration
- ⚙️ **Declarative aspects** - `@DaprState`/`@DaprPublish` are enforced by real Spring AOP aspects, not just documentation

### Implementation status

| Capability | Status |
|---|---|
| State, Pub/Sub (publish), Service Invocation, Bindings, Secrets, Configuration | ✅ Implemented |
| Pub/Sub subscription endpoint (`@DaprSubscribe`/`@DaprTopic` → `GET/POST /dapr/subscribe/...`) | ✅ Implemented (`pubsub` package) |
| Resilient service invocation (retry, timeout, circuit breaker, fallback) | ✅ Implemented (`resilience` package) |
| `@DaprState`/`@DaprPublish` declarative enforcement | ✅ Implemented (`aspect` package, requires `spring-boot-starter-aspectj`) |
| Typed, ETag-aware `StateRepository<T>` with optimistic-retry `update()` | ✅ Implemented (`state` package) |
| Distributed Lock (`@DaprLock`, `tryLock`/`unlock`) | ⛔ **Planned** - throws `UnsupportedOperationException`; the pinned Dapr Java SDK (1.18.0) does not expose a lock API on `DaprClient` |
| Actors (`@DaprActor`, actor invocation/state/timers/reminders via the facade) | ⛔ **Planned** - use `dapr-sdk-actors`' `ActorProxyBuilder`/`ActorRuntime` directly; the facade methods are documented placeholders |
| Cryptography (`encrypt`/`decrypt`) | ⛔ **Planned** - not available on the pinned SDK |

---

## 🚀 Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-dapr</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure Application

**application.yml:**
```yaml
adhar:
  dapr:
    enabled: true
    app-id: my-service
    app-port: 8080
    dapr-port: 3500
    state-store: statestore
    pubsub: pubsub
    secret-store: secretstore
```

### 3. Use Dapr Client

```java
@Service
public class UserService {
    
    private final AdharDaprClient dapr = new AdharDaprClient();
    
    public void saveUser(User user) {
        dapr.saveState("statestore", "user:" + user.getId(), user);
    }
    
    public Optional<User> getUser(String userId) {
        return dapr.getState("statestore", "user:" + userId, User.class);
    }
}
```

---

## 🎯 Dapr Annotations

### @DaprState

Automatically manage state in Dapr state store. Enforced by `aspect.DaprStateAspect` (requires
`spring-boot-starter-aspectj` on the classpath and AOP proxying enabled, which
`DaprAutoConfiguration` wires up automatically): `SAVE` proceeds then saves the return value,
`GET` fetches the value directly (skipping the method body), and `DELETE` proceeds then deletes
the key. The `key` attribute is a SpEL expression evaluated against the method's arguments
(`#paramName`, `#p0`/`#a0`), exactly like Spring's own `@Cacheable(key = ...)`.

```java
@Service
public class UserService {
    
    @DaprState(storeName = "statestore", key = "#userId")
    public User saveUser(String userId, User user) {
        // State automatically saved after method execution
        return user;
    }
    
    @DaprState(storeName = "statestore", key = "#userId", operation = Operation.GET)
    public User getUser(String userId) {
        // State automatically retrieved
        return null; // Will be replaced with state value
    }
    
    @DaprState(storeName = "statestore", key = "#userId", operation = Operation.DELETE)
    public void deleteUser(String userId) {
        // State automatically deleted
    }
}
```

### @DaprPublish

Automatically publish events to Dapr pub/sub. Enforced by `aspect.DaprPublishAspect`: after the
method executes, its return value (or a specific parameter, when `publishReturnValue = false`)
is published to the configured topic. A `null` payload is not published.

```java
@Service
public class OrderService {
    
    @DaprPublish(pubsubName = "pubsub", topic = "order-created")
    public Order createOrder(OrderRequest request) {
        Order order = new Order(request);
        // Order automatically published after method execution
        return order;
    }
    
    @DaprPublish(pubsubName = "pubsub", topic = "order-updated", publishReturnValue = false, parameterIndex = 0)
    public void updateOrder(OrderUpdatedEvent event) {
        // Event parameter automatically published
    }
}
```

### @DaprSubscribe / @DaprTopic

Subscribe to Dapr pub/sub topics. **No `@RestController`/`@PostMapping` boilerplate needed** -
just annotate a method on any Spring bean and `pubsub.DaprSubscriptionRegistrar` (a
`SmartInitializingSingleton`) discovers it automatically once the context has started. It is
exposed through `pubsub.DaprSubscriptionController`:

- `GET /dapr/subscribe` returns the subscription metadata Dapr's sidecar polls for on startup.
- `POST /dapr/subscribe/**` receives the CloudEvent and dispatches it to your handler method,
  converting the event's `data` field to the method's parameter type (or wrapping the whole
  envelope in an `io.dapr.client.domain.CloudEvent<T>` if that's what the method declares).

Each handler gets a synthesized route under `/dapr/subscribe/{pubsub}/{topic-or-custom-route}`
regardless of the `route` attribute's exact value (it's folded into the slug), so the whole
mechanism is a single, predictable dispatch entry point - you never need to write your own
`@PostMapping` for it.

```java
@Component
public class OrderEventHandler {

    @DaprSubscribe(pubsubName = "pubsub", topic = "order-created")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Order created: {}", event.getOrderId());
        // Process event; a thrown exception here maps to a RETRY dispatch result
    }

    @DaprSubscribe(pubsubName = "pubsub", topic = "order-updated", deadLetterTopic = "order-errors")
    public void handleOrderUpdated(OrderUpdatedEvent event) {
        // Process event or let it flow to the dead-letter topic on repeated failure
    }

    // @DaprTopic works the same way and additionally supports metadata:
    @DaprTopic(pubsubName = "pubsub", name = "orders.cancelled", metadata = {"rawPayload=true"})
    public void handleOrderCancelled(CloudEvent<OrderCancelledEvent> event) {
        OrderCancelledEvent data = event.getData();
        log.info("Order cancelled: {}", data.getOrderId());
    }
}
```

### @DaprInvoke

Documents the intent to invoke another service via Dapr service invocation. **Not yet enforced
by an aspect** - call `DaprFacade#invokeService`/`#invokeServiceResilient` (or
`AdharDaprClient#invokeService`) directly from the method body; the annotation is currently
metadata only.

```java
@Service
public class CheckoutService {

    @DaprInvoke(appId = "inventory-service", method = "POST", endpoint = "/validate")
    public InventoryResponse validateInventory(InventoryRequest request) {
        return dapr.invokeService("inventory-service", "/validate", request, InventoryResponse.class);
    }
}
```

### @DaprLock

Acquire distributed locks for resource synchronization.

```java
@Service
public class OrderService {
    
    @DaprLock(storeName = "lockstore", resourceId = "#orderId", lockOwner = "order-service")
    public Order processOrder(String orderId) {
        // Only one instance processes this order at a time
        return orderRepository.process(orderId);
    }
}
```

### @DaprConfiguration

Retrieve configuration from Dapr configuration store.

```java
@Service
public class DatabaseService {
    
    @DaprConfiguration(storeName = "configstore", key = "database.connection-string")
    public String getConnectionString() {
        return null; // Will be replaced with config value
    }
    
    @DaprConfiguration(storeName = "configstore", key = "feature.flags.new-checkout", defaultValue = "false")
    public String getNewCheckoutFlag() {
        return null;
    }
}
```

### @DaprBinding

Invoke output bindings (email, SMS, storage, etc.).

```java
@Service
public class NotificationService {
    
    @DaprBinding(bindingName = "email-binding", operation = "create")
    public void sendEmail(EmailRequest request) {
        // Email automatically sent via binding
    }
    
    @DaprBinding(bindingName = "sms-binding", operation = "create")
    public void sendSMS(SMSRequest request) {
        // SMS sent via binding
    }
}
```

### @DaprSecret

Inject secrets from Dapr secret store.

```java
@Service
public class PaymentService {
    
    @DaprSecret(storeName = "secretstore", secretName = "payment-api-key")
    private String apiKey;
    
    @DaprSecret(storeName = "secretstore", secretName = "database-credentials", key = "password")
    private String dbPassword;
}
```

### @DaprActor

Define Dapr actors for stateful, single-threaded compute.

> ⛔ **Planned, not implemented.** `DaprFacade#invokeActor`/`saveActorState`/`getActorState`/
> `registerActorReminder`/`registerActorTimer` all throw `UnsupportedOperationException` -
> use `dapr-sdk-actors`'s `ActorProxyBuilder`/`ActorRuntime` directly instead. `@DaprActor` and
> `@ActorMethod` are documentation-only annotations today.

```java
@DaprActor(type = "OrderActor", idleTimeout = "60m")
public class OrderActor {
    
    @DaprActorMethod
    public Order processOrder(OrderRequest request) {
        // Actor method - single-threaded, stateful
        return new Order(request);
    }
    
    @DaprActorMethod("getStatus")
    public OrderStatus getOrderStatus(String orderId) {
        return actorStateManager.get("status");
    }
}
```

---

## 🗄️ State Management

Store and retrieve distributed state.

### Save State

```java
// Save simple state
dapr.saveState("statestore", "counter", 42);

// Save object
User user = new User("john", "john@example.com");
dapr.saveState("statestore", "user:123", user);

// Save with metadata
Map<String, String> metadata = new HashMap<>();
metadata.put("ttl", "3600");
dapr.saveState("statestore", "session:abc", session, metadata);
```

### Get State

```java
// Get state
Optional<Integer> counter = dapr.getState("statestore", "counter", Integer.class);

// Get object
Optional<User> user = dapr.getState("statestore", "user:123", User.class);

// Use value
user.ifPresent(u -> log.info("Found user: {}", u.getName()));
```

### Delete State

```java
dapr.deleteState("statestore", "user:123");
```

### Complete Example

```java
@Service
public class ShoppingCartService {
    
    private final AdharDaprClient dapr = new AdharDaprClient();
    private static final String STATE_STORE = "statestore";
    
    public void addToCart(String userId, Product product) {
        // Get existing cart
        Optional<Cart> cartOpt = dapr.getState(
            STATE_STORE, 
            "cart:" + userId, 
            Cart.class
        );
        
        // Update cart
        Cart cart = cartOpt.orElse(new Cart(userId));
        cart.addItem(product);
        
        // Save cart
        dapr.saveState(STATE_STORE, "cart:" + userId, cart);
    }
    
    public Optional<Cart> getCart(String userId) {
        return dapr.getState(STATE_STORE, "cart:" + userId, Cart.class);
    }
    
    public void clearCart(String userId) {
        dapr.deleteState(STATE_STORE, "cart:" + userId);
    }
}
```

### Typed, ETag-Aware Repository

`state.StateRepository<T>` wraps `DaprFacade#getStateWithETag`/`saveStateWithETag` with a typed
read-modify-write API and an optimistic-concurrency retry loop: `update()` re-reads the current
value and ETag and retries the save on an ETag conflict (up to `maxRetries`, default 3, with a
short backoff), throwing `OptimisticConcurrencyException` only if every retry loses the race.

```java
StateRepository<Cart> carts = new StateRepository<>(DaprFacade.getInstance(), "statestore", Cart.class);

// Unconditional read/write
Optional<Cart> existing = carts.find("cart:" + userId);
carts.save("cart:" + userId, cart);

// Optimistic-concurrency update: safe under concurrent writers
Cart updated = carts.update("cart:" + userId, current -> {
    Cart cart = current != null ? current : new Cart(userId);
    cart.addItem(product);
    return cart;
});

// ETag-guarded delete (false if the key never existed)
boolean deleted = carts.delete("cart:" + userId);
```

---

## 📢 Pub/Sub Messaging

Publish and subscribe to events.

### Publish Events

```java
// Publish event
OrderCreatedEvent event = new OrderCreatedEvent(orderId, customerId);
dapr.publishEvent("pubsub", "orders", event);

// Publish with metadata
Map<String, String> metadata = new HashMap<>();
metadata.put("correlation-id", correlationId);
dapr.publishEvent("pubsub", "orders", event, metadata);
```

### Subscribe to Events

Just annotate a method with `@DaprSubscribe` (or `@DaprTopic`) on any Spring bean - no
`@RestController`/`@PostMapping` needed. `DaprSubscriptionRegistrar` discovers it and
`DaprSubscriptionController` exposes `GET /dapr/subscribe` and dispatches incoming CloudEvents
to it automatically (see [@DaprSubscribe / @DaprTopic](#daprsubscribe--daprtopic) above).

```java
@Component
public class EventSubscriber {

    @DaprSubscribe(pubsubName = "pubsub", topic = "orders")
    public void handleOrder(OrderCreatedEvent event) {
        log.info("Received order: {}", event);
        // Process event; a thrown exception maps to a RETRY dispatch result
    }
}
```

### Complete Example

```java
@Service
public class OrderService {
    
    private final AdharDaprClient dapr = new AdharDaprClient();
    private static final String PUBSUB = "pubsub";
    
    public Order createOrder(OrderRequest request) {
        // Save order
        Order order = new Order(request);
        dapr.saveState("statestore", "order:" + order.getId(), order);
        
        // Publish event
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getTotal()
        );
        dapr.publishEvent(PUBSUB, "order-created", event);
        
        return order;
    }
}

@Component
public class NotificationService {

    @DaprSubscribe(pubsubName = "pubsub", topic = "order-created")
    public void sendOrderNotification(OrderCreatedEvent event) {
        // Send email notification
        emailService.sendOrderConfirmation(event);

        // Send SMS
        smsService.sendOrderSMS(event);
    }
}
```

---

## 🔗 Service Invocation

Call other services using service discovery.

### Invoke Service

```java
// GET request
Order order = dapr.get(
    "order-service",          // App ID
    "/orders/123",            // Endpoint
    Order.class               // Response type
);

// POST request
OrderResponse response = dapr.post(
    "order-service",
    "/orders",
    orderRequest,
    OrderResponse.class
);

// Custom method
User user = dapr.invokeService(
    "user-service",
    "PUT",
    "/users/123",
    updateRequest,
    User.class
);
```

### Complete Example

```java
@Service
public class CheckoutService {
    
    private final AdharDaprClient dapr = new AdharDaprClient();
    
    public CheckoutResult checkout(CheckoutRequest request) {
        // 1. Validate inventory
        InventoryResponse inventory = dapr.post(
            "inventory-service",
            "/validate",
            request.getItems(),
            InventoryResponse.class
        );
        
        if (!inventory.isAvailable()) {
            throw new OutOfStockException();
        }
        
        // 2. Process payment
        PaymentResponse payment = dapr.post(
            "payment-service",
            "/charge",
            new PaymentRequest(request.getTotal()),
            PaymentResponse.class
        );
        
        // 3. Create order
        Order order = dapr.post(
            "order-service",
            "/orders",
            new OrderRequest(request, payment.getTransactionId()),
            Order.class
        );
        
        return new CheckoutResult(order.getId(), payment.getTransactionId());
    }
}
```

### Resilient Invocation (retry, timeout, circuit breaker, fallback)

Raw `invokeService`/`invokeMethod` calls rethrow whatever exception the sidecar call raised,
with no retry or timeout. `resilience.DaprInvocationResilience` wraps invocation with:

- Linear-backoff retry (`maxAttempts`, `retryBackoff`)
- A per-attempt timeout, enforced on a shared daemon executor (`timeout`)
- A simple consecutive-failure circuit breaker (`failureThreshold`, `openStateDuration`) that
  fails fast without invoking the call while open, then allows a single half-open trial call
- An optional `Function<Throwable, R>` fallback instead of throwing

No Resilience4j (or other external resilience library) dependency is required - it's a small,
self-contained helper built on `java.util.concurrent`.

```java
// Via DaprFacade (uses its own internal invokeService under the hood):
DaprFacade dapr = DaprFacade.getInstance();

InventoryResponse response = dapr.invokeServiceResilient(
    "inventory-service", "/validate", "POST", request, InventoryResponse.class,
    cause -> InventoryResponse.unavailable()); // fallback

// Or directly wrapping AdharDaprClient with custom settings:
ResilienceSettings settings = new ResilienceSettings(
    3, Duration.ofMillis(100), Duration.ofSeconds(2), 5, Duration.ofSeconds(30));
DaprInvocationResilience resilience = new DaprInvocationResilience(settings);

InventoryResponse response = resilience.invokeService(
    adharDaprClient, "inventory-service", "POST", "/validate", request, InventoryResponse.class);
```

---

## 🔐 Secrets Management

Retrieve secrets from secret stores.

### Get Secret

```java
// Get single secret
Optional<String> apiKey = dapr.getSecret("secretstore", "api-key");

// Use secret
apiKey.ifPresent(key -> apiClient.setApiKey(key));

// Get all secrets
Map<String, String> secrets = dapr.getAllSecrets("secretstore", "database");
String username = secrets.get("username");
String password = secrets.get("password");
```

### Complete Example

```java
@Configuration
public class DatabaseConfig {
    
    private final AdharDaprClient dapr = new AdharDaprClient();
    
    @Bean
    public DataSource dataSource() {
        Map<String, String> dbSecrets = dapr.getAllSecrets("secretstore", "database");
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbSecrets.get("url"));
        config.setUsername(dbSecrets.get("username"));
        config.setPassword(dbSecrets.get("password"));
        
        return new HikariDataSource(config);
    }
}
```

---

## 🔌 Bindings

Trigger external systems and resources.

### Invoke Binding

```java
// Send email via binding
EmailRequest emailRequest = new EmailRequest(
    "user@example.com",
    "Order Confirmation",
    "Your order has been confirmed"
);

dapr.invokeBinding("email-binding", "create", emailRequest);

// Send SMS
SmsRequest smsRequest = new SmsRequest("+1234567890", "Order confirmed!");
dapr.invokeBinding("sms-binding", "create", smsRequest);

// Write to S3
FileData fileData = new FileData("report.pdf", pdfBytes);
dapr.invokeBinding("s3-binding", "create", fileData);
```

### Complete Example

```java
@Service
public class NotificationService {
    
    private final AdharDaprClient dapr = new AdharDaprClient();
    
    public void sendOrderConfirmation(Order order) {
        // Send email
        EmailRequest email = new EmailRequest(
            order.getCustomerEmail(),
            "Order Confirmation - " + order.getId(),
            generateEmailBody(order)
        );
        dapr.invokeBinding("email-binding", "create", email);
        
        // Send SMS
        if (order.getCustomerPhone() != null) {
            SmsRequest sms = new SmsRequest(
                order.getCustomerPhone(),
                "Order " + order.getId() + " confirmed!"
            );
            dapr.invokeBinding("sms-binding", "create", sms);
        }
        
        // Save to blob storage
        OrderReport report = generateReport(order);
        dapr.invokeBinding("blob-binding", "create", report);
    }
}
```

---

## 🔒 Distributed Lock

> ⛔ **Planned, not implemented.** `tryLock`/`unlock` (on both `DaprFacade` and
> `AdharDaprClient`) and `@DaprLock` throw `UnsupportedOperationException`. The pinned Dapr Java
> SDK (`1.18.0`) does not expose a distributed lock API on `DaprClient`; this needs an SDK
> upgrade to implement for real. The examples below describe the intended API only.

Synchronize access to shared resources across multiple instances.

### Acquire Lock

```java
AdharDaprClient dapr = new AdharDaprClient();

// Acquire lock
boolean acquired = dapr.tryLock(
    "lockstore",
    "order:123",        // resource ID
    "instance-1",       // lock owner
    30                  // expiry in seconds
);

if (acquired) {
    try {
        // Process order exclusively
        processOrder("123");
    } finally {
        // Release lock
        dapr.unlock("lockstore", "order:123", "instance-1");
    }
}
```

### With Annotation

```java
@Service
public class PaymentService {
    
    @DaprLock(
        storeName = "lockstore",
        resourceId = "#paymentId",
        lockOwner = "payment-service",
        expiryInSeconds = 60
    )
    public Payment processPayment(String paymentId, PaymentRequest request) {
        // Only one instance processes this payment
        return paymentGateway.charge(request);
    }
}
```

### Complete Example

```java
@Service
public class InventoryService {
    
    private final AdharDaprClient dapr = new AdharDaprClient();
    
    public boolean reserveInventory(String productId, int quantity) {
        String lockOwner = "inventory-service-" + UUID.randomUUID();
        
        // Try to acquire lock
        if (!dapr.tryLock("lockstore", "product:" + productId, lockOwner, 30)) {
            log.warn("Could not acquire lock for product: {}", productId);
            return false;
        }
        
        try {
            // Get current inventory
            Optional<Inventory> inv = dapr.getState("statestore", "inventory:" + productId, Inventory.class);
            
            if (inv.isPresent() && inv.get().getQuantity() >= quantity) {
                // Update inventory
                inv.get().setQuantity(inv.get().getQuantity() - quantity);
                dapr.saveState("statestore", "inventory:" + productId, inv.get());
                return true;
            }
            return false;
            
        } finally {
            // Always release lock
            dapr.unlock("lockstore", "product:" + productId, lockOwner);
        }
    }
}
```

---

## ⚙️ Configuration API

Manage application configuration dynamically.

### Get Configuration

```java
AdharDaprClient dapr = new AdharDaprClient();

// Get single configuration
Optional<String> connectionString = dapr.getConfiguration(
    "configstore",
    "database.connection-string"
);

// Get multiple configurations
Map<String, String> configs = dapr.getConfigurations(
    "configstore",
    "database.url",
    "database.username",
    "cache.ttl"
);
```

### With Annotation

```java
@Service
public class FeatureService {
    
    @DaprConfiguration(
        storeName = "configstore",
        key = "features.new-checkout.enabled",
        defaultValue = "false"
    )
    public boolean isNewCheckoutEnabled() {
        return false; // Will be replaced with config value
    }
}
```

### Configuration with Subscription

```java
@Service
public class ConfigService {
    
    private final AdharDaprClient dapr = new AdharDaprClient();
    private Map<String, String> cache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void subscribeToConfigs() {
        // Subscribe to configuration changes
        Map<String, String> configs = dapr.getConfigurations(
            "configstore",
            "database.url",
            "cache.ttl",
            "feature.flags"
        );
        
        cache.putAll(configs);
        log.info("Loaded {} configurations", configs.size());
    }
    
    public String getConfig(String key) {
        return cache.getOrDefault(key, "");
    }
}
```

---

## 🔐 Cryptography

> ⛔ **Planned, not implemented.** `AdharDaprClient#encrypt`/`decrypt` throw
> `UnsupportedOperationException`; the cryptography API is not available on the pinned Dapr
> Java SDK (`1.18.0`). The examples below describe the intended API only.

Encrypt and decrypt data using Dapr cryptography.

### Encrypt Data

```java
AdharDaprClient dapr = new AdharDaprClient();

// Encrypt sensitive data
String plaintext = "sensitive-data";
byte[] encrypted = dapr.encrypt(
    "crypto",
    plaintext.getBytes(),
    "AES256-GCM"
);

// Save encrypted data
dapr.saveState("statestore", "encrypted-data", encrypted);
```

### Decrypt Data

```java
// Get encrypted data
Optional<byte[]> encryptedOpt = dapr.getState("statestore", "encrypted-data", byte[].class);

if (encryptedOpt.isPresent()) {
    // Decrypt data
    byte[] decrypted = dapr.decrypt(
        "crypto",
        encryptedOpt.get(),
        "AES256-GCM"
    );
    
    String plaintext = new String(decrypted);
}
```

### Complete Example

```java
@Service
public class SecureDataService {
    
    private final AdharDaprClient dapr = new AdharDaprClient();
    private static final String CRYPTO_COMPONENT = "crypto";
    private static final String ALGORITHM = "AES256-GCM";
    
    public void saveSecureData(String userId, UserData data) {
        // Encrypt sensitive data
        String json = objectMapper.writeValueAsString(data);
        byte[] encrypted = dapr.encrypt(CRYPTO_COMPONENT, json.getBytes(), ALGORITHM);
        
        // Save encrypted data
        dapr.saveState("statestore", "user-data:" + userId, encrypted);
        
        log.info("Saved encrypted data for user: {}", userId);
    }
    
    public UserData getSecureData(String userId) {
        // Get encrypted data
        Optional<byte[]> encrypted = dapr.getState(
            "statestore",
            "user-data:" + userId,
            byte[].class
        );
        
        if (encrypted.isEmpty()) {
            return null;
        }
        
        // Decrypt data
        byte[] decrypted = dapr.decrypt(CRYPTO_COMPONENT, encrypted.get(), ALGORITHM);
        String json = new String(decrypted);
        
        return objectMapper.readValue(json, UserData.class);
    }
}
```

---

## 🏗️ Complete Application Example

```java
@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

@RestController
@RequestMapping("/orders")
public class OrderController {
    
    private final OrderService orderService;
    
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody OrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.ok(order);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        return orderService.getOrder(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}

@Service
public class OrderService {
    
    private final AdharDaprClient dapr = new AdharDaprClient();
    
    public Order createOrder(OrderRequest request) {
        // 1. Validate with inventory service
        InventoryResponse inventory = dapr.post(
            "inventory-service",
            "/validate",
            request.getItems(),
            InventoryResponse.class
        );
        
        if (!inventory.isAvailable()) {
            throw new BusinessException("Items out of stock");
        }
        
        // 2. Process payment
        PaymentResponse payment = dapr.post(
            "payment-service",
            "/charge",
            new PaymentRequest(request.getTotal()),
            PaymentResponse.class
        );
        
        // 3. Create order
        Order order = new Order(request, payment.getTransactionId());
        
        // 4. Save to state store
        dapr.saveState("statestore", "order:" + order.getId(), order);
        
        // 5. Publish event
        dapr.publishEvent("pubsub", "order-created", 
            new OrderCreatedEvent(order));
        
        // 6. Send notifications
        dapr.invokeBinding("email-binding", "create", 
            new EmailRequest(order.getCustomerEmail(), "Order Confirmed", "..."));
        
        return order;
    }
    
    public Optional<Order> getOrder(String orderId) {
        return dapr.getState("statestore", "order:" + orderId, Order.class);
    }
}

@RestController
public class OrderEventHandler {
    
    @Topic(name = "order-created", pubsubName = "pubsub")
    @PostMapping("/order-events")
    public ResponseEntity<Void> handleOrderCreated(@RequestBody OrderCreatedEvent event) {
        log.info("Order created: {}", event.getOrderId());
        
        // Update analytics
        analyticsService.recordOrder(event);
        
        // Send to warehouse
        warehouseService.prepareShipment(event);
        
        return ResponseEntity.ok().build();
    }
}
```

---

## 📊 Dapr Components Configuration

### State Store (Redis)

**statestore.yaml:**
```yaml
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: statestore
spec:
  type: state.redis
  version: v1
  metadata:
  - name: redisHost
    value: localhost:6379
  - name: redisPassword
    value: ""
```

### Pub/Sub (Kafka)

**pubsub.yaml:**
```yaml
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: pubsub
spec:
  type: pubsub.kafka
  version: v1
  metadata:
  - name: brokers
    value: localhost:9092
  - name: authRequired
    value: "false"
```

### Secret Store (Kubernetes)

**secretstore.yaml:**
```yaml
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: secretstore
spec:
  type: secretstores.kubernetes
  version: v1
  metadata: []
```

---

## 🎯 Best Practices

### 1. Use Try-With-Resources

```java
try (AdharDaprClient dapr = new AdharDaprClient()) {
    dapr.saveState("statestore", "key", value);
}
```

### 2. Handle Failures Gracefully

```java
Optional<User> user = dapr.getState("statestore", "user:123", User.class);

user.ifPresentOrElse(
    u -> log.info("Found: {}", u),
    () -> log.warn("User not found")
);
```

### 3. Use Meaningful State Keys

```java
// ✅ Good - Clear hierarchy
dapr.saveState("statestore", "user:123", user);
dapr.saveState("statestore", "order:456", order);
dapr.saveState("statestore", "cart:user:123", cart);

// ❌ Bad - No structure
dapr.saveState("statestore", "123", user);
```

### 4. Publish Events for Side Effects

```java
// Create order (main operation)
Order order = createOrder(request);

// Publish event for side effects (email, analytics, warehouse)
dapr.publishEvent("pubsub", "order-created", new OrderCreatedEvent(order));
```

---

## 📚 Resources

- [Dapr Documentation](https://docs.dapr.io/)
- [Dapr Building Blocks](https://docs.dapr.io/concepts/building-blocks-concept/)
- [Dapr Java SDK](https://github.com/dapr/java-sdk)

---

**Built with ❤️ by Adhar Platform Team**

