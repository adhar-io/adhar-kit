# Adhar Kit Tracing

A comprehensive, enterprise-grade distributed tracing library for Spring Boot applications with seamless OpenTelemetry, Zipkin, and Jaeger integration.

## Overview

The **adhar-kit-tracing** module provides a robust distributed tracing infrastructure for Spring Boot applications, offering both annotation-based and programmatic tracing capabilities. It includes advanced features like automatic instrumentation, context propagation, baggage management, and multi-backend support.

## Features

### 🎯 **Annotation-Based Tracing**
- `@NewSpan` - Create new spans for method execution
- `@ContinueSpan` - Add context to existing spans
- `@DatabaseSpan` - Database operation tracing with semantic attributes
- `@HttpClientSpan` - HTTP client request tracing
- `@MessagingSpan` - Message queue operation tracing
- `@AsyncSpan` - Asynchronous operation tracing with context propagation

### 🔧 **Consolidated Programmatic API**
- **AdharTracing** - Unified utility for all tracing operations including:
  - Manual span management with fluent API
  - Baggage management for cross-service context propagation
  - Async operation tracing with context preservation
  - Semantic span creation (database, HTTP, messaging)
  - Error handling and exception recording
- **TracingAspect** - AOP-based automatic instrumentation

### 🚀 **Enterprise Features**
- **Multi-backend support** - OpenTelemetry, Zipkin, Jaeger (via OTLP)
- **Flexible sampling** - Probability-based, rate-limited, per-service
- **Context propagation** - W3C Trace Context, B3, Jaeger formats
- **Kubernetes integration** - Automatic resource attribute detection
- **Performance optimization** - Minimal overhead, configurable features

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-tracing</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2. Configuration

```yaml
adhar:
  tracing:
    enabled: true
    tags:
      application: "my-service"
      version: "1.0.0"
    sampling:
      probability: 0.1
    open-telemetry:
      enabled: true
      endpoint: "http://otel-collector:4317"
    zipkin:
      enabled: false
    jaeger:
      enabled: true
      grpc-endpoint: "http://jaeger:14250"
```

### 3. Basic Usage

#### Annotation-Based Tracing

```java
@Service
public class UserService {
    
    @NewSpan("user.create")
    public User createUser(@SpanTag("user.email") String email) {
        // Method automatically traced
        return userRepository.save(new User(email));
    }
    
    @DatabaseSpan(operation = "SELECT", table = "users")
    public List<User> findUsers() {
        return userRepository.findAll();
    }
}
```

#### Programmatic Tracing with Consolidated API

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final AdharTracing tracing;
    
    public Order processOrder(Order order) {
        return tracing.withinSpan("order.process", () -> {
            // Set baggage for cross-service context
            tracing.setBaggage("user.id", order.getUserId());
            tracing.setBaggage("order.type", order.getType());
            
            // Add tags for observability
            tracing.addTag("order.amount", String.valueOf(order.getAmount()));
            
            // Process order
            Order processedOrder = businessLogic.process(order);
            
            // Record success event
            tracing.addEvent("order.processed", Map.of(
                "order.id", processedOrder.getId(),
                "processing.time", "150ms"
            ));
            
            return processedOrder;
        });
    }
    
    public CompletableFuture<Void> processOrderAsync(Order order) {
        return tracing.withinSpanAsync("order.process.async", 
            () -> CompletableFuture.supplyAsync(() -> {
                // Async processing with trace context preserved
                return processOrder(order);
            }).thenAccept(result -> {
                // Handle result
            })
        );
    }
}
```

#### Manual Span Management

```java
@Component
@RequiredArgsConstructor
public class PaymentProcessor {
    
    private final AdharTracing tracing;
    
    public void processPayment(Payment payment) {
        // Create semantic spans
        Span paymentSpan = tracing.createDatabaseSpan("INSERT", "payments", 
            "INSERT INTO payments (amount, status) VALUES (?, ?)");
        
        try (Tracer.SpanInScope scope = tracer.withSpanInScope(paymentSpan.start())) {
            // Process payment
            paymentRepository.save(payment);
            
            // Add success indicators
            tracing.addTag("payment.status", "success");
            
        } catch (Exception e) {
            // Record exception
            tracing.recordException(e);
            throw e;
        } finally {
            paymentSpan.end();
        }
    }
}
```

#### Baggage Management

```java
@RestController
@RequiredArgsConstructor
public class ApiController {
    
    private final AdharTracing tracing;
    
    @PostMapping("/orders")
    public ResponseEntity<Order> createOrder(@RequestBody Order order, 
                                           HttpServletRequest request) {
        // Extract baggage from incoming headers
        Map<String, String> headers = extractHeaders(request);
        tracing.extractBaggageFromHeaders(headers);
        
        // Set additional context
        tracing.setBaggage("request.id", UUID.randomUUID().toString());
        
        Order result = orderService.processOrder(order);
        
        // Inject baggage into outgoing response headers
        Map<String, String> responseHeaders = new HashMap<>();
        tracing.injectBaggageIntoHeaders(responseHeaders);
        
        return ResponseEntity.ok()
            .headers(createHttpHeaders(responseHeaders))
            .body(result);
    }
}
```

## Configuration Reference

### Core Configuration

```yaml
adhar:
  tracing:
    enabled: true                    # Enable/disable tracing
    tags:                           # Global tags for all spans
      service: "user-service"
      version: "1.2.3"
      environment: "production"
    
    sampling:
      probability: 0.1              # Sample 10% of traces
      rate-limit: 1000             # Max 1000 spans per second
    
    resource:
      service-name: "${spring.application.name}"
      service-version: "${app.version:unknown}"
      service-namespace: "${app.namespace:default}"
      service-instance-id: "${HOSTNAME:${random.uuid}}"
```

### Backend Configuration

#### OpenTelemetry (Recommended)

```yaml
adhar:
  tracing:
    open-telemetry:
      enabled: true
      endpoint: "http://otel-collector:4317"
      use-grpc: true
      timeout: "PT30S"
      compression: "gzip"
      headers:
        "api-key": "${OTEL_API_KEY}"
```

#### Jaeger (via OTLP)

```yaml
adhar:
  tracing:
    jaeger:
      enabled: true
      use-grpc: true
      grpc-endpoint: "http://jaeger:14250"
      http-endpoint: "http://jaeger:14268/api/traces"
      timeout: "PT30S"
```

#### Zipkin

```yaml
adhar:
  tracing:
    zipkin:
      enabled: true
      base-url: "http://zipkin:9411"
      api-path: "/api/v2/spans"
      read-timeout: "PT10S"
```

### Advanced Features

#### Context Propagation

```yaml
adhar:
  tracing:
    propagation:
      type: "tracecontext"          # w3c, b3, jaeger
      b3-single-header: false
      additional-formats:
        - "b3"
        - "jaeger"
```

#### Baggage Configuration

```yaml
adhar:
  tracing:
    baggage:
      enabled: true
      max-entries: 32
      max-value-length: 1024
      allowed-keys:
        - "user.id"
        - "tenant.id"
        - "correlation.id"
```

#### Web Tracing

```yaml
adhar:
  tracing:
    web:
      enabled: true
      trace-http-clients: true
      include-request-headers: true
      include-response-headers: false
      url-patterns:
        - "/api/**"
        - "/actuator/health"
```

## API Reference

### AdharTracing Methods

#### Span Management
- `withinSpan(String name, Supplier<T> operation)` - Execute code within a span
- `withinSpan(String name, Map<String,String> tags, Supplier<T> operation)` - Execute with tags
- `withinSpan(String name, Runnable operation)` - Execute void operation
- `withinSpanCallable(String name, Callable<T> callable)` - Execute callable with exceptions
- `withinSpanAsync(String name, Supplier<CompletableFuture<T>> operation)` - Async execution

#### Span Information
- `getCurrentSpan()` - Get current active span
- `getCurrentTraceId()` - Get current trace ID
- `getCurrentSpanId()` - Get current span ID
- `isTracingActive()` - Check if tracing is active

#### Span Manipulation
- `addTag(String key, String value)` - Add tag to current span
- `addTags(Map<String,String> tags)` - Add multiple tags
- `addEvent(String name)` - Add event to current span
- `addEvent(String name, Map<String,String> attributes)` - Add event with attributes
- `recordException(Throwable exception)` - Record exception in span

#### Semantic Spans
- `createDatabaseSpan(String operation, String table, String statement)` - Database span
- `createHttpClientSpan(String method, String url)` - HTTP client span
- `createMessagingSpan(String operation, String destination, String system)` - Messaging span

#### Baggage Management
- `setBaggage(String key, String value)` - Set baggage item
- `getBaggage(String key)` - Get baggage item
- `getAllBaggage()` - Get all baggage items
- `clearBaggage()` - Clear all baggage
- `setBaggageItems(Map<String,String> items)` - Set multiple items
- `extractBaggageFromHeaders(Map<String,String> headers)` - Extract from HTTP headers
- `injectBaggageIntoHeaders(Map<String,String> headers)` - Inject into HTTP headers

#### Context Propagation
- `wrapWithTraceContext(Function<T,R> function)` - Wrap function with context
- `wrapWithTraceContext(Consumer<T> consumer)` - Wrap consumer with context
- `wrapWithTraceContext(Runnable runnable)` - Wrap runnable with context

## Migration Guide

### From Separate Utilities

If you were previously using separate `BaggageUtils`, all functionality has been consolidated into `AdharTracing`:

```java
// Before (deprecated)
@Autowired private BaggageUtils baggageUtils;
@Autowired private AdharTracing adharTracing;

baggageUtils.setBaggage("key", "value");
adharTracing.withinSpan("operation", () -> {...});

// After (consolidated)
@Autowired private AdharTracing tracing;

tracing.setBaggage("key", "value");
tracing.withinSpan("operation", () -> {...});
```

## Performance Considerations

- **Sampling**: Use appropriate sampling rates for production (typically 0.01-0.1)
- **Async Operations**: Use `withinSpanAsync()` for proper context propagation
- **Baggage**: Limit baggage size and entries for optimal performance
- **Tags**: Use consistent tag naming for better observability

## Troubleshooting

### Common Issues

1. **No traces appearing**: Check sampling configuration and backend connectivity
2. **Context not propagated**: Ensure proper async span management
3. **High overhead**: Reduce sampling rate or disable expensive features
4. **Jaeger connection fails**: Jaeger now uses OTLP - update endpoints accordingly

### Debug Configuration

```yaml
logging:
  level:
    com.adhar.adharkit.tracing: DEBUG
    io.micrometer.tracing: DEBUG
    io.opentelemetry: DEBUG
```

## Examples

See the [examples directory](./examples) for complete working examples including:
- Spring Boot REST API with tracing
- Microservices with baggage propagation
- Database and messaging integration
- Custom instrumentation patterns

## License

This project is licensed under the MIT License - see the [LICENSE](../LICENSE) file for details.
