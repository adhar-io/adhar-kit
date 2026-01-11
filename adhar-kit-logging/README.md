# 📝 Adhar Kit Logging - Enterprise Logging Framework

**Comprehensive multi-framework logging solution for enterprise microservices**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.x-blue.svg)](https://micronaut.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Quick Start](#quick-start)
- [Annotations](#annotations)
- [AdharLogger API](#adharlogger-api)
- [Sensitive Data Masking](#sensitive-data-masking)
- [MDC Context](#mdc-context)
- [Distributed Tracing](#distributed-tracing)
- [Multi-Framework Support](#multi-framework-support)
- [Configuration](#configuration)
- [Examples](#examples)
- [Best Practices](#best-practices)

---

## 🎯 Overview

The **adhar-kit-logging** module provides enterprise-grade logging capabilities for microservices with:

- 📝 **Universal Logging Facade** - Works across Spring Boot, Quarkus, and Micronaut
- 🎭 **AOP-based Logging** - Declarative logging using annotations
- 🔒 **Sensitive Data Masking** - Automatic PII/credential masking
- 🔗 **Distributed Tracing** - Seamless integration with OpenTelemetry/Zipkin
- 🏷️ **MDC Support** - Correlation IDs and contextual information
- 📊 **Structured Logging** - JSON output for log aggregation
- ⚡ **Performance Tracking** - Execution time and metrics monitoring
- 🔍 **Audit Logging** - Security and compliance audit trails
- 🎯 **Exception Logging** - Centralized error tracking

---

## ✨ Features

### Core Features

✅ **Multi-Framework Logging Facade**
- Universal API across Spring Boot, Quarkus, Micronaut
- Framework-agnostic logging interface
- Automatic framework detection

✅ **Annotations (6)**
- `@Loggable` - Method entry/exit logging
- `@LogExecutionTime` - Performance monitoring
- `@LogExceptions` - Exception handling
- `@LogMetrics` - Metrics collection
- `@Audit` - Audit trail logging
- `@Sensitive` - Data masking

✅ **AdharLogger Utility**
- Correlation ID management
- MDC context operations
- Structured JSON logging
- Tracing integration
- User context tracking

✅ **Sensitive Data Masking**
- Credit card numbers
- Email addresses
- Phone numbers
- Passwords
- API keys
- Custom patterns

✅ **Distributed Tracing**
- OpenTelemetry integration
- Zipkin support
- Trace/Span ID propagation
- Automatic context injection

---

## 🚀 Quick Start

### 1. Add Dependency

**Maven:**
```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-logging</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'com.adhar.kit:adhar-kit-logging:1.0.0'
```

### 2. Configure

**application.yml:**
```yaml
adhar:
  logging:
    enabled: true
    
    # MDC Context
    mdc:
      enabled: true
      include-correlation-id: true
      include-user-info: true
      correlation-id-field: correlationId
      user-id-field: userId
    
    # Sensitive Data Masking
    masking:
      enabled: true
      mask-credit-cards: true
      mask-emails: false
      mask-phone-numbers: true
      mask-passwords: true
      additional-keys:
        - apiKey
        - secretToken
    
    # Distributed Tracing
    tracing:
      enabled: true
      include-trace-id: true
      include-span-id: true
      trace-id-field: traceId
      span-id-field: spanId
    
    # AOP Aspects
    aspects:
      enabled: true
      log-method-execution: true
      log-exceptions: true
```

### 3. Use Logging

**Spring Boot:**
```java
@Service
public class OrderService {
    
    @Autowired
    private AdharLogger adharLogger;
    
    @Loggable(logArgs = true, logResult = true)
    public Order createOrder(OrderRequest request) {
        // Method entry/exit automatically logged
        adharLogger.info(OrderService.class, "Processing order for customer: {}", 
            request.getCustomerId());
        
        return processOrder(request);
    }
}
```

**Quarkus:**
```java
@ApplicationScoped
public class OrderService {
    
    @Inject
    AdharLogger adharLogger;
    
    @Loggable(logArgs = true, logResult = true)
    public Order createOrder(OrderRequest request) {
        adharLogger.info(OrderService.class, "Processing order for customer: {}", 
            request.getCustomerId());
        
        return processOrder(request);
    }
}
```

**Micronaut:**
```java
@Singleton
public class OrderService {
    
    @Inject
    private AdharLogger adharLogger;
    
    @Loggable(logArgs = true, logResult = true)
    public Order createOrder(OrderRequest request) {
        adharLogger.info(OrderService.class, "Processing order for customer: {}", 
            request.getCustomerId());
        
        return processOrder(request);
    }
}
```

---

## 🏷️ Annotations

### @Loggable

Automatically logs method entry and exit with optional arguments and results.

```java
@Loggable(
    value = "create-order",           // Operation name
    level = Level.INFO,                // Log level
    logArgs = true,                    // Log method arguments
    logResult = true,                  // Log return value
    maskFields = {"password", "cvv"},  // Fields to mask
    sampleRate = 1.0                   // Sampling rate (0.0 - 1.0)
)
public Order createOrder(OrderRequest request) {
    // Logs: [ENTRY] create-order with args: {...}
    // ... processing ...
    // Logs: [EXIT] create-order returned: {...}
    return order;
}
```

**Output:**
```
INFO  [correlationId=abc123] OrderService - [ENTRY] create-order with args: {customerId=12345, amount=***}
INFO  [correlationId=abc123] OrderService - [EXIT] create-order returned: {orderId=67890, status=CREATED}
```

### @LogExecutionTime

Tracks and logs method execution time for performance monitoring.

```java
@LogExecutionTime(
    value = "process-payment",
    threshold = 1000,        // Log warning if > 1 second
    level = Level.INFO
)
public PaymentResult processPayment(PaymentRequest request) {
    // Automatically logs execution time
    return payment;
}
```

**Output:**
```
INFO  [correlationId=abc123] PaymentService - [PERFORMANCE] process-payment executed in 850ms
```

### @LogExceptions

Centralized exception logging with context.

```java
@LogExceptions(
    logStackTrace = true,
    includeContext = true,
    level = Level.ERROR
)
public void processOrder(Order order) {
    // Exceptions automatically logged with full context
    throw new OrderProcessingException("Payment failed");
}
```

**Output:**
```
ERROR [correlationId=abc123] OrderService - [EXCEPTION] OrderProcessingException: Payment failed
  Context: {orderId=123, userId=456, amount=100.00}
  Stack trace: ...
```

### @LogMetrics

Collects and logs method execution metrics.

```java
@LogMetrics(
    value = "order-processing",
    recordSuccess = true,
    recordFailure = true,
    includeExecutionTime = true
)
public Order processOrder(OrderRequest request) {
    // Metrics automatically collected
    return order;
}
```

### @Audit

Creates audit trail for security and compliance.

```java
@Audit(
    action = "USER_LOGIN",
    includeUser = true,
    includeTimestamp = true,
    includeResult = true
)
public LoginResponse login(LoginRequest request) {
    // Audit log automatically created
    return response;
}
```

**Output:**
```
INFO  [AUDIT] action=USER_LOGIN, user=john@example.com, timestamp=2025-11-02T10:30:00Z, result=SUCCESS
```

### @Sensitive

Marks fields or parameters for automatic masking.

```java
public class User {
    private String name;
    
    @Sensitive
    private String password;
    
    @Sensitive
    private String ssn;
    
    private String email;
}

// Logged as: {name=John Doe, password=***, ssn=***, email=john@example.com}
```

---

## 📖 AdharLogger API

### Basic Logging

```java
@Autowired
private AdharLogger logger;

// Simple logging
logger.info(MyClass.class, "Processing started");
logger.debug(MyClass.class, "Debug info: {}", value);
logger.warn(MyClass.class, "Warning message");
logger.error(MyClass.class, "Error occurred", exception);

// With parameters
logger.info(MyClass.class, "User {} logged in from {}", userId, ipAddress);
```

### Correlation ID Management

```java
// Ensure correlation ID exists (auto-generate if missing)
String correlationId = logger.ensureCorrelationId(null);

// Set specific correlation ID
logger.setCorrelationId("order-12345");

// Get current correlation ID
String currentId = logger.getCorrelationId();

// Execute with correlation ID
logger.withCorrelationId("order-12345", () -> {
    logger.info(MyClass.class, "Processing with correlation ID");
});
```

### MDC Context Operations

```java
// Set single MDC value
logger.put("orderId", "12345");
logger.put("customerId", "67890");

// Set multiple values
logger.putAll(Map.of(
    "orderId", "12345",
    "customerId", "67890",
    "region", "US-WEST"
));

// Execute with temporary context
logger.withContext(Map.of("requestId", "req-123"), () -> {
    logger.info(MyClass.class, "Processing request");
    // Context automatically cleared after execution
});

// Get MDC value
String orderId = logger.get("orderId");

// Remove MDC value
logger.remove("orderId");

// Clear all MDC
logger.clear();
```

### User Context

```java
// Set user ID
logger.setUserId("john.doe@example.com");

// Get user ID
String userId = logger.getUserId();

// Set user name
logger.setUserName("John Doe");

// Get user name
String userName = logger.getUserName();
```

### Structured JSON Logging

```java
// Log with structured data
Map<String, Object> data = Map.of(
    "orderId", "12345",
    "amount", 100.50,
    "currency", "USD",
    "items", 3
);

logger.infoJson(MyClass.class, "Order created", data);

// Output: {"message":"Order created","orderId":"12345","amount":100.50,"currency":"USD","items":3}
```

### Tracing Integration

```java
// Get current trace ID
String traceId = logger.getTraceId();

// Get current span ID
String spanId = logger.getSpanId();

// Log with trace context
logger.infoWithTrace(MyClass.class, "Processing order");
// Output: [traceId=abc123, spanId=def456] Processing order
```

---

## 🔒 Sensitive Data Masking

### Automatic Masking

```java
// Credit cards automatically masked
logger.info(MyClass.class, "Card: 4532-1234-5678-9010");
// Output: Card: ****-****-****-9010

// Emails masked (if enabled)
logger.info(MyClass.class, "Email: john.doe@example.com");
// Output: Email: j***@example.com

// Passwords always masked
Map<String, Object> data = Map.of(
    "username", "john",
    "password", "secret123"
);
logger.infoJson(MyClass.class, "Login attempt", data);
// Output: {"username":"john","password":"***"}
```

### Custom Masking

```java
// Add custom sensitive fields
adhar:
  logging:
    masking:
      additional-keys:
        - apiKey
        - secretToken
        - internalId

// In code
Map<String, Object> data = Map.of(
    "apiKey", "sk_live_abc123",
    "publicId", "pk_test_xyz"
);
logger.infoJson(MyClass.class, "API call", data);
// Output: {"apiKey":"***","publicId":"pk_test_xyz"}
```

### Field-level Masking

```java
@Loggable(
    logArgs = true,
    maskFields = {"password", "cvv", "ssn"}
)
public User registerUser(UserRequest request) {
    // password, cvv, ssn fields automatically masked in logs
    return user;
}
```

---

## 🏷️ MDC Context

### Automatic MDC Propagation

```java
@RestController
public class OrderController {
    
    @Autowired
    private AdharLogger logger;
    
    @GetMapping("/orders/{id}")
    public Order getOrder(@PathVariable String id,
                         @RequestHeader("X-Correlation-ID") String correlationId) {
        
        // Set correlation ID for entire request
        logger.setCorrelationId(correlationId);
        
        // All subsequent logs include correlation ID
        logger.info(OrderController.class, "Fetching order: {}", id);
        
        return orderService.getOrder(id);
        // Correlation ID automatically included in all logs
    }
}
```

### Async Context Propagation

```java
@Service
public class OrderService {
    
    @Autowired
    private AdharLogger logger;
    
    @Async
    public CompletableFuture<Order> processOrderAsync(OrderRequest request) {
        // MDC context automatically propagated to async thread
        logger.info(OrderService.class, "Async processing started");
        
        return CompletableFuture.completedFuture(order);
    }
}
```

---

## 🔗 Distributed Tracing

### OpenTelemetry Integration

```java
@Service
public class PaymentService {
    
    @Autowired
    private AdharLogger logger;
    
    @Autowired
    private Tracer tracer;
    
    public PaymentResult processPayment(PaymentRequest request) {
        // Start span
        Span span = tracer.spanBuilder("process-payment")
            .startSpan();
        
        try (var scope = tracer.withSpan(span)) {
            // Trace ID automatically included in logs
            logger.info(PaymentService.class, "Processing payment");
            
            return payment;
        } finally {
            span.end();
        }
    }
}
```

### Automatic Trace Propagation

```yaml
adhar:
  logging:
    tracing:
      enabled: true
      include-trace-id: true
      include-span-id: true
```

**Output:**
```
INFO  [traceId=abc123def456, spanId=789012] PaymentService - Processing payment
```

---

## 🌐 Multi-Framework Support

### Spring Boot

```java
// Configuration
@Configuration
public class LoggingConfig {
    
    @Bean
    public AdharLogger adharLogger(AdharLoggingProperties properties,
                                   @Autowired(required = false) Tracer tracer) {
        return new AdharLogger(properties, tracer);
    }
}

// Service
@Service
public class OrderService {
    
    @Autowired
    private AdharLogger logger;
    
    @Loggable(logArgs = true)
    public void processOrder(Order order) {
        logger.info(OrderService.class, "Processing order: {}", order.getId());
    }
}
```

### Quarkus

```java
// Producer
@ApplicationScoped
public class LoggingProducer {
    
    @Produces
    @Singleton
    public AdharLogger adharLogger(AdharLoggingProperties properties,
                                   @Inject Tracer tracer) {
        return new AdharLogger(properties, tracer);
    }
}

// Service
@ApplicationScoped
public class OrderService {
    
    @Inject
    AdharLogger logger;
    
    @Loggable(logArgs = true)
    public void processOrder(Order order) {
        logger.info(OrderService.class, "Processing order: {}", order.getId());
    }
}
```

### Micronaut

```java
// Factory
@Factory
public class LoggingFactory {
    
    @Bean
    @Singleton
    public AdharLogger adharLogger(AdharLoggingProperties properties,
                                   @Nullable Tracer tracer) {
        return new AdharLogger(properties, tracer);
    }
}

// Service
@Singleton
public class OrderService {
    
    @Inject
    private AdharLogger logger;
    
    @Loggable(logArgs = true)
    public void processOrder(Order order) {
        logger.info(OrderService.class, "Processing order: {}", order.getId());
    }
}
```

---

## ⚙️ Configuration

### Complete Configuration Example

```yaml
adhar:
  logging:
    # Enable/disable logging features
    enabled: true
    
    # ========================================
    # MDC (Mapped Diagnostic Context)
    # ========================================
    mdc:
      enabled: true
      include-correlation-id: true
      include-user-info: true
      include-session-id: false
      correlation-id-field: correlationId
      user-id-field: userId
      user-name-field: userName
      session-id-field: sessionId
    
    # ========================================
    # Sensitive Data Masking
    # ========================================
    masking:
      enabled: true
      mask-credit-cards: true
      mask-emails: false
      mask-phone-numbers: true
      mask-passwords: true
      mask-api-keys: true
      replacement-text: "***"
      additional-keys:
        - apiKey
        - secretToken
        - internalId
        - ssn
    
    # ========================================
    # Distributed Tracing
    # ========================================
    tracing:
      enabled: true
      include-trace-id: true
      include-span-id: true
      trace-id-field: traceId
      span-id-field: spanId
      parent-span-id-field: parentSpanId
    
    # ========================================
    # AOP Aspects
    # ========================================
    aspects:
      enabled: true
      log-method-execution: true
      log-exceptions: true
      log-execution-time: true
      log-metrics: true
      audit-logging: true
    
    # ========================================
    # Console Logging
    # ========================================
    console:
      enabled: true
      pattern: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
      use-color: true
    
    # ========================================
    # File Logging
    # ========================================
    file:
      enabled: false
      name: logs/application.log
      max-size: 10MB
      max-history: 30
      total-size-cap: 1GB
    
    # ========================================
    # JSON Encoder
    # ========================================
    json-encoder:
      enabled: false
      include-mdc: true
      include-caller-data: false
      include-context-name: true
      include-level-value: true
    
    # ========================================
    # Async Logging
    # ========================================
    async:
      enabled: false
      queue-size: 256
      discard-threshold: 0
      max-flush-time: 1000
    
    # ========================================
    # Log Levels
    # ========================================
    levels:
      root: INFO
      custom-levels:
        com.adhar: DEBUG
        org.springframework: INFO
        org.hibernate: WARN
```

---

## 💡 Examples

### Example 1: REST API with Full Logging

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @Autowired
    private AdharLogger logger;
    
    @Autowired
    private OrderService orderService;
    
    @PostMapping
    @Loggable(logArgs = true, logResult = true, maskFields = {"cardNumber", "cvv"})
    @Audit(action = "CREATE_ORDER", includeUser = true)
    public ResponseEntity<Order> createOrder(
            @RequestBody OrderRequest request,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        
        // Set correlation ID for request tracking
        logger.setCorrelationId(correlationId);
        
        // Set user context
        logger.setUserId(request.getUserId());
        
        // Add business context
        logger.withContext(Map.of(
            "customerId", request.getCustomerId(),
            "orderType", request.getType()
        ), () -> {
            logger.info(OrderController.class, "Creating order for customer: {}", 
                request.getCustomerId());
            
            // Process order
            Order order = orderService.createOrder(request);
            
            logger.info(OrderController.class, "Order created successfully: {}", 
                order.getId());
        });
        
        return ResponseEntity.ok(order);
    }
}
```

### Example 2: Service with Exception Handling

```java
@Service
public class PaymentService {
    
    @Autowired
    private AdharLogger logger;
    
    @LogExecutionTime(threshold = 1000)
    @LogExceptions(logStackTrace = true, includeContext = true)
    @LogMetrics(value = "payment-processing", recordSuccess = true, recordFailure = true)
    public PaymentResult processPayment(PaymentRequest request) {
        logger.info(PaymentService.class, "Processing payment for amount: {}", 
            request.getAmount());
        
        try {
            // Add payment context
            logger.putAll(Map.of(
                "paymentId", request.getId(),
                "amount", String.valueOf(request.getAmount()),
                "currency", request.getCurrency()
            ));
            
            // Process payment
            PaymentResult result = executePayment(request);
            
            // Log structured result
            logger.infoJson(PaymentService.class, "Payment successful", Map.of(
                "paymentId", result.getId(),
                "status", result.getStatus(),
                "transactionId", result.getTransactionId()
            ));
            
            return result;
            
        } catch (PaymentException e) {
            logger.error(PaymentService.class, "Payment failed: {}", e.getMessage(), e);
            throw e;
        } finally {
            // Cleanup context
            logger.remove("paymentId");
            logger.remove("amount");
            logger.remove("currency");
        }
    }
}
```

### Example 3: Async Processing with Context Propagation

```java
@Service
public class OrderProcessingService {
    
    @Autowired
    private AdharLogger logger;
    
    @Async
    @Loggable(logArgs = true)
    public CompletableFuture<OrderResult> processOrderAsync(Order order) {
        // MDC context automatically propagated to async thread
        
        logger.info(OrderProcessingService.class, "Starting async order processing");
        
        return CompletableFuture.supplyAsync(() -> {
            // Context available in async operation
            logger.info(OrderProcessingService.class, "Processing order: {}", 
                order.getId());
            
            // Simulate processing
            OrderResult result = process(order);
            
            logger.infoJson(OrderProcessingService.class, "Order processed", Map.of(
                "orderId", order.getId(),
                "status", result.getStatus(),
                "duration", result.getDuration()
            ));
            
            return result;
        });
    }
}
```

---

## 📊 Best Practices

### 1. Always Use Correlation IDs

```java
@RestController
public class ApiController {
    
    @Autowired
    private AdharLogger logger;
    
    @PostMapping("/process")
    public ResponseEntity<?> process(@RequestHeader("X-Correlation-ID") String correlationId) {
        // Always set correlation ID at entry point
        logger.ensureCorrelationId(correlationId);
        
        // All downstream logs will include correlation ID
        return service.process();
    }
}
```

### 2. Mask Sensitive Data

```java
// Always mask PII and sensitive data
@Loggable(
    logArgs = true,
    maskFields = {"password", "ssn", "cardNumber", "cvv"}
)
public User createUser(UserRequest request) {
    return user;
}
```

### 3. Use Structured Logging

```java
// Prefer structured logging over string concatenation
// ❌ Bad
logger.info(MyClass.class, "Order " + orderId + " processed for " + customerId);

// ✅ Good
logger.info(MyClass.class, "Order processed", Map.of(
    "orderId", orderId,
    "customerId", customerId
));
```

### 4. Clean Up MDC Context

```java
public void processOrder(Order order) {
    try {
        logger.put("orderId", order.getId());
        // ... processing ...
    } finally {
        // Always cleanup
        logger.remove("orderId");
    }
}

// Or use withContext for automatic cleanup
logger.withContext(Map.of("orderId", order.getId()), () -> {
    // Context automatically cleared
});
```

### 5. Use Appropriate Log Levels

```java
// DEBUG - Detailed diagnostic information
logger.debug(MyClass.class, "Processing step 1 of 5");

// INFO - General informational messages
logger.info(MyClass.class, "Order created: {}", orderId);

// WARN - Warning messages for potentially harmful situations
logger.warn(MyClass.class, "Low inventory for product: {}", productId);

// ERROR - Error events that might still allow the application to continue
logger.error(MyClass.class, "Failed to process payment", exception);
```

---

## 🔗 Related Modules

- [adhar-kit-commons](../adhar-kit-commons) - Common utilities
- [adhar-kit-tracing](../adhar-kit-tracing) - Distributed tracing
- [adhar-kit-metrics](../adhar-kit-metrics) - Metrics collection

---

## 🤝 Contributing

Contributions are welcome! Please follow our [contribution guidelines](../CONTRIBUTING.md).

---

## 📄 License

Apache License 2.0 - see [LICENSE](../LICENSE) for details.

---

**Built with ❤️ by Adhar Platform Team**

