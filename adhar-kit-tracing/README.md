# 🔍 Adhar Kit Tracing - Enterprise Distributed Tracing

**Comprehensive distributed tracing with OpenTelemetry**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.x-blue.svg)](https://micronaut.io/)
[![OpenTelemetry](https://img.shields.io/badge/OpenTelemetry-1.x-blue.svg)](https://opentelemetry.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 0.1.0-SNAPSHOT  
**Status:** ✅ Production Ready

---

## 📖 Overview

A comprehensive, enterprise-grade distributed tracing library for Spring Boot applications with seamless OpenTelemetry, Zipkin, and Jaeger integration.

## 🎯 Features

### 🎯 **Annotation-Based Tracing**
- `@NewSpan` - Create new spans for method execution
- `@ContinueSpan` - Add context to existing spans
- `@DatabaseSpan` - Database operation tracing with semantic attributes
- `@HttpClientSpan` - HTTP client request tracing
- `@MessagingSpan` - Message queue operation tracing
- `@AsyncSpan` - Asynchronous operation tracing with context propagation
- `@SpanTag` - Tag a method parameter's value (or a SpEL expression over it) onto the span
  created/continued by one of the annotations above

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
    <version>0.1.0-SNAPSHOT</version>
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
        // Method automatically traced; "user.email" is tagged with the email parameter's value
        return userRepository.save(new User(email));
    }

    @NewSpan("order.process")
    public Order processOrder(@SpanTag(value = "order.id", expression = "id") Order order) {
        // @SpanTag's expression is SpEL evaluated with the parameter's own value as the root
        // object, so "id" here reads order.getId()
        return process(order);
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

Baggage is backed by Micrometer Tracing's real, context-scoped baggage API
(`Tracer#createBaggageInScope`/`Tracer#getAllBaggage`) rather than a local map, so it is
visible wherever the trace context propagates (including into child spans created on the
same thread). `AdharTracing` additionally handles cross-process propagation using the
standard **W3C `baggage` HTTP header** (see https://www.w3.org/TR/baggage/) — a single header
with comma-separated, percent-encoded `key=value` members.

```java
@RestController
@RequiredArgsConstructor
public class ApiController {
    
    private final AdharTracing tracing;
    
    @PostMapping("/orders")
    public ResponseEntity<Order> createOrder(@RequestBody Order order, 
                                           HttpServletRequest request) {
        // Extract baggage from the incoming W3C `baggage` header
        Map<String, String> headers = extractHeaders(request);
        tracing.extractBaggageFromHeaders(headers);
        
        // Set additional context
        tracing.setBaggage("request.id", UUID.randomUUID().toString());
        
        Order result = orderService.processOrder(order);
        
        // Inject baggage into the outgoing W3C `baggage` header
        Map<String, String> responseHeaders = new HashMap<>();
        tracing.injectBaggageIntoHeaders(responseHeaders);
        
        return ResponseEntity.ok()
            .headers(createHttpHeaders(responseHeaders))
            .body(result);
    }
}
```

Baggage entries listed in `adhar.tracing.baggage.correlation-fields` are additionally tagged
onto the current span as `baggage.<key>` for visibility in the trace backend. Entries are only
propagated across the wire via `injectBaggageIntoHeaders` when listed in
`adhar.tracing.baggage.remote-fields` (or all entries, if that list is empty).

#### Trace Context Propagation Across Threads

`wrapWithTraceContext(...)` captures the current span at wrap-time and re-attaches it (opening
a new `Tracer.SpanInScope`, closed in a `finally`) for the duration of every invocation of the
wrapped `Function`/`Consumer`/`Runnable`/`Supplier`/`Callable` — safe to hand off to another
thread (e.g. an executor):

```java
Runnable work = tracing.wrapWithTraceContext(() -> doSomething());
executorService.submit(work); // `doSomething()` still sees the original span as "current"
```

For `@Async` methods, wire `TraceContextTaskDecorator` into your executor instead:

```java
@Bean
public Executor taskExecutor(TraceContextTaskDecorator taskDecorator) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(taskDecorator);
    executor.initialize();
    return executor;
}
```

#### MDC / Log Correlation

When Servlet + Spring MVC are on the classpath, the auto-configuration registers
`TraceContextMdcFilter`, which injects the current request's `traceId`/`spanId` into the SLF4J
MDC for the duration of the request (cleared in a `finally`, so it never leaks onto a pooled
thread), honoring `adhar.tracing.web.skip-patterns`. Include `%X{traceId}`/`%X{spanId}` in your
logging pattern to correlate log lines with traces. Disable it with
`adhar.tracing.web.mdc-enabled=false`.

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
      # Only these keys are propagated across processes via injectBaggageIntoHeaders;
      # if empty, all current baggage entries are propagated.
      remote-fields:
        - "user.id"
        - "tenant.id"
      # These keys are additionally tagged onto the current span as "baggage.<key>".
      correlation-fields:
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
      # Registers TraceContextMdcFilter to inject traceId/spanId into the SLF4J MDC
      # for the duration of each request (see "MDC / Log Correlation" above).
      mdc-enabled: true
      skip-patterns:
        - "/actuator/**"
        - "/health/**"
        - "/info/**"
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
Backed by the real Micrometer `Tracer` baggage API (context-scoped), not a local map.
- `setBaggage(String key, String value)` - Set baggage item
- `getBaggage(String key)` - Get baggage item
- `removeBaggage(String key)` - Remove a baggage item
- `getAllBaggage()` - Get all baggage items
- `clearBaggage()` - Clear all baggage
- `setBaggageItems(Map<String,String> items)` - Set multiple items
- `containsBaggageKey(String key)` / `getBaggageCount()` / `isBaggageEmpty()`
- `copyBaggageToSpan(Span span)` - Tag a span with all current baggage entries
- `extractBaggageFromHeaders(Map<String,String> headers)` - Parse the W3C `baggage` header
- `injectBaggageIntoHeaders(Map<String,String> headers)` - Write the W3C `baggage` header

#### Context Propagation
Each captures the current span at wrap-time and re-attaches it (`tracer.withSpan(...)`,
closed in a `finally`) for every invocation of the wrapped delegate.
- `wrapWithTraceContext(Function<T,R> function)` - Wrap function with context
- `wrapWithTraceContext(Consumer<T> consumer)` - Wrap consumer with context
- `wrapWithTraceContext(Runnable runnable)` - Wrap runnable with context
- `wrapWithTraceContext(Supplier<T> supplier)` - Wrap supplier with context
- `wrapWithTraceContext(Callable<T> callable)` - Wrap callable with context
- `TraceContextTaskDecorator` (Spring `TaskDecorator`) - wire into `@Async` executors for the
  same effect without touching call sites

## Migration Guide

### From Separate Utilities

If you were previously using separate `BaggageUtils`, all functionality has been consolidated into `AdharTracing`:

```java
// Before (deprecated) - separate dependencies
private final BaggageUtils baggageUtils;
private final AdharTracing adharTracing;

baggageUtils.setBaggage("key", "value");
adharTracing.withinSpan("operation", () -> {...});

// After (consolidated) - single dependency via constructor injection
private final AdharTracing tracing;

public MyService(AdharTracing tracing) {
    this.tracing = tracing;
}

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
