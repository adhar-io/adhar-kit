# 🏥 Adhar Kit Health - Enterprise Health Check Module

**Automated health checks for enterprise microservices across all frameworks**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.x-blue.svg)](https://micronaut.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 1.0.0  
**Status:** ✅ Production Ready

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Quick Start](#quick-start)
- [Multi-Framework Support](#multi-framework-support)
- [Built-in Health Indicators](#built-in-health-indicators)
- [Custom Health Indicators](#custom-health-indicators)
- [Configuration](#configuration)
- [Health Models](#health-models)
- [Examples](#examples)
- [Best Practices](#best-practices)

---

## 🎯 Overview

The **adhar-kit-health** module provides comprehensive health check capabilities for enterprise microservices with:

- 🚀 **Auto-discovery** - Automatic health indicator registration
- 🔄 **Multi-Framework** - Works with Spring Boot, Quarkus, Micronaut
- 📊 **Built-in Indicators** - Database, Redis, Kafka, MongoDB, etc.
- 🎯 **Custom Indicators** - Easy creation of custom health checks
- ⚡ **Parallel Execution** - Fast health checks with concurrent execution
- 🛡️ **Timeout Handling** - Prevents hanging health checks
- 📈 **Kubernetes Ready** - Liveness and readiness probes
- 🔍 **Detailed Responses** - Comprehensive health information

---

## ✨ Features

### Core Features

✅ **Automated Health Checks**
- Auto-discovery of health indicators
- Parallel health check execution
- Timeout handling
- Exception handling

✅ **Built-in Indicators**
- Database connectivity
- Disk space monitoring
- Redis connection
- Kafka connectivity
- MongoDB health
- Elasticsearch health
- gRPC service health

✅ **Multi-Framework Support**
- Spring Boot (@Component)
- Quarkus (@ApplicationScoped)
- Micronaut (@Singleton)
- Same API across all frameworks

✅ **Kubernetes Integration**
- Liveness probes
- Readiness probes
- Startup probes
- Custom probe endpoints

---

## 🚀 Quick Start

### 1. Add Dependency

**Maven:**
```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-health</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'com.adhar.kit:adhar-kit-health:1.0.0'
```

### 2. Enable Health Checks

**application.yml:**
```yaml
adhar:
  health:
    enabled: true
    show-details: always
    show-components: true
    
    database:
      enabled: true
      timeout: 5000
    
    redis:
      enabled: true
      timeout: 3000
```

### 3. Create Health Endpoint

**Spring Boot:**
```java
@RestController
public class HealthController {
    
    @Autowired
    private HealthRegistry healthRegistry;
    
    @GetMapping("/health")
    public HealthResponse health() {
        return healthRegistry.checkHealth();
    }
    
    @GetMapping("/health/ready")
    public HealthResponse readiness() {
        return healthRegistry.checkHealth();
    }
    
    @GetMapping("/health/live")
    public HealthResponse liveness() {
        return healthRegistry.checkHealth();
    }
}
```

### 4. Custom Health Indicator

```java
@HealthIndicator(name = "payment-gateway")
@Component  // or @ApplicationScoped for Quarkus, @Singleton for Micronaut
public class PaymentGatewayHealthIndicator implements AdharHealthIndicator {
    
    @Autowired
    private PaymentGatewayClient paymentClient;
    
    @Override
    public Health check() {
        try {
            // Check payment gateway connectivity
            boolean isAvailable = paymentClient.ping();
            
            if (isAvailable) {
                return Health.up()
                    .withDetail("gateway", "available")
                    .withDetail("latency", "45ms")
                    .build();
            } else {
                return Health.down()
                    .withDetail("gateway", "unavailable")
                    .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    @Override
    public String getName() {
        return "payment-gateway";
    }
}
```

---

## 🌐 Multi-Framework Support

### Spring Boot Integration

```java
// Configuration
@Configuration
public class HealthConfig {
    
    @Bean
    public HealthRegistry healthRegistry(Collection<AdharHealthIndicator> indicators) {
        return SpringBootHealthIntegration.createHealthRegistry(indicators);
    }
    
    @Bean
    public DiskSpaceHealthIndicator diskSpaceHealthIndicator() {
        return new DiskSpaceHealthIndicator(1024 * 1024 * 100, "/"); // 100MB threshold
    }
}

// Custom Indicator
@HealthIndicator(name = "order-service")
@Component
public class OrderServiceHealthIndicator implements AdharHealthIndicator {
    
    @Autowired
    private OrderService orderService;
    
    @Override
    public Health check() {
        long activeOrders = orderService.getActiveOrderCount();
        
        return Health.up()
            .withDetail("activeOrders", activeOrders)
            .withDetail("status", "operational")
            .build();
    }
    
    @Override
    public String getName() {
        return "order-service";
    }
}
```

### Quarkus Integration

```java
// Configuration
@ApplicationScoped
public class HealthConfig {
    
    @Produces
    @Singleton
    public HealthRegistry healthRegistry(Instance<AdharHealthIndicator> indicators) {
        List<AdharHealthIndicator> indicatorList = StreamSupport.stream(
            indicators.spliterator(), false
        ).collect(Collectors.toList());
        
        return QuarkusHealthIntegration.createHealthRegistry(indicatorList);
    }
}

// Custom Indicator
@HealthIndicator(name = "order-service")
@ApplicationScoped
public class OrderServiceHealthIndicator implements AdharHealthIndicator {
    
    @Inject
    OrderService orderService;
    
    @Override
    public Health check() {
        return Health.up()
            .withDetail("status", "operational")
            .build();
    }
    
    @Override
    public String getName() {
        return "order-service";
    }
}
```

### Micronaut Integration

```java
// Configuration
@Factory
public class HealthConfig {
    
    @Bean
    @Singleton
    public HealthRegistry healthRegistry(Collection<AdharHealthIndicator> indicators) {
        return MicronautHealthIntegration.createHealthRegistry(indicators);
    }
}

// Custom Indicator
@HealthIndicator(name = "order-service")
@Singleton
public class OrderServiceHealthIndicator implements AdharHealthIndicator {
    
    @Inject
    private OrderService orderService;
    
    @Override
    public Health check() {
        return Health.up()
            .withDetail("status", "operational")
            .build();
    }
    
    @Override
    public String getName() {
        return "order-service";
    }
}
```

---

## 🔍 Built-in Health Indicators

### Database Health Indicator

Checks database connectivity and executes validation query.

```java
@Bean
public DatabaseHealthIndicator databaseHealthIndicator(
        DataSource dataSource,
        AdharHealthProperties properties) {
    return new DatabaseHealthIndicator(dataSource, properties.getDatabase());
}
```

**Health Response:**
```json
{
  "status": "UP",
  "component": "database",
  "details": {
    "database": "PostgreSQL",
    "version": "14.5",
    "validationQuery": "SELECT 1"
  }
}
```

### Disk Space Health Indicator

Monitors available disk space.

```java
@Bean
public DiskSpaceHealthIndicator diskSpaceHealthIndicator() {
    long threshold = 1024 * 1024 * 100; // 100MB
    String path = "/";
    return new DiskSpaceHealthIndicator(threshold, path);
}
```

**Health Response:**
```json
{
  "status": "UP",
  "component": "diskSpace",
  "details": {
    "total": "500.00 GB",
    "free": "150.00 GB",
    "usable": "145.00 GB",
    "threshold": "100.00 MB",
    "path": "/"
  }
}
```

---

## 🎨 Custom Health Indicators

### Simple Custom Indicator

```java
@HealthIndicator(name = "external-api")
@Component
public class ExternalApiHealthIndicator implements AdharHealthIndicator {
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Override
    public Health check() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                "https://api.example.com/health",
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return Health.up()
                    .withDetail("api", "available")
                    .withDetail("responseTime", "45ms")
                    .build();
            } else {
                return Health.down()
                    .withDetail("api", "unavailable")
                    .withDetail("statusCode", response.getStatusCode())
                    .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    @Override
    public String getName() {
        return "external-api";
    }
}
```

### Complex Custom Indicator

```java
@HealthIndicator(name = "business-rules", timeout = 3000)
@Component
public class BusinessRulesHealthIndicator implements AdharHealthIndicator {
    
    @Autowired
    private RulesEngine rulesEngine;
    
    @Autowired
    private MetricsCollector metrics;
    
    @Override
    public Health check() {
        try {
            // Check multiple aspects
            boolean rulesLoaded = rulesEngine.isLoaded();
            int activeRules = rulesEngine.getActiveRuleCount();
            long evaluationCount = metrics.getRuleEvaluationCount();
            double avgExecutionTime = metrics.getAverageExecutionTime();
            
            if (!rulesLoaded) {
                return Health.down()
                    .withDetail("status", "rules not loaded")
                    .build();
            }
            
            if (activeRules == 0) {
                return Health.down()
                    .withDetail("status", "no active rules")
                    .build();
            }
            
            // Check if execution time is too high
            if (avgExecutionTime > 1000) {
                return Health.outOfService()
                    .withDetail("status", "performance degraded")
                    .withDetail("avgExecutionTime", avgExecutionTime + "ms")
                    .build();
            }
            
            return Health.up()
                .withDetail("rulesLoaded", rulesLoaded)
                .withDetail("activeRules", activeRules)
                .withDetail("totalEvaluations", evaluationCount)
                .withDetail("avgExecutionTime", avgExecutionTime + "ms")
                .build();
                
        } catch (Exception e) {
            return Health.down(e)
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    @Override
    public String getName() {
        return "business-rules";
    }
}
```

---

## ⚙️ Configuration

### Complete Configuration Example

**application.yml:**
```yaml
adhar:
  health:
    enabled: true
    show-details: always              # always, never, when-authorized
    show-components: true
    
    # Endpoints
    endpoint: "/health"
    readiness-endpoint: "/health/ready"
    liveness-endpoint: "/health/live"
    
    # Database
    database:
      enabled: true
      timeout: 5000
      validation-query: "SELECT 1"
    
    # Redis
    redis:
      enabled: true
      timeout: 3000
    
    # Kafka
    kafka:
      enabled: true
      timeout: 5000
    
    # MongoDB
    mongo:
      enabled: true
      timeout: 3000
    
    # Elasticsearch
    elasticsearch:
      enabled: true
      timeout: 3000
    
    # gRPC
    grpc:
      enabled: true
      timeout: 3000
    
    # Custom Indicators
    custom:
      payment-gateway:
        enabled: true
        timeout: 5000
        properties:
          url: "https://payment.example.com"
          api-key: "${PAYMENT_API_KEY}"
```

---

## 📦 Health Models

### Health Model

```java
// Create healthy status
Health health = Health.up()
    .component("service-name")
    .withDetail("version", "1.0.0")
    .withDetail("uptime", uptimeSeconds)
    .build();

// Create unhealthy status
Health health = Health.down()
    .component("service-name")
    .withDetail("error", "Connection timeout")
    .build();

// Create unhealthy status with exception
Health health = Health.down(exception)
    .component("service-name")
    .build();

// Create out-of-service status
Health health = Health.outOfService()
    .component("service-name")
    .withDetail("reason", "Maintenance mode")
    .build();

// Create unknown status
Health health = Health.unknown()
    .component("service-name")
    .build();
```

### HealthResponse Model

```java
HealthResponse response = HealthResponse.builder()
    .status(Health.Status.UP)
    .timestamp(LocalDateTime.now())
    .build();

// Add components
response.addComponent("database", databaseHealth);
response.addComponent("redis", redisHealth);

// Add details
response.addDetail("version", "1.0.0");
response.addDetail("environment", "production");
```

---

## 💡 Examples

### Complete Health Endpoint

```java
@RestController
@RequestMapping("/health")
public class HealthController {
    
    @Autowired
    private HealthRegistry healthRegistry;
    
    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = healthRegistry.checkHealth();
        
        HttpStatus status = response.isHealthy() ? 
            HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            
        return ResponseEntity.status(status).body(response);
    }
    
    @GetMapping("/ready")
    public ResponseEntity<HealthResponse> readiness() {
        HealthResponse response = healthRegistry.checkHealth();
        
        // Check specific components for readiness
        boolean isReady = response.getComponents().values().stream()
            .allMatch(Health::isUp);
            
        HttpStatus status = isReady ? 
            HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            
        return ResponseEntity.status(status).body(response);
    }
    
    @GetMapping("/live")
    public ResponseEntity<Void> liveness() {
        // Simple liveness check
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{component}")
    public ResponseEntity<Health> componentHealth(@PathVariable String component) {
        AdharHealthIndicator indicator = healthRegistry.getIndicator(component);
        
        if (indicator == null) {
            return ResponseEntity.notFound().build();
        }
        
        Health health = indicator.check();
        HttpStatus status = health.isUp() ? 
            HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            
        return ResponseEntity.status(status).body(health);
    }
}
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  template:
    spec:
      containers:
      - name: order-service
        image: order-service:1.0.0
        ports:
        - containerPort: 8080
        
        # Liveness Probe
        livenessProbe:
          httpGet:
            path: /health/live
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        
        # Readiness Probe
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        
        # Startup Probe
        startupProbe:
          httpGet:
            path: /health/live
            port: 8080
          initialDelaySeconds: 0
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 30
```

---

## 📊 Best Practices

### 1. Timeout Configuration

```java
@HealthIndicator(name = "slow-service", timeout = 3000)
@Component
public class SlowServiceHealthIndicator implements AdharHealthIndicator {
    
    @Override
    public Health check() {
        // Health check logic
        // Should complete within 3 seconds
    }
}
```

### 2. Graceful Degradation

```java
@HealthIndicator(name = "payment")
@Component
public class PaymentHealthIndicator implements AdharHealthIndicator {
    
    @Override
    public Health check() {
        try {
            // Primary check
            boolean primary = checkPrimaryGateway();
            
            if (primary) {
                return Health.up()
                    .withDetail("gateway", "primary")
                    .build();
            }
            
            // Fallback check
            boolean fallback = checkFallbackGateway();
            
            if (fallback) {
                return Health.outOfService()
                    .withDetail("gateway", "fallback")
                    .withDetail("warning", "using fallback gateway")
                    .build();
            }
            
            return Health.down()
                .withDetail("gateway", "none")
                .build();
                
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

### 3. Resource Cleanup

```java
@Component
public class HealthRegistryCleanup {
    
    @Autowired
    private HealthRegistry healthRegistry;
    
    @PreDestroy
    public void cleanup() {
        healthRegistry.shutdown();
    }
}
```

### 4. Caching for Expensive Checks

```java
@HealthIndicator(name = "expensive")
@Component
public class ExpensiveHealthIndicator implements AdharHealthIndicator {
    
    private volatile Health cachedHealth;
    private volatile long lastCheck = 0;
    private static final long CACHE_DURATION = 60000; // 1 minute
    
    @Override
    public Health check() {
        long now = System.currentTimeMillis();
        
        if (cachedHealth != null && (now - lastCheck) < CACHE_DURATION) {
            return cachedHealth;
        }
        
        // Perform expensive check
        Health health = performExpensiveCheck();
        
        cachedHealth = health;
        lastCheck = now;
        
        return health;
    }
    
    private Health performExpensiveCheck() {
        // Expensive operation
        return Health.up().build();
    }
}
```

---

## 🔗 Related Modules

- [adhar-kit-commons](../adhar-kit-commons) - Common utilities
- [adhar-kit-metrics](../adhar-kit-metrics) - Metrics collection
- [adhar-kit-tracing](../adhar-kit-tracing) - Distributed tracing

---

## 🤝 Contributing

Contributions are welcome! Please follow our [contribution guidelines](../CONTRIBUTING.md).

---

## 📄 License

Apache License 2.0 - see [LICENSE](../LICENSE) for details.

---

**Built with ❤️ by Adhar Platform Team**

