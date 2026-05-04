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

- 🗄️ **State Management** - Distributed state store
- 📢 **Pub/Sub** - Event-driven messaging
- 🔗 **Service Invocation** - Service-to-service calls
- 🔌 **Bindings** - External system integration
- 🔐 **Secrets** - Secure secrets management
- 🔧 **Configuration** - Distributed configuration

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

Automatically manage state in Dapr state store.

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

Automatically publish events to Dapr pub/sub.

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

### @DaprSubscribe

Subscribe to Dapr pub/sub topics.

```java
@RestController
public class OrderEventHandler {
    
    @DaprSubscribe(pubsubName = "pubsub", topic = "order-created")
    @PostMapping("/orders/events")
    public ResponseEntity<Void> handleOrderCreated(OrderCreatedEvent event) {
        log.info("Order created: {}", event.getOrderId());
        // Process event
        return ResponseEntity.ok().build();
    }
    
    @DaprSubscribe(pubsubName = "pubsub", topic = "order-updated", deadLetterTopic = "order-errors")
    @PostMapping("/orders/updates")
    public ResponseEntity<Void> handleOrderUpdated(OrderUpdatedEvent event) {
        // Process event or send to dead letter if fails
        return ResponseEntity.ok().build();
    }
}
```

### @DaprInvoke

Invoke other services via Dapr service invocation.

```java
@Service
public class CheckoutService {
    
    @DaprInvoke(appId = "inventory-service", method = "POST", endpoint = "/validate")
    public InventoryResponse validateInventory(InventoryRequest request) {
        // Actual invocation handled automatically
        return null;
    }
    
    @DaprInvoke(appId = "payment-service", method = "POST", endpoint = "/charge")
    public PaymentResponse processPayment(PaymentRequest request) {
        return null;
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

Use Spring annotations (if using Spring):

```java
@RestController
public class OrderEventHandler {
    
    @Topic(name = "orders", pubsubName = "pubsub")
    @PostMapping("/orders-created")
    public ResponseEntity<Void> handleOrderCreated(
        @RequestBody OrderCreatedEvent event
    ) {
        log.info("Order created: {}", event.getOrderId());
        // Process event
        return ResponseEntity.ok().build();
    }
}
```

Or use Dapr SDK directly:

```java
@Component
public class EventSubscriber {
    
    @DaprSubscribe(pubsubName = "pubsub", topic = "orders")
    public void handleOrder(OrderCreatedEvent event) {
        log.info("Received order: {}", event);
        // Process event
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

@RestController
public class NotificationService {
    
    @Topic(name = "order-created", pubsubName = "pubsub")
    @PostMapping("/order-notifications")
    public ResponseEntity<Void> sendOrderNotification(
        @RequestBody OrderCreatedEvent event
    ) {
        // Send email notification
        emailService.sendOrderConfirmation(event);
        
        // Send SMS
        smsService.sendOrderSMS(event);
        
        return ResponseEntity.ok().build();
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

