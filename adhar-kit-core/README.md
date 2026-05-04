# 🛠️ Adhar Kit Core - Enterprise Core Utilities

**Core patterns and utilities for building robust microservices**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.x-blue.svg)](https://micronaut.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 0.1.0-SNAPSHOT  
**Status:** ✅ Production Ready

---

## 📖 Overview

The **adhar-kit-core** module provides fundamental patterns and utilities used across all microservices:

- 🎯 **Design Patterns** - Specification, Result, Observer, Lazy
- 🔄 **Retry Logic** - Exponential backoff, custom policies
- 🔀 **Converters** - Type conversions, transformations
- ⚡ **Async Utilities** - Non-blocking operations, parallel execution
- 💾 **Memoization** - Cache expensive computations
- 🛠️ **Core Utilities** - Lazy loading, type conversion
- ⚙️ **Configuration** - Centralized core configuration

---

## 🚀 Quick Start

### Add Dependency

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

---

## 🎯 Core Patterns

### 1. Specification Pattern

Build complex filtering logic in a composable way.

```java
// Define specifications
Specification<User> activeUsers = user -> user.isActive();
Specification<User> premiumUsers = user -> user.isPremium();
Specification<User> recentLogin = user -> 
    user.getLastLogin().isAfter(LocalDateTime.now().minusDays(7));

// Combine specifications
Specification<User> targetUsers = activeUsers
    .and(premiumUsers)
    .and(recentLogin);

// Filter collection
List<User> filtered = targetUsers.filter(allUsers);

// Or use with streams
List<User> result = users.stream()
    .filter(targetUsers)
    .collect(Collectors.toList());
```

**JPA Integration:**
```java
// In repository
Specification<User> spec = Specification
    .<User>where((root, query, cb) -> cb.equal(root.get("active"), true))
    .and((root, query, cb) -> cb.equal(root.get("role"), "ADMIN"))
    .and((root, query, cb) -> cb.greaterThan(root.get("loginCount"), 10));

List<User> users = userRepository.findAll(spec);
```

**Helper Methods:**
```java
// Find first match
Optional<User> firstActive = activeUsers.findFirst(users);

// Check if any match
boolean hasActive = activeUsers.anyMatch(users);

// Check if all match
boolean allActive = activeUsers.allMatch(users);
```

---

### 2. Result Pattern

Functional error handling without exceptions.

```java
// Service method returns Result
public Result<Order, OrderError> createOrder(OrderRequest request) {
    return validateRequest(request)
        .flatMap(this::checkInventory)
        .flatMap(this::processPayment)
        .flatMap(this::saveOrder)
        .map(order -> order.withStatus(OrderStatus.CONFIRMED));
}

// Handle result
Result<Order, OrderError> result = orderService.createOrder(request);

// Pattern matching
String message = result.match(
    order -> "Order created: " + order.getId(),
    error -> "Error: " + error.getMessage()
);

// Or use callbacks
result.ifSuccess(order -> log.info("Created: {}", order))
      .ifFailure(error -> log.error("Failed: {}", error));
```

**Chaining Operations:**
```java
Result<User, String> result = userRepository.findById(userId)
    .map(user -> user.withLastLogin(LocalDateTime.now()))
    .flatMap(userRepository::save)
    .mapError(error -> "Failed to update user: " + error);
```

**Multiple Results:**
```java
Result<User, String> user = getUser();
Result<Account, String> account = getAccount();
Result<Profile, String> profile = getProfile();

// Combine all
Result<List<Object>, String> all = Result.combine(user, account, profile);
```

**Convert to Optional:**
```java
Optional<User> userOpt = result.toOptional();
```

**Get or Throw:**
```java
User user = result.getOrThrow(error -> 
    new BusinessException("User not found: " + error)
);
```

---

### 3. Retry Utility

Handle transient failures with configurable retry logic.

**Simple Retry:**
```java
// Retry 3 times with 1 second delay
User user = RetryUtil.execute(
    () -> externalService.getUser(userId),
    3,      // max retries
    1000    // delay ms
);
```

**Exponential Backoff:**
```java
// Retry with exponential backoff
Order order = RetryUtil.executeWithBackoff(
    () -> orderService.create(request),
    5,      // max retries
    1000,   // initial delay (1s)
    2.0     // multiplier (1s, 2s, 4s, 8s, 16s)
);
```

**Custom Retry Policy:**
```java
RetryPolicy policy = RetryPolicy.builder()
    .maxRetries(5)
    .initialDelay(1000)
    .maxDelay(30000)
    .backoffMultiplier(2.0)
    .build();

Result result = RetryUtil.execute(() -> service.call(), policy);
```

**Real-World Example:**
```java
@Service
public class PaymentService {
    
    public Payment processPayment(PaymentRequest request) {
        return RetryUtil.executeWithBackoff(
            () -> {
                // Call external payment gateway
                return paymentGateway.charge(request);
            },
            3,      // Retry 3 times
            2000,   // Start with 2s delay
            2.0     // Double delay each retry (2s, 4s, 8s)
        );
    }
}
```

---

## 💡 Usage Examples

### E-Commerce Order Processing

```java
public class OrderService {
    
    // Specifications for business rules
    private final Specification<Product> inStock = 
        product -> product.getStock() > 0;
    
    private final Specification<Product> active = 
        product -> product.isActive();
    
    private final Specification<User> eligibleForDiscount =
        user -> user.isPremium() && user.getOrderCount() > 5;
    
    public Result<Order, OrderError> createOrder(OrderRequest request) {
        // Step 1: Validate request
        return validateRequest(request)
            // Step 2: Check inventory
            .flatMap(req -> checkInventory(req.getProducts()))
            // Step 3: Calculate total with discount
            .flatMap(req -> calculateTotal(req, eligibleForDiscount.test(req.getUser())))
            // Step 4: Process payment with retry
            .flatMap(this::processPaymentWithRetry)
            // Step 5: Save order
            .flatMap(this::saveOrder)
            // Step 6: Send confirmation
            .map(order -> {
                sendConfirmation(order);
                return order;
            });
    }
    
    private Result<PaymentResult, OrderError> processPaymentWithRetry(Order order) {
        try {
            PaymentResult result = RetryUtil.executeWithBackoff(
                () -> paymentGateway.charge(order.getTotal()),
                3, 2000, 2.0
            );
            return Result.success(result);
        } catch (Exception e) {
            return Result.failure(new OrderError("Payment failed: " + e.getMessage()));
        }
    }
    
    private Result<OrderRequest, OrderError> checkInventory(List<Product> products) {
        // Filter available products
        List<Product> available = inStock.and(active).filter(products);
        
        if (available.size() != products.size()) {
            return Result.failure(new OrderError("Some products unavailable"));
        }
        return Result.success(request);
    }
}
```

### User Management with Specifications

```java
public class UserQueryService {
    
    public List<User> findActiveAdmins() {
        Specification<User> activeAdmins = Specification
            .<User>where(user -> user.isActive())
            .and(user -> "ADMIN".equals(user.getRole()));
        
        return activeAdmins.filter(getAllUsers());
    }
    
    public List<User> findEligibleForRewards() {
        Specification<User> eligible = Specification
            .<User>where(user -> user.getOrderCount() > 10)
            .and(user -> user.getTotalSpent() > 1000)
            .and(user -> user.getLastOrderDate().isAfter(LocalDate.now().minusMonths(3)));
        
        return eligible.filter(getAllUsers());
    }
    
    public Optional<User> findTopSpender() {
        Specification<User> active = user -> user.isActive();
        
        return users.stream()
            .filter(active)
            .max(Comparator.comparing(User::getTotalSpent));
    }
}
```

---

## 🎯 Additional Core Features

### 4. Observer Pattern

Event-driven communication between objects.

```java
Observable<OrderEvent> orderEvents = new Observable<>();

// Subscribe observers
orderEvents.subscribe(event -> emailService.sendNotification(event));
orderEvents.subscribe(event -> analyticsService.track(event));
orderEvents.subscribe(event -> warehouseService.processOrder(event));

// Notify all observers
orderEvents.notifyObservers(new OrderCreatedEvent(order));
```

**Unsubscribe:**
```java
Subscription subscription = orderEvents.subscribe(observer);

// Later
subscription.unsubscribe();
```

---

### 5. Type Converter

Safe type conversions between different types.

```java
// String to various types
Integer age = TypeConverter.convert("25", Integer.class);
Boolean active = TypeConverter.convert("true", Boolean.class);
LocalDate date = TypeConverter.convert("2025-01-01", LocalDate.class);
BigDecimal price = TypeConverter.convert("99.99", BigDecimal.class);

// With default value
Integer count = TypeConverter.convert("invalid", Integer.class, 0);

// Object to Map
Map<String, Object> map = TypeConverter.objectToMap(user);

// Map to Object
User user = TypeConverter.mapToObject(map, User.class);

// Custom date parsing
LocalDate customDate = TypeConverter.parseDate("01/01/2025", "MM/dd/yyyy");
```

---

### 6. Lazy Initialization

Deferred object creation until first access.

```java
// Create lazy initializer
Lazy<ExpensiveResource> resource = Lazy.of(() -> {
    return new ExpensiveResource();
});

// Not created yet
boolean init = resource.isInitialized();  // false

// Created on first access
ExpensiveResource instance = resource.get();

// Same instance returned
ExpensiveResource same = resource.get();

// Reset for re-initialization
resource.reset();
ExpensiveResource newInstance = resource.get();
```

**Lazy Registry:**
```java
LazyRegistry registry = new LazyRegistry();

// Register lazy values
registry.register("database", () -> createDatabase());
registry.register("cache", () -> createCache());

// Get values (created on first access)
Database db = registry.get("database", Database.class);
Cache cache = registry.get("cache", Cache.class);
```

---

### 7. Async Utilities

Non-blocking async operations.

```java
// Run async
AsyncUtil.runAsync(() -> {
    processLargeFile();
});

// Supply async
CompletableFuture<User> future = AsyncUtil.supplyAsync(() -> {
    return userRepository.findById(userId);
});

User user = future.get();

// Parallel execution
List<String> userIds = Arrays.asList("1", "2", "3", "4", "5");

List<CompletableFuture<User>> futures = AsyncUtil.executeInParallel(
    userIds,
    userId -> userService.getUser(userId)
);

List<User> users = AsyncUtil.waitAll(futures);

// With timeout
List<Order> orders = AsyncUtil.waitAll(futures, 30, TimeUnit.SECONDS);

// Execute with timeout
Order order = AsyncUtil.executeWithTimeout(
    () -> orderService.getOrder(orderId),
    5,
    TimeUnit.SECONDS
);
```

---

### 8. Memoization

Cache expensive computations.

```java
Memoizer<String, User> userCache = new Memoizer<>();

// First call - computes and caches
User user = userCache.get("user123", userId -> {
    return expensiveDatabaseQuery(userId);
});

// Second call - returns cached value
User cached = userCache.get("user123", userId -> ...);

// With TTL (5 minutes)
Memoizer<String, Stock> stockCache = new Memoizer<>(300000);

Stock stock = stockCache.get("AAPL", symbol -> {
    return stockService.getQuote(symbol);
});

// Clear cache
userCache.clear();

// Invalidate specific key
userCache.invalidate("user123");
```

**Service Example:**
```java
public class PriceService {
    private final Memoizer<String, BigDecimal> priceCache = new Memoizer<>(60000);
    
    public BigDecimal getPrice(String productId) {
        return priceCache.get(productId, id -> {
            // Expensive calculation - cached for 1 minute
            return calculatePriceFromMultipleSources(id);
        });
    }
}
```

---

## 🎯 Annotations

### @Retry

Automatically retry failed method executions with exponential backoff.

```java
@Service
public class PaymentService {
    
    @Retry(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public PaymentResult processPayment(PaymentRequest request) {
        return paymentGateway.charge(request);
    }
}
```

**Configuration:**
- `maxAttempts` - Maximum retry attempts (default: 3)
- `backoff.delay` - Initial delay in ms (default: 1000)
- `backoff.maxDelay` - Maximum delay in ms (default: 30000)
- `backoff.multiplier` - Backoff multiplier (default: 2.0)
- `retryOn` - Exceptions to retry on (default: Exception.class)

### @Async

Execute methods asynchronously in a separate thread pool.

```java
@Service
public class NotificationService {
    
    @Async
    public CompletableFuture<Void> sendEmail(EmailRequest request) {
        emailClient.send(request);
        return CompletableFuture.completedFuture(null);
    }
    
    @Async(timeout = 5000)
    public CompletableFuture<Report> generateReport(String reportId) {
        Report report = reportGenerator.generate(reportId);
        return CompletableFuture.completedFuture(report);
    }
}
```

**Configuration:**
- `value` - Thread pool name (default: "default")
- `timeout` - Timeout in ms (0 = no timeout)

### @Memoize

Cache method results for improved performance.

```java
@Service
public class PriceService {
    
    @Memoize(ttl = 300000) // Cache for 5 minutes
    public BigDecimal getPrice(String productId) {
        return expensivePriceCalculation(productId);
    }
    
    @Memoize(value = "user-cache", ttl = 600000)
    public User getUserProfile(String userId) {
        return userRepository.findById(userId);
    }
}
```

**Configuration:**
- `value` - Cache name (default: "default")
- `ttl` - Time-to-live in ms (-1 = never expires)
- `useAllParams` - Use all parameters for cache key (default: true)

---

## ⚙️ Core Configuration

Configure all core features via properties:

**application.yml:**
```yaml
adhar:
  core:
    enabled: true
    
    # Async configuration
    async:
      enabled: true
      pool-size: 10
      max-pool-size: 50
      queue-capacity: 100
      thread-name-prefix: "adhar-async-"
      
    # Retry configuration
    retry:
      enabled: true
      max-attempts: 3
      initial-delay: 1000
      max-delay: 30000
      backoff-multiplier: 2.0
      
    # Circuit breaker
    circuit-breaker:
      enabled: true
      failure-threshold: 5
      success-threshold: 2
      timeout-duration: 60000
      
    # Cache configuration
    cache:
      enabled: true
      max-size: 1000
      ttl: 300
      tti: 180
      
    # Validation
    validation:
      enabled: true
      fail-fast: true
      validate-on-startup: true
```

---

## 📚 API Reference

### Patterns

#### Specification<T>

| Method | Description |
|--------|-------------|
| `and(Specification<T>)` | Combines with AND |
| `or(Specification<T>)` | Combines with OR |
| `negate()` | Negates specification |
| `filter(List<T>)` | Filters list |
| `findFirst(List<T>)` | Finds first match |
| `anyMatch(List<T>)` | Checks if any match |
| `allMatch(List<T>)` | Checks if all match |

#### Result<T, E>

| Method | Description |
|--------|-------------|
| `success(T)` | Creates success result |
| `failure(E)` | Creates failure result |
| `map(Function<T, U>)` | Maps success value |
| `flatMap(Function<T, Result<U, E>>)` | Flat maps success |
| `mapError(Function<E, F>)` | Maps error value |
| `ifSuccess(Consumer<T>)` | Executes on success |
| `ifFailure(Consumer<E>)` | Executes on failure |
| `match(Function, Function)` | Pattern matching |
| `getOrDefault(T)` | Gets value or default |
| `getOrThrow(Function)` | Gets value or throws |
| `toOptional()` | Converts to Optional |

#### Observable<T>

| Method | Description |
|--------|-------------|
| `subscribe(Observer<T>)` | Subscribes observer |
| `subscribe(Consumer<T>)` | Subscribes consumer |
| `unsubscribe(Observer<T>)` | Unsubscribes observer |
| `notifyObservers(T)` | Notifies all observers |
| `getObserverCount()` | Gets observer count |
| `clear()` | Clears all observers |

### Utilities

#### RetryUtil

| Method | Description |
|--------|-------------|
| `execute(Supplier, int, long)` | Execute with retry |
| `executeWithBackoff(...)` | Execute with exponential backoff |

#### TypeConverter

| Method | Description |
|--------|-------------|
| `convert(Object, Class<T>)` | Converts to type |
| `convert(Object, Class<T>, T)` | Converts with default |
| `objectToMap(Object)` | Object to Map |
| `mapToObject(Map, Class<T>)` | Map to Object |
| `parseDate(String, String)` | Parse date with pattern |
| `parseDateTime(String, String)` | Parse datetime with pattern |
| `canConvert(Object, Class)` | Checks if convertible |

#### Lazy<T>

| Method | Description |
|--------|-------------|
| `of(Supplier<T>)` | Creates lazy initializer |
| `get()` | Gets value (creates if needed) |
| `isInitialized()` | Checks if initialized |
| `reset()` | Resets value |
| `getIfInitialized()` | Gets if initialized |

#### AsyncUtil

| Method | Description |
|--------|-------------|
| `runAsync(Runnable)` | Runs task async |
| `supplyAsync(Supplier<T>)` | Supplies value async |
| `executeInParallel(List, Function)` | Parallel execution |
| `waitAll(List<Future>)` | Waits for all |
| `waitAll(List, timeout, unit)` | Waits with timeout |
| `executeWithTimeout(Supplier, timeout, unit)` | Execute with timeout |

#### Memoizer<K, V>

| Method | Description |
|--------|-------------|
| `get(K, Function<K, V>)` | Gets cached or computed |
| `clear()` | Clears cache |
| `invalidate(K)` | Invalidates key |
| `size()` | Gets cache size |
| `containsKey(K)` | Checks if cached |

---

## 🎯 Best Practices

### 1. Use Specifications for Complex Queries

```java
// ✅ Good - Composable and testable
Specification<Product> availableProducts = inStock
    .and(active)
    .and(notExpired);

// ❌ Bad - Monolithic condition
if (product.getStock() > 0 && product.isActive() && 
    !product.isExpired()) {
    // ...
}
```

### 2. Use Result for Error Handling

```java
// ✅ Good - Functional error handling
public Result<User, UserError> createUser(UserRequest request) {
    return validate(request)
        .flatMap(this::checkDuplicate)
        .flatMap(this::save);
}

// ❌ Bad - Exception-based
public User createUser(UserRequest request) throws ValidationException {
    validate(request);  // Throws
    checkDuplicate(request);  // Throws
    return save(request);  // Throws
}
```

### 3. Use Retry for Transient Failures

```java
// ✅ Good - Retry transient failures
Payment payment = RetryUtil.executeWithBackoff(
    () -> paymentGateway.charge(amount),
    3, 1000, 2.0
);

// ❌ Bad - No retry, fails immediately
Payment payment = paymentGateway.charge(amount);
```

---

## 🧪 Testing

```java
@Test
public void testSpecificationCombination() {
    Specification<User> activeUsers = user -> user.isActive();
    Specification<User> premiumUsers = user -> user.isPremium();
    
    Specification<User> combined = activeUsers.and(premiumUsers);
    
    User user = new User(true, true);
    assertTrue(combined.test(user));
}

@Test
public void testResultMapping() {
    Result<Integer, String> result = Result.success(10);
    
    Result<String, String> mapped = result.map(n -> "Number: " + n);
    
    assertTrue(mapped.isSuccess());
    assertEquals("Number: 10", mapped.getValue());
}

@Test
public void testRetrySuccess() throws Exception {
    AtomicInteger attempts = new AtomicInteger(0);
    
    String result = RetryUtil.execute(
        () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("Fail");
            }
            return "Success";
        },
        3, 100
    );
    
    assertEquals("Success", result);
    assertEquals(2, attempts.get());
}
```

---

## 📖 Resources

- [Specification Pattern](https://en.wikipedia.org/wiki/Specification_pattern)
- [Result Type](https://www.microsoft.com/en-us/research/publication/railway-oriented-programming/)
- [Exponential Backoff](https://en.wikipedia.org/wiki/Exponential_backoff)

---

**Built with ❤️ by Adhar Platform Team**

