# Adhar Kit Logging

Comprehensive enterprise logging framework for Spring Boot applications with AOP-based logging aspects, sensitive data masking, and distributed tracing support.

## Features

- **AdharLogger**: Consolidated logging utility for microservices
- **AOP-based Logging**: Declarative logging using annotations
- **Sensitive Data Masking**: Automatic masking of sensitive information in logs
- **Distributed Tracing**: Integration with Micrometer Tracing
- **MDC Support**: Mapped Diagnostic Context for correlation IDs and user context
- **Structured Logging**: JSON-formatted logs with configurable fields
- **Performance Monitoring**: Execution time tracking and metrics
- **Audit Logging**: Security and compliance audit trails
- **Exception Logging**: Centralized exception handling and logging

## Quick Start for Microservices

### Maven Dependency

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-logging</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Basic Configuration

Add to your `application.yml`:

```yaml
adhar:
  logging:
    enabled: true
    aspects:
      enabled: true
    masking:
      enabled: true
      additional-keys:
        - customSecret
        - apiKey
    mdc:
      enabled: true
      correlation-id-field: correlationId
    tracing:
      enabled: true
      trace-id-field: traceId
      span-id-field: spanId
```

### Using AdharLogger in Your Services

The `AdharLogger` is automatically configured and available for injection:

```java
@Service
public class UserService {
    
    private final AdharLogger adharLogger;
    
    public UserService(AdharLogger adharLogger) {
        this.adharLogger = adharLogger;
    }
    
    public User createUser(UserRequest request) {
        // Ensure correlation ID exists
        String correlationId = adharLogger.ensureCorrelationId(null);
        
        // Simple logging
        adharLogger.info(UserService.class, "Creating user: {}", request.getUsername());
        
        // JSON structured logging
        Map<String, Object> context = Map.of(
            "operation", "user_creation",
            "username", request.getUsername(),
            "timestamp", Instant.now()
        );
        adharLogger.infoJson(UserService.class, "User creation started", context);
        
        // Context-aware logging
        adharLogger.withContext(Map.of("userId", "123"), () -> {
            adharLogger.info(UserService.class, "Processing user data");
        });
        
        return new User();
    }
}
```

## Declarative Logging with Annotations

### @Loggable - Method Entry/Exit Logging

```java
@Service
public class OrderService {
    
    @Loggable(
        value = "createOrder",
        level = Level.INFO,
        logArgs = true,
        logResult = true,
        maskFields = {"creditCard", "ssn"},
        sampleRate = 1.0
    )
    public Order createOrder(@Sensitive String creditCard, OrderRequest request) {
        // Implementation
        return new Order();
    }
}
```

### @LogExecutionTime - Performance Monitoring

```java
@RestController
public class OrderController {
    
    @LogExecutionTime(
        value = "processOrder",
        level = Level.INFO,
        thresholdMs = 1000,  // Only log if > 1 second
        includeArgs = true,
        sampleRate = 0.1     // Log 10% of calls
    )
    public ResponseEntity<Order> processOrder(@RequestBody OrderRequest request) {
        // Implementation
        return ResponseEntity.ok(new Order());
    }
}
```

### @Audit - Security and Compliance Logging

```java
@RestController
public class AdminController {
    
    @Audit(
        eventType = "USER_DELETION",
        value = "deleteUser",
        level = Level.WARN,
        includeArgs = true,
        includeUser = true,
        tags = {"admin", "user-management"}
    )
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        // Implementation
        return ResponseEntity.ok().build();
    }
}
```

### @LogMetrics - Business Metrics

```java
@Service
public class AnalyticsService {
    
    @LogMetrics(
        metricName = "data_processing",
        value = "processAnalytics",
        includeExecutionTime = true,
        includeResult = true,
        tags = {"analytics", "batch-processing"},
        sampleRate = 0.05  // Log 5% for high-volume operations
    )
    public AnalyticsResult processAnalytics(AnalyticsRequest request) {
        // Implementation
        return new AnalyticsResult();
    }
}
```

## AdharLogger API Reference

### Correlation ID Management

```java
// Ensure correlation ID exists (generates new one if needed)
String correlationId = adharLogger.ensureCorrelationId(null);

// Set specific correlation ID
adharLogger.setCorrelationId("custom-correlation-id");

// Get current correlation ID
String currentId = adharLogger.getCorrelationId();
```

### User Context Management

```java
// Set user ID in MDC
adharLogger.setUserId("user123");

// Get current user ID
String userId = adharLogger.getUserId();
```

### MDC Operations

```java
// Set custom MDC values
adharLogger.put("requestId", "req-123");
adharLogger.put("tenantId", "tenant-456");

// Get MDC values
String requestId = adharLogger.get("requestId");

// Clear all MDC
adharLogger.clear();
```

### Context-Aware Logging

```java
// Execute with temporary context
adharLogger.withContext(Map.of("operation", "batch-process"), () -> {
    adharLogger.info(MyService.class, "Processing batch");
    // ... business logic
});

// Return value with context
String result = adharLogger.withContextSupplier(
    Map.of("operation", "calculation"), 
    () -> performCalculation()
);
```

### Structured JSON Logging

```java
// Log structured data
Map<String, Object> data = Map.of(
    "userId", "123",
    "action", "login",
    "timestamp", Instant.now(),
    "success", true
);

adharLogger.infoJson(MyService.class, "User login event", data);
adharLogger.warnJson(MyService.class, "Rate limit exceeded", data);
```

### Sensitive Data Utilities

```java
// Mask sensitive strings
String maskedPassword = adharLogger.maskString("password123"); // "***"
String partialMask = adharLogger.maskString("password123", 2, 1, '*'); // "pa*******3"

// Check if field is sensitive
boolean isSensitive = adharLogger.isSensitiveField("creditCardNumber"); // true
```

## Microservices Integration Examples

### REST Controller with Full Logging

```java
@RestController
@RequestMapping("/api/users")
@Loggable(level = Level.DEBUG, sampleRate = 0.1)  // Class-level logging
public class UserController {
    
    private final UserService userService;
    private final AdharLogger adharLogger;
    
    public UserController(UserService userService, AdharLogger adharLogger) {
        this.userService = userService;
        this.adharLogger = adharLogger;
    }
    
    @PostMapping
    @Audit(eventType = "USER_CREATION", includeUser = true, tags = {"api", "user"})
    @LogExecutionTime(thresholdMs = 500)
    public ResponseEntity<User> createUser(@RequestBody UserRequest request) {
        
        // Ensure correlation ID for distributed tracing
        String correlationId = adharLogger.ensureCorrelationId(
            request.getHeaders().getFirst("X-Correlation-ID")
        );
        
        try {
            User user = userService.createUser(request);
            
            adharLogger.infoWithContext(
                Map.of("userId", user.getId(), "operation", "user_created"),
                UserController.class,
                "User created successfully: {}",
                user.getUsername()
            );
            
            return ResponseEntity.ok(user);
            
        } catch (Exception e) {
            adharLogger.errorWithContext(
                Map.of("operation", "user_creation_failed"),
                UserController.class,
                "Failed to create user: {}", 
                e.getMessage()
            );
            throw e;
        }
    }
}
```

### Service Layer with Business Logic Logging

```java
@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final AdharLogger adharLogger;
    
    public OrderService(OrderRepository orderRepository, 
                       PaymentService paymentService,
                       AdharLogger adharLogger) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.adharLogger = adharLogger;
    }
    
    @Loggable(logArgs = true, logResult = true)
    @LogMetrics(metricName = "order_processing", includeExecutionTime = true)
    @LogExceptions(includeStackTrace = true)
    public Order processOrder(OrderRequest request) {
        
        return adharLogger.withContextSupplier(
            Map.of(
                "orderId", request.getOrderId(),
                "customerId", request.getCustomerId(),
                "amount", request.getAmount().toString()
            ),
            () -> {
                adharLogger.info(OrderService.class, "Starting order processing");
                
                // Validate order
                validateOrder(request);
                
                // Process payment
                PaymentResult payment = paymentService.processPayment(request.getPayment());
                
                // Create order
                Order order = createOrder(request, payment);
                
                adharLogger.infoJson(OrderService.class, "Order processed successfully", 
                    Map.of(
                        "orderId", order.getId(),
                        "status", order.getStatus(),
                        "processingTime", "calculated_elsewhere"
                    )
                );
                
                return order;
            }
        );
    }
    
    @LogExecutionTime(thresholdMs = 100)
    private void validateOrder(OrderRequest request) {
        adharLogger.debug(OrderService.class, "Validating order: {}", request.getOrderId());
        // Validation logic
    }
}
```

### Configuration Properties Reference

```yaml
adhar:
  logging:
    enabled: true                          # Enable/disable logging framework
    
    aspects:
      enabled: true                        # Enable/disable AOP aspects
    
    masking:
      enabled: true                        # Enable/disable sensitive data masking
      additional-keys:                     # Additional field names to mask
        - customSecret
        - internalToken
    
    mdc:
      enabled: true                        # Enable/disable MDC support
      include-correlation-id: true         # Include correlation IDs
      include-user-info: true              # Include user information
      correlation-id-field: correlationId
      user-id-field: userId
      session-id-field: sessionId
    
    tracing:
      enabled: true                        # Enable/disable tracing integration
      include-trace-id: true               # Include trace IDs
      include-span-id: true                # Include span IDs
      include-parent-id: true              # Include parent span IDs
      trace-id-field: traceId
      span-id-field: spanId
      parent-id-field: parentId
    
    json-encoder:
      include-mdc: true                    # Include MDC in JSON logs
      custom-fields:                       # Custom fields to add to logs
        application: "${spring.application.name}"
        version: "@project.version@"
        environment: "${spring.profiles.active}"
```

## Best Practices for Microservices

1. **Always use correlation IDs**: Call `adharLogger.ensureCorrelationId()` at service boundaries
2. **Structured logging**: Use `infoJson()` methods for complex data structures
3. **Context management**: Use `withContext()` for scoped logging context
4. **Sampling**: Use appropriate sample rates for high-volume operations
5. **Sensitive data**: Always mask PII and sensitive information
6. **Performance monitoring**: Use `@LogExecutionTime` with thresholds
7. **Audit trails**: Use `@Audit` for security-sensitive operations
8. **Exception handling**: Use `@LogExceptions` for centralized error logging

## Sample Configuration Files

A complete sample configuration is available at:
- `src/main/resources/application-logging-sample.yml`

Copy and customize this file for your microservice needs.

## Troubleshooting

### Common Issues

1. **AdharLogger not injected**: Ensure `adhar.logging.enabled=true` in configuration
2. **Aspects not working**: Verify `@EnableAspectJAutoProxy` and AspectJ dependencies
3. **Missing correlation IDs**: Check MDC configuration and web filter registration
4. **Performance impact**: Adjust sampling rates and execution time thresholds

### Debug Configuration

```yaml
logging:
  level:
    com.adhar.adharkit.logging: DEBUG
    root: INFO
```

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.
