# 🧩 Adhar Kit Commons - Enterprise Foundation Library

**DDD annotations, base classes, and enterprise utilities for microservices**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.x-blue.svg)](https://micronaut.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 0.1.0-SNAPSHOT  
**Status:** ✅ Production Ready

---

## 📖 Overview

The **adhar-kit-commons** module provides foundational components for building enterprise microservices with:

- 🏛️ **DDD Annotations** - Domain-Driven Design support
- 🎯 **Base Classes** - Entities, DTOs, Repositories
- 🛠️ **Utilities** - Collections, DateTime, Validation, JSON
- 📝 **Common Models** - API responses, errors, pagination
- 🔧 **Enterprise Patterns** - Idempotency, versioning, events
- ☁️ **CloudEvents Support** - Standard event format and base classes
- 🚨 **Global Exception Handling** - `SpringGlobalExceptionHandler` (@RestControllerAdvice) mapping the exception hierarchy (via `getHttpStatus()`) plus `@Valid` / constraint violations to `ErrorResponse` bodies
- 🔁 **Idempotency Runtime** - `IdempotencyAspect` + TTL-aware `InMemoryIdempotencyStore` behind `@Idempotent` (SpEL / `{index}` keys, cached replay, `DuplicateRequestException` for in-flight duplicates)
- 🧵 **Tenant & Correlation Context** - `TenantContext` / `CorrelationContext` ThreadLocals with servlet filters populating SLF4J MDC from `X-Tenant-ID` / `X-Correlation-ID` / `X-Request-ID` and echoing ids on responses
- 🏷️ **API Versioning Runtime** - `ApiVersionInterceptor` emitting `Deprecation` / `Sunset` headers for `@ApiVersion` handlers, with optional `X-API-Version` request validation
- ✅ **`@NotNullOrEmpty` Validator** - Jakarta `ConstraintValidator` for strings, collections, maps, arrays and optionals
- ⚙️ **Spring Boot Auto-Configuration** - all runtime beans registered conditionally, overridable via `@ConditionalOnMissingBean`, toggleable under `adhar.commons.*`

### Why Commons?

Every microservice needs common patterns:

✅ **Standardization** - Consistent patterns across services  
✅ **DRY Principle** - Don't repeat yourself  
✅ **Best Practices** - Enterprise patterns built-in  
✅ **Type Safety** - Generics and strong typing  
✅ **Production Ready** - Battle-tested components  
✅ **CloudEvents Compliant** - Standard event format across platform  

---

## 🚀 Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-commons</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Use Base Classes

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity<String> {
    
    @Id
    private String userId;
    
    private String username;
    private String email;
    
    @Override
    public String getId() {
        return userId;
    }
}
```

### 3. Use Utilities

```java
// Validation
ValidationUtils.requireNonNull(user, "User required");
ValidationUtils.requireValidEmail(email, "Invalid email");

// Collections
List<String> names = CollectionUtils.map(users, User::getName);
boolean hasActive = CollectionUtils.anyMatch(users, User::isActive);

// DateTime
String formatted = DateTimeUtils.formatDateTime(LocalDateTime.now());
long days = DateTimeUtils.daysBetween(start, end);
```

---

## 🎯 Core Features

### 1. CloudEvents Support (CNCF Standard)

**CloudEvents** is a CNCF specification for describing event data in a common way across platforms.

#### CloudEvent Structure

```java
CloudEvent<Order> event = CloudEvent.<Order>builder()
    .id(UUID.randomUUID().toString())
    .source(URI.create("https://api.example.com/orders"))
    .type("com.example.order.created")
    .subject("orders/12345")
    .dataContentType("application/json")
    .time(OffsetDateTime.now())
    .data(order)
    .build();
```

**Required Attributes:**
- `id` - Unique event identifier
- `source` - Event producer URI
- `specversion` - CloudEvents version (1.0)
- `type` - Event type (reverse-DNS format)

**Optional Attributes:**
- `datacontenttype` - Content type (default: application/json)
- `dataschema` - Schema URI
- `subject` - Event subject
- `time` - Event timestamp
- `data` - Event payload

#### Domain Events

```java
public class OrderPlacedEvent extends DomainEvent {
    private String orderId;
    private String customerId;
    private BigDecimal totalAmount;
    
    public OrderPlacedEvent(String orderId, String customerId, BigDecimal totalAmount) {
        super();  // Auto-generates eventId, timestamp
        this.orderId = orderId;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
    }
    
    @Override
    public String getEventType() {
        return "com.example.order.placed";
    }
    
    @Override
    public String getAggregateId() {
        return orderId;
    }
}

// Usage
OrderPlacedEvent event = new OrderPlacedEvent(orderId, customerId, amount);
event.setCorrelationId(correlationId);
event.setTriggeredBy("john.doe");

// Convert to CloudEvent
CloudEvent<DomainEvent> cloudEvent = event.toCloudEvent(
    URI.create("https://orders.example.com")
);
```

#### Kafka Integration

```java
// Create CloudEvent
CloudEvent<Order> cloudEvent = CloudEvent.create(
    "com.example.order.created",
    URI.create("https://orders.example.com"),
    order
);

// Wrap for Kafka
KafkaCloudEvent<Order> kafkaEvent = new KafkaCloudEvent<>(
    cloudEvent,
    "order-events",           // Topic
    order.getCustomerId()     // Partition key
);

kafkaEvent.addHeader("tenant-id", tenantId);
kafkaEvent.addHeader("correlation-id", correlationId);

// Publish to Kafka
kafkaTemplate.send(
    kafkaEvent.getTopic(),
    kafkaEvent.getPartitionKey(),
    kafkaEvent.getEvent()
);
```

#### Event Publishing Interface

```java
// Framework-agnostic publisher interface
public interface CloudEventPublisher {
    <T> void publish(CloudEvent<T> event);
    <T> void publishAsync(CloudEvent<T> event);
    <T> void publish(String topic, CloudEvent<T> event);
    void publishDomainEvent(DomainEvent domainEvent, URI source);
}

// Each framework provides implementation:
// - Spring: ApplicationEventPublisher
// - Quarkus: CDI Events
// - Micronaut: ApplicationEventPublisher
// - Kafka: KafkaTemplate
```

---

### 2. DDD Annotations

#### @AggregateRoot

```java
@AggregateRoot(boundedContext = "order-management")
@Entity
public class Order {
    @Id
    private String orderId;
    
    @OneToMany(cascade = CascadeType.ALL)
    private List<OrderItem> items;
    
    // Only Order can modify OrderItems
    public void addItem(OrderItem item) {
        this.items.add(item);
        // Business logic here
    }
}
```

#### @ValueObject

```java
@ValueObject
@Value
public class Money {
    private final BigDecimal amount;
    private final Currency currency;
    
    public Money add(Money other) {
        ValidationUtils.requireEquals(
            this.currency, 
            other.currency, 
            "Currency mismatch"
        );
        return new Money(
            this.amount.add(other.amount), 
            this.currency
        );
    }
}
```

#### @DomainEntity

```java
@DomainEntity
@Entity
public class Customer {
    @Id
    private String customerId;
    
    private String name;
    private Email email;
    
    public void updateEmail(Email newEmail) {
        this.email = newEmail;
        // Raise domain event
    }
}
```

#### @DomainService

```java
@DomainService
@Service
public class TransferMoneyService {
    
    public void transfer(Account from, Account to, Money amount) {
        ValidationUtils.requirePositive(
            amount.getAmount(), 
            "Amount must be positive"
        );
        
        from.withdraw(amount);
        to.deposit(amount);
    }
}
```

### 2. Event-Driven Annotations

#### @PublishEvent (CloudEvents)

```java
@Service
public class OrderService {
    
    @PublishEvent(
        eventType = "com.example.order.placed",
        source = "https://api.example.com/orders",
        topic = "order-events",
        async = true
    )
    public Order placeOrder(OrderRequest request) {
        Order order = createOrder(request);
        // CloudEvent published automatically
        return order;
    }
}
```

### 3. Enterprise Annotations

#### @Idempotent

```java
@Service
public class PaymentService {
    
    @Idempotent(
        key = "#paymentId",
        ttl = 300,
        storage = "redis"
    )
    public PaymentResult processPayment(String paymentId, Money amount) {
        // Safe to retry - processes once
        return paymentGateway.charge(amount);
    }
}
```

#### @ApiVersion

```java
@RestController
@RequestMapping("/api")
public class UserController {
    
    @ApiVersion(
        version = "1.0",
        deprecated = true,
        deprecationMessage = "Use v2.0",
        sunsetDate = "2026-12-31"
    )
    @GetMapping("/users/{id}")
    public UserV1 getUserV1(@PathVariable String id) {
        return userService.findById(id);
    }
    
    @ApiVersion(version = "2.0")
    @GetMapping("/v2/users/{id}")
    public UserV2 getUserV2(@PathVariable String id) {
        return userService.findByIdV2(id);
    }
}
```

#### @NotNullOrEmpty

```java
public class CreateUserRequest {
    
    @NotNullOrEmpty(message = "Username is required")
    private String username;
    
    @NotNullOrEmpty(message = "Email is required")
    private String email;
    
    @NotNullOrEmpty(message = "At least one role required")
    private List<String> roles;
}
```

---

## ☁️ CloudEvents Support

The commons module provides base classes and annotations for CloudEvents 1.0 specification compliance across all Adhar modules.

### Base CloudEvent Class

**All domain events extend CloudEvent:**

```java
public class OrderCreatedEvent extends CloudEvent {
    private final String orderId;
    private final String customerId;
    private final BigDecimal amount;
    
    public OrderCreatedEvent(String orderId, String customerId, BigDecimal amount) {
        super(
            URI.create("https://adhar.example.com/orders"),
            "com.adhar.order.created"
        );
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
    }
    
    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
}
```

**With subject:**
```java
public class OrderCreatedEvent extends CloudEvent {
    public OrderCreatedEvent(Order order) {
        super(
            URI.create("https://adhar.example.com/orders"),
            "com.adhar.order.created",
            "order-" + order.getId() // Subject
        );
    }
}
```

**With extensions:**
```java
public class OrderCreatedEvent extends CloudEvent {
    public OrderCreatedEvent(Order order) {
        super(
            URI.create("https://adhar.example.com/orders"),
            "com.adhar.order.created"
        );
        addExtension("priority", "high");
        addExtension("region", order.getRegion());
        addExtension("customerId", order.getCustomerId());
    }
}
```

### CloudEvent Attributes

Every CloudEvent automatically includes:

- **id** - Unique identifier (UUID)
- **source** - URI identifying event source
- **specversion** - CloudEvents version (1.0)
- **type** - Event type
- **time** - Timestamp (auto-generated)
- **subject** - Optional subject
- **datacontenttype** - Content type
- **dataschema** - Optional schema URI
- **extensions** - Custom metadata

### @EventPublisher Annotation

```java
@EventPublisher(
    eventType = "com.adhar.order.created",
    source = "https://adhar.example.com/orders"
)
@Service
public class OrderService {
    public OrderCreatedEvent createOrder(OrderRequest request) {
        return new OrderCreatedEvent(order);
    }
}
```

### @EventHandler Annotation

```java
@Service
public class OrderEventHandler {
    
    @EventHandler(
        eventType = "com.adhar.order.created",
        source = "https://adhar.example.com/orders"
    )
    public void handleOrderCreated(OrderCreatedEvent event) {
        processOrder(event);
    }
}
```

---

## 🏗️ Base Classes

### BaseEntity

```java
@Entity
@Table(name = "products")
public class Product extends BaseEntity<String> {
    
    @Id
    private String productId;
    
    private String name;
    private BigDecimal price;
    
    @Override
    public String getId() {
        return productId;
    }
}

// Usage
Product product = new Product();
product.prePersist("admin");  // Sets audit fields
product.preUpdate("admin");   // Updates audit fields
boolean isNew = product.isNew();  // Checks if persisted
```

**Provides:**
- `createdBy` - Who created
- `createdAt` - Creation timestamp
- `updatedBy` - Last updater
- `updatedAt` - Update timestamp
- `version` - Optimistic locking

### BaseDTO

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class UserDTO extends BaseDTO {
    
    private String userId;
    private String username;
    private String email;
    
    @Override
    public void validate() {
        ValidationUtils.requireNonEmpty(username, "Username required");
        ValidationUtils.requireValidEmail(email, "Invalid email");
    }
}

// Usage
UserDTO dto = new UserDTO();
dto.validate();  // Throws if invalid
boolean valid = dto.isValid();  // Returns false if invalid
```

### BaseRepository

```java
@Repository
public interface UserRepository extends BaseRepository<User, String> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    List<User> findByRole(String role);
}

// Provides:
// - save(entity)
// - findById(id)
// - findAll()
// - deleteById(id)
// - existsById(id)
// - count()
```

---

## 🛠️ Utility Classes

### CollectionUtils

```java
// Null-safe operations
boolean empty = CollectionUtils.isEmpty(list);
List<String> safe = CollectionUtils.nullSafe(list);

// Transformations
List<String> names = CollectionUtils.map(users, User::getName);
Map<String, User> userMap = CollectionUtils.toMap(users, User::getId);

// Filtering
List<User> active = CollectionUtils.filter(users, User::isActive);
Optional<User> found = CollectionUtils.findFirst(users, u -> u.getId().equals("123"));

// Grouping & Partitioning
Map<String, List<User>> byRole = CollectionUtils.groupBy(users, User::getRole);
Map<Boolean, List<User>> partitioned = CollectionUtils.partition(users, User::isActive);

// Checks
boolean any = CollectionUtils.anyMatch(users, User::isAdmin);
boolean all = CollectionUtils.allMatch(users, User::isActive);

// Utilities
String first = CollectionUtils.firstOrDefault(names, "default");
String last = CollectionUtils.lastOrDefault(names, "default");
List<String> list = CollectionUtils.listOf("a", "b", "c");
Set<String> set = CollectionUtils.setOf("a", "b", "c");
```

### DateTimeUtils

```java
// Current time
LocalDateTime now = DateTimeUtils.now();
LocalDateTime nowUtc = DateTimeUtils.nowUtc();
LocalDate today = DateTimeUtils.today();
long currentMillis = DateTimeUtils.currentTimeMillis();

// Formatting
String formatted = DateTimeUtils.formatDateTime(now);
String dateStr = DateTimeUtils.formatDate(LocalDate.now());
String custom = DateTimeUtils.format(now, "dd/MM/yyyy HH:mm");

// Parsing
LocalDateTime parsed = DateTimeUtils.parseDateTime("2025-11-02T10:30:00");
LocalDate date = DateTimeUtils.parseDate("2025-11-02");

// Conversions
Date legacyDate = DateTimeUtils.toDate(now);
LocalDateTime ldt = DateTimeUtils.fromDate(legacyDate);
long epochMillis = DateTimeUtils.toEpochMillis(now);
LocalDateTime fromEpoch = DateTimeUtils.fromEpochMillis(epochMillis);

// Calculations
LocalDateTime future = DateTimeUtils.addDays(now, 7);
LocalDateTime past = DateTimeUtils.addHours(now, -24);
long days = DateTimeUtils.daysBetween(start, end);
long hours = DateTimeUtils.hoursBetween(start, end);

// Comparisons
boolean isPast = DateTimeUtils.isPast(someDate);
boolean isFuture = DateTimeUtils.isFuture(someDate);
boolean isToday = DateTimeUtils.isToday(someLocalDate);
boolean sameDay = DateTimeUtils.isSameDay(date1, date2);
```

### ValidationUtils

```java
// Null checks
User user = ValidationUtils.requireNonNull(user, "User required");
ValidationUtils.requireTrue(isValid, "Invalid state");
ValidationUtils.requireFalse(isDuplicate, "Duplicate entry");

// String validation
String username = ValidationUtils.requireNonEmpty(username, "Username required");
boolean empty = ValidationUtils.isEmpty(str);
boolean blank = ValidationUtils.isBlank(str);

// Collection validation
List<String> items = ValidationUtils.requireNonEmpty(items, "Items required");
boolean isEmpty = ValidationUtils.isEmpty(collection);

// Numeric validation
BigDecimal amount = ValidationUtils.requirePositive(amount, "Amount must be positive");
Integer age = ValidationUtils.requireNonNegative(age, "Age cannot be negative");
Double rate = ValidationUtils.requireInRange(rate, 0.0, 100.0, "Rate must be 0-100");

// Format validation
boolean validEmail = ValidationUtils.isValidEmail("user@example.com");
String email = ValidationUtils.requireValidEmail(email, "Invalid email");
boolean validPhone = ValidationUtils.isValidPhone("+1234567890");
String phone = ValidationUtils.requireValidPhone(phone, "Invalid phone");
boolean validUuid = ValidationUtils.isValidUUID(uuid);

// Length validation
String password = ValidationUtils.requireMinLength(password, 8, "Password too short");
String code = ValidationUtils.requireMaxLength(code, 6, "Code too long");
String pin = ValidationUtils.requireLength(pin, 4, 6, "PIN must be 4-6 digits");

// Pattern validation
boolean matches = ValidationUtils.matches(str, pattern);
String validated = ValidationUtils.requireMatches(str, pattern, "Invalid format");

// Equality checks
ValidationUtils.requireEquals(obj1, obj2, "Objects must match");
ValidationUtils.requireNotEquals(obj1, obj2, "Objects must differ");
```

---

## 📋 Complete Annotation Matrix

| Annotation | Category | Use Case |
|------------|----------|----------|
| `@AggregateRoot` | DDD | Mark aggregate root entities |
| `@ValueObject` | DDD | Mark immutable value objects |
| `@DomainEntity` | DDD | Mark domain entities |
| `@DomainService` | DDD | Mark domain services |
| `@PublishEvent` | Events | Auto-publish domain events |
| `@Idempotent` | Enterprise | Idempotent operations |
| `@ApiVersion` | Versioning | API version management |
| `@NotNullOrEmpty` | Validation | Validate non-empty fields |

**Total:** 8 DDD & Enterprise Annotations

---

## 💡 Usage Patterns

### Pattern 1: DDD Entity with Audit

```java
@AggregateRoot(boundedContext = "inventory")
@Entity
@Table(name = "products")
public class Product extends BaseEntity<String> {
    
    @Id
    private String productId;
    
    private String name;
    private Money price;
    private int stockQuantity;
    
    @Override
    public String getId() {
        return productId;
    }
    
    public void reduceStock(int quantity) {
        ValidationUtils.requirePositive(quantity, "Quantity must be positive");
        ValidationUtils.requireTrue(
            this.stockQuantity >= quantity,
            "Insufficient stock"
        );
        
        this.stockQuantity -= quantity;
        this.preUpdate(SecurityContextHolder.getCurrentUser());
    }
}
```

### Pattern 2: Value Object with Validation

```java
@ValueObject
@Value
public class Email {
    
    private final String value;
    
    public Email(String value) {
        this.value = ValidationUtils.requireValidEmail(
            value, 
            "Invalid email format"
        );
    }
    
    public String getDomain() {
        return value.substring(value.indexOf('@') + 1);
    }
}
```

### Pattern 3: Service with Events

```java
@DomainService
@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    
    @Idempotent(key = "#request.orderId", ttl = 300)
    @PublishEvent(eventType = "OrderCreated", async = true)
    public Order createOrder(CreateOrderRequest request) {
        request.validate();
        
        Order order = new Order();
        order.prePersist(getCurrentUser());
        
        return orderRepository.save(order);
    }
}
```

### Pattern 4: Versioned API

```java
@RestController
@RequestMapping("/api")
public class ProductController {
    
    @ApiVersion(version = "1.0", deprecated = true)
    @GetMapping("/products/{id}")
    public ProductV1 getProductV1(@PathVariable String id) {
        return productService.findByIdV1(id);
    }
    
    @ApiVersion(version = "2.0")
    @GetMapping("/v2/products/{id}")
    public ProductV2 getProductV2(@PathVariable String id) {
        return productService.findByIdV2(id);
    }
}
```

---

## 🏢 Enterprise Patterns

### Auditable Entities

```java
@Entity
public class AuditableEntity extends BaseEntity<String> {
    // Automatically provides:
    // - createdBy
    // - createdAt
    // - updatedBy
    // - updatedAt
    // - version
}
```

### Validated DTOs

```java
public class CreateUserRequest extends BaseDTO {
    
    @NotNullOrEmpty
    private String username;
    
    private String email;
    
    @Override
    public void validate() {
        ValidationUtils.requireNonEmpty(username, "Username required");
        ValidationUtils.requireValidEmail(email, "Invalid email");
    }
}
```

### Repository Pattern

```java
@Repository
public interface ProductRepository extends BaseRepository<Product, String> {
    // Standard CRUD provided
    // Add custom queries
    List<Product> findByCategory(String category);
}
```

---

## 📊 Best Practices

### 1. Use Base Classes

```java
// ✅ GOOD: Extend base classes
public class User extends BaseEntity<String> {
    // Audit fields provided
}

// ❌ BAD: Manual audit fields
public class User {
    private String createdBy;
    private LocalDateTime createdAt;
    // Lots of boilerplate
}
```

### 2. Use Utility Classes

```java
// ✅ GOOD: Use utilities
List<String> names = CollectionUtils.map(users, User::getName);

// ❌ BAD: Manual iteration
List<String> names = new ArrayList<>();
for (User user : users) {
    names.add(user.getName());
}
```

### 3. Validate Early

```java
// ✅ GOOD: Validate at entry
public void createUser(CreateUserRequest request) {
    request.validate();  // Fail fast
    // Process...
}

// ❌ BAD: No validation
public void createUser(CreateUserRequest request) {
    // May fail later
}
```

### 4. Use DDD Annotations

```java
// ✅ GOOD: Clear intent
@AggregateRoot
@Entity
public class Order {
    // Clear architectural boundary
}

// ❌ BAD: No context
@Entity
public class Order {
    // What kind of entity?
}
```

---

## 🎊 Summary

**The Adhar Kit Commons provides:**

### Annotations (8)
✅ **DDD Support** - 4 annotations (@AggregateRoot, @ValueObject, etc.)  
✅ **Events** - @PublishEvent  
✅ **Enterprise** - @Idempotent, @ApiVersion  
✅ **Validation** - @NotNullOrEmpty  

### Base Classes (3)
✅ **BaseEntity** - Auditable entities with lifecycle  
✅ **BaseDTO** - Validated data transfer objects  
✅ **BaseRepository** - Standard CRUD interface  

### Utilities (4)
✅ **CollectionUtils** - 25+ collection operations  
✅ **DateTimeUtils** - 30+ date/time operations  
✅ **ValidationUtils** - 40+ validation methods  
✅ **StringUtils** - String operations  

### Common Models
✅ **ApiResponse** - Standardized API responses  
✅ **ErrorResponse** - Error handling  
✅ **PageResponse** - Pagination support  

### Runtime Support (auto-configured)
✅ **SpringGlobalExceptionHandler** - HTTP status mapping for the exception hierarchy (400/404/409/422/500/502)  
✅ **Idempotency** - `IdempotencyStore` + `IdempotencyAspect` for `@Idempotent`  
✅ **Context Propagation** - `TenantContextFilter` + `CorrelationIdFilter` with MDC support  
✅ **API Versioning** - `ApiVersionInterceptor` for `@ApiVersion` (Deprecation/Sunset headers)  
✅ **Toggles** - `adhar.commons.{exception-handler,idempotency,correlation,tenant,api-versioning}.enabled`  

**Perfect for:**
- 🏢 Enterprise microservices
- 🎯 DDD implementations
- 📦 Multi-module projects
- 🔄 Event-driven systems
- 🌐 REST APIs
- 📊 Business applications

---

## 📄 License

Copyright © 2025 Adhar Platform Team

