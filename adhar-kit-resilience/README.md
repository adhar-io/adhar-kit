# 🛡️ Adhar Kit Resilience - Enterprise Fault Tolerance

**Resilience patterns for microservices using Resilience4j**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.x-blue.svg)](https://micronaut.io/)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-2.x-blue.svg)](https://resilience4j.readme.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 1.0.0  
**Status:** ✅ Production Ready

---

## 📖 Overview

Enterprise-grade resilience patterns for microservices using Resilience4j.

## 🎯 Features

- **Circuit Breaker** - Prevent cascading failures
- **Retry** - Automatic retry with exponential backoff
- **Rate Limiter** - Control request rates
- **Bulkhead** - Isolate resources and limit concurrent executions
- **Time Limiter** - Handle timeouts gracefully
- **Metrics Integration** - Full Micrometer metrics support
- **Annotation-based** - Simple annotation-driven configuration

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-resilience</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Basic Usage

#### Circuit Breaker

```java
@Service
public class PaymentService {
    
    @CircuitBreaker(name = "payment", fallbackMethod = "fallbackPayment")
    public PaymentResponse processPayment(PaymentRequest request) {
        // Call external payment gateway
        return externalPaymentGateway.process(request);
    }
    
    private PaymentResponse fallbackPayment(PaymentRequest request, Exception ex) {
        // Fallback logic
        return PaymentResponse.failed("Service unavailable");
    }
}
```

#### Retry with Exponential Backoff

```java
@Service
public class OrderService {
    
    @Retry(name = "order", maxAttempts = 5, waitDuration = 1000L)
    public Order createOrder(OrderRequest request) {
        // Will retry up to 5 times with 1 second wait
        return orderRepository.save(request);
    }
}
```

#### Rate Limiting

```java
@RestController
@RequestMapping("/api/search")
public class SearchController {
    
    @RateLimit(name = "search", limitForPeriod = 100, limitRefreshPeriod = 60000L)
    @GetMapping
    public SearchResults search(@RequestParam String query) {
        // Limited to 100 requests per minute
        return searchService.search(query);
    }
}
```

#### Bulkhead Pattern

```java
@Service
public class ReportService {
    
    @Bulkhead(name = "report", maxConcurrentCalls = 10)
    public Report generateReport(ReportRequest request) {
        // Only 10 concurrent report generations allowed
        return reportGenerator.generate(request);
    }
}
```

#### Time Limiter

```java
@Service
public class DataService {
    
    @TimeLimiter(name = "data", timeoutDuration = 5000L)
    public CompletableFuture<DataResponse> fetchData(String id) {
        // Will timeout after 5 seconds
        return CompletableFuture.supplyAsync(() -> 
            dataRepository.findById(id));
    }
}
```

## Configuration

### application.yml

```yaml
adhar:
  resilience:
    enabled: true
    
    circuit-breaker:
      payment:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 100
        slow-call-duration-threshold: 60s
        sliding-window-size: 100
        minimum-number-of-calls: 10
        wait-duration-in-open-state: 60s
        
    retry:
      order:
        max-attempts: 5
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        
    rate-limiter:
      search:
        limit-for-period: 100
        limit-refresh-period: 1m
        timeout-duration: 5s
        
    bulkhead:
      report:
        max-concurrent-calls: 10
        max-wait-duration: 100ms
        
    time-limiter:
      data:
        timeout-duration: 5s
        cancel-running-future: true
        
    metrics:
      enabled: true
      export-circuit-breaker-metrics: true
      export-retry-metrics: true
      export-rate-limiter-metrics: true
      export-bulkhead-metrics: true
```

### application.properties

```properties
# Enable resilience
adhar.resilience.enabled=true

# Circuit Breaker
adhar.resilience.circuit-breaker.payment.failure-rate-threshold=50
adhar.resilience.circuit-breaker.payment.sliding-window-size=100
adhar.resilience.circuit-breaker.payment.wait-duration-in-open-state=60s

# Retry
adhar.resilience.retry.order.max-attempts=5
adhar.resilience.retry.order.wait-duration=1s
adhar.resilience.retry.order.enable-exponential-backoff=true

# Rate Limiter
adhar.resilience.rate-limiter.search.limit-for-period=100
adhar.resilience.rate-limiter.search.limit-refresh-period=60s

# Metrics
adhar.resilience.metrics.enabled=true
```

## Advanced Usage

### Combining Multiple Patterns

```java
@Service
public class AdvancedService {
    
    @CircuitBreaker(name = "external-api", fallbackMethod = "fallback")
    @Retry(name = "external-api", maxAttempts = 3)
    @RateLimit(name = "external-api", limitForPeriod = 50)
    @TimeLimiter(name = "external-api", timeoutDuration = 3000L)
    public Response callExternalApi(Request request) {
        // Protected by multiple resilience patterns
        return externalApiClient.call(request);
    }
    
    private Response fallback(Request request, Exception ex) {
        return Response.error("Service temporarily unavailable");
    }
}
```

### Accessing Metrics Programmatically

```java
@Service
@RequiredArgsConstructor
public class MonitoringService {
    
    private final ResilienceMetricsService metricsService;
    
    public void checkHealth() {
        // Get circuit breaker metrics
        var cbMetrics = metricsService.getCircuitBreakerMetrics("payment");
        log.info("Circuit breaker state: {}, failure rate: {}%", 
                 cbMetrics.getState(), cbMetrics.getFailureRate());
        
        // Get all retry metrics
        var retryMetrics = metricsService.getRetryMetrics();
        retryMetrics.forEach((name, metrics) -> 
            log.info("Retry {}: {} successful, {} failed", 
                     name, metrics.getNumberOfSuccessfulCallsWithRetryAttempt(),
                     metrics.getNumberOfFailedCallsWithRetryAttempt()));
    }
}
```

## Metrics

All resilience patterns export metrics to Micrometer:

- **Circuit Breaker Metrics**: state, failure rate, slow call rate, call counts
- **Retry Metrics**: successful/failed calls with/without retry
- **Rate Limiter Metrics**: available permissions, waiting threads
- **Bulkhead Metrics**: available/max concurrent calls
- **Time Limiter Metrics**: timeout count, successful calls

Access metrics via:
- Prometheus (`/actuator/prometheus`)
- Metrics endpoint (`/actuator/metrics`)
- Custom monitoring dashboards

## Best Practices

1. **Use Meaningful Names**: Use descriptive names for resilience instances
2. **Configure Per Service**: Different services may need different thresholds
3. **Implement Fallbacks**: Always provide fallback methods for critical operations
4. **Monitor Metrics**: Set up alerts on circuit breaker state changes
5. **Combine Patterns**: Use multiple patterns together for better protection
6. **Test Failures**: Regularly test your fallback mechanisms
7. **Tune Parameters**: Adjust thresholds based on actual service behavior

## Architecture

```
┌─────────────────────────────────────────────────────┐
│           Application Method Call                   │
└──────────────────┬──────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────┐
│          ResilienceAspect (AOP)                     │
│  - Intercepts annotated methods                     │
│  - Applies resilience patterns                      │
└──────────────────┬──────────────────────────────────┘
                   │
        ┌──────────┴────────────┐
        │                       │
┌───────▼────────┐    ┌────────▼────────┐
│ CircuitBreaker │    │     Retry       │
│    Registry    │    │    Registry     │
└───────┬────────┘    └────────┬────────┘
        │                      │
┌───────▼────────┐    ┌────────▼────────┐
│  RateLimiter   │    │    Bulkhead     │
│    Registry    │    │    Registry     │
└───────┬────────┘    └────────┬────────┘
        │                      │
        └──────────┬───────────┘
                   │
┌──────────────────▼──────────────────────────────────┐
│         Micrometer Metrics Registry                 │
│  - Exports metrics to monitoring systems            │
└─────────────────────────────────────────────────────┘
```

## Dependencies

- Spring Boot 4.0+
- Resilience4j 2.1.0
- Spring AOP
- Micrometer Core

## License

Copyright © 2025 Adhar Platform Team

## Support

For issues and questions, please contact the Adhar Platform Team.

