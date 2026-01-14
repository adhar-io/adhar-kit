# Adhar Kit BOM (Bill of Materials)

**Version:** 1.0.0  
**Status:** ✅ Production Ready  
**Description:** Centralized dependency management for the Adhar Kit Enterprise Microservices Foundation

---

## 📖 Overview

The **Adhar Kit BOM** provides centralized version management for all Adhar Kit modules and their dependencies. It ensures version compatibility across all modules and simplifies dependency management in your projects.

### What is a BOM?

A Bill of Materials (BOM) is a Maven POM that centralizes dependency version management. By importing the Adhar Kit BOM, you don't need to specify versions for Adhar Kit modules or common third-party dependencies.

### Benefits

✅ **Version Consistency** - All modules use compatible versions  
✅ **Simplified Configuration** - No need to specify versions  
✅ **Dependency Conflict Resolution** - Prevents version conflicts  
✅ **Easy Upgrades** - Update BOM version to upgrade all modules  
✅ **Production Tested** - All versions are tested together  
✅ **Java 21 LTS Compatible** - Tested with Java 21 LTS

---

## 🚀 Quick Start

### Import the BOM

Add the BOM to your project's `pom.xml`:

```xml
<project>
    <dependencyManagement>
        <dependencies>
            <!-- Import Adhar Kit BOM -->
            <dependency>
                <groupId>com.adhar.kit</groupId>
                <artifactId>adhar-kit-bom</artifactId>
                <version>1.0.0</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### Use Adhar Kit Modules (No Version Required!)

```xml
<dependencies>
    <!-- No version needed - managed by BOM -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-commons</artifactId>
    </dependency>

    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-logging</artifactId>
    </dependency>

    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-metrics</artifactId>
    </dependency>
</dependencies>
```

---

## 📦 Managed Modules

### Core & Foundation (3 modules)

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| **Commons** | `adhar-kit-commons` | Base utilities, exceptions, models |
| **Core** | `adhar-kit-core` | Core abstractions and patterns |
| **Test Commons** | `adhar-kit-test-commons` | Testing utilities and test containers |

### Configuration & Discovery (1 module)

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| **Config** | `adhar-kit-config` | Centralized configuration management |

### Data & Persistence (2 modules)

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| **Persistence** | `adhar-kit-persistence` | JPA, QueryDSL, multi-tenancy, soft delete |
| **Cache** | `adhar-kit-cache` | Redis, Caffeine, hybrid caching |

### Messaging & Events (2 modules)

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| **Messaging** | `adhar-kit-messaging` | Kafka, RabbitMQ, event-driven patterns |
| **MERS** | `adhar-kit-mers` | Microservices Event-driven Reactive Streaming |

### Observability (4 modules)

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| **Logging** | `adhar-kit-logging` | Structured logging with MDC, masking |
| **Metrics** | `adhar-kit-metrics` | Micrometer, Prometheus, custom metrics |
| **Tracing** | `adhar-kit-tracing` | Distributed tracing with OpenTelemetry |
| **Health** | `adhar-kit-health` | Health checks and readiness probes |

### Resilience & Security (2 modules)

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| **Resilience** | `adhar-kit-resilience` | Circuit breakers, retries, bulkheads |
| **Security** | `adhar-kit-security` | JWT, OAuth2, RBAC, encryption |

### API & Communication (2 modules)

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| **Docs** | `adhar-kit-docs` | OpenAPI/Swagger documentation |
| **gRPC** | `adhar-kit-grpc` | gRPC server/client support |

### Cloud Native (2 modules)

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| **Kubernetes** | `adhar-kit-kubernetes` | Kubernetes client and utilities |
| **Dapr** | `adhar-kit-dapr` | Dapr integration for microservices |

### AI & Analytics (2 modules)

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| **AI** | `adhar-kit-ai` | Spring AI, LangChain4j integration |
| **Analytics** | `adhar-kit-analytics` | PostHog analytics with 8 annotations |

### All-in-One (1 module)

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| **Starter** | `adhar-kit-starter` | Includes all commonly used modules |

**Total: 23 Production-Ready Modules**

---

## 📚 Version Matrix

### Spring Ecosystem

| Dependency | Version | Notes |
|------------|---------|-------|
| Spring Boot | 3.4.1 | Java 21 LTS compatible (GA) |
| Spring Framework | 6.2.1 | Included with Boot |
| Spring Cloud | 2024.0.0 | Latest release |
| Spring Kafka | 3.3.0 | Kafka integration |
| Spring AMQP | 3.2.0 | RabbitMQ support |

### Core Libraries

| Dependency | Version | Notes |
|------------|---------|-------|
| Lombok | 1.18.34 | Java 21 compatible |
| MapStruct | 1.6.0 | Object mapping |
| Jakarta Persistence | 3.2.0 | JPA 3.2 |
| Jakarta Validation | 3.1.0 | Bean Validation |
| QueryDSL | 5.1.0 | Type-safe queries |

### Observability

| Dependency | Version | Notes |
|------------|---------|-------|
| Micrometer | 1.15.0 | Metrics |
| Micrometer Tracing | 1.5.0 | Distributed tracing |
| OpenTelemetry | 1.45.0 | OTLP exporter |
| Logstash Encoder | 7.4 | JSON logging |
| Janino | 3.1.10 | Logback conditions |

### Resilience

| Dependency | Version | Notes |
|------------|---------|-------|
| Resilience4j | 2.3.0 | Circuit breakers |

### API & Communication

| Dependency | Version | Notes |
|------------|---------|-------|
| SpringDoc OpenAPI | 2.8.0 | Swagger UI |
| gRPC | 1.70.0 | RPC framework |
| Protocol Buffers | 4.29.0 | Serialization |

### Cloud Native

| Dependency | Version | Notes |
|------------|---------|-------|
| Dapr SDK | 1.14.0 | Dapr integration |
| Kubernetes Client | 7.1.0 | K8s Java client |

### AI & ML

| Dependency | Version | Notes |
|------------|---------|-------|
| Spring AI | 1.0.0 | OpenAI, Azure AI (GA) |
| LangChain4j | 0.38.0 | LLM framework |

### Analytics

| Dependency | Version | Notes |
|------------|---------|-------|
| PostHog | 3.1.1 | Product analytics |

### Testing

| Dependency | Version | Notes |
|------------|---------|-------|
| Testcontainers | 1.20.4 | Docker containers |
| Mockito | 5.15.2 | Java 21 compatible |
| Byte Buddy | 1.17.0 | Java agent |
| WireMock | 3.10.0 | HTTP mocking |
| Awaitility | 4.2.2 | Async testing |

---

## 💡 Usage Examples

### Basic Spring Boot Application

```xml
<project>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
    </parent>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.adhar.kit</groupId>
                <artifactId>adhar-kit-bom</artifactId>
                <version>1.0.0</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Adhar Kit -->
        <dependency>
            <groupId>com.adhar.kit</groupId>
            <artifactId>adhar-kit-commons</artifactId>
        </dependency>
        <dependency>
            <groupId>com.adhar.kit</groupId>
            <artifactId>adhar-kit-logging</artifactId>
        </dependency>
        <dependency>
            <groupId>com.adhar.kit</groupId>
            <artifactId>adhar-kit-metrics</artifactId>
        </dependency>
    </dependencies>
</project>
```

### Microservice with Full Stack

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- Adhar Kit - Observability -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-logging</artifactId>
    </dependency>
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-metrics</artifactId>
    </dependency>
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-tracing</artifactId>
    </dependency>

    <!-- Adhar Kit - Resilience -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-resilience</artifactId>
    </dependency>

    <!-- Adhar Kit - Data -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-persistence</artifactId>
    </dependency>
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-cache</artifactId>
    </dependency>

    <!-- Adhar Kit - Security -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-security</artifactId>
    </dependency>

    <!-- Adhar Kit - API -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-docs</artifactId>
    </dependency>
</dependencies>
```

### Event-Driven Microservice

```xml
<dependencies>
    <!-- Adhar Kit - Messaging -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-messaging</artifactId>
    </dependency>
    <dependency>
        <groupId>com.dbs</groupId>
        <artifactId>adhar-kit-mers</artifactId>
    </dependency>

    <!-- Adhar Kit - Core -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-commons</artifactId>
    </dependency>
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-logging</artifactId>
    </dependency>
</dependencies>
```

### AI-Powered Application

```xml
<dependencies>
    <!-- Adhar Kit - AI -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-ai</artifactId>
    </dependency>

    <!-- Adhar Kit - Analytics -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-analytics</artifactId>
    </dependency>

    <!-- Adhar Kit - Core -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-commons</artifactId>
    </dependency>
</dependencies>
```

### Cloud-Native Application

```xml
<dependencies>
    <!-- Adhar Kit - Cloud -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-kubernetes</artifactId>
    </dependency>
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-dapr</artifactId>
    </dependency>

    <!-- Adhar Kit - Observability -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-health</artifactId>
    </dependency>
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-metrics</artifactId>
    </dependency>
</dependencies>
```

### All-in-One Starter

```xml
<dependencies>
    <!-- Single dependency includes most common modules -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-starter</artifactId>
    </dependency>
</dependencies>
```

---

## 🔧 Advanced Configuration

### Override BOM Versions

If needed, you can override specific versions:

```xml
<properties>
    <!-- Override Resilience4j version -->
    <resilience4j.version>2.1.0</resilience4j.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.adhar.kit</groupId>
            <artifactId>adhar-kit-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Multiple BOMs

Combine Adhar Kit BOM with other BOMs:

```xml
<dependencyManagement>
    <dependencies>
        <!-- Adhar Kit BOM -->
        <dependency>
            <groupId>com.adhar.kit</groupId>
            <artifactId>adhar-kit-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- Spring Cloud BOM -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2024.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 📋 Module Dependencies Chart

```
adhar-kit-starter
├── adhar-kit-commons
├── adhar-kit-logging
├── adhar-kit-metrics
├── adhar-kit-resilience
├── adhar-kit-config
├── adhar-kit-cache
├── adhar-kit-persistence
└── adhar-kit-docs

adhar-kit-ai
├── adhar-kit-commons
└── spring-ai-*

adhar-kit-analytics
├── adhar-kit-commons
├── adhar-kit-persistence
└── posthog

adhar-kit-messaging
├── adhar-kit-commons
├── spring-kafka
└── spring-rabbit

adhar-kit-persistence
├── adhar-kit-commons
├── spring-data-jpa
└── querydsl

adhar-kit-cache
├── adhar-kit-commons
├── spring-data-redis
└── caffeine

adhar-kit-tracing
├── adhar-kit-commons
├── micrometer-tracing
└── opentelemetry
```

---

## 🎯 Best Practices

### 1. Always Use BOM

✅ **DO:** Import BOM in dependencyManagement  
❌ **DON'T:** Specify versions for Adhar Kit modules manually

```xml
<!-- ✅ GOOD -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.adhar.kit</groupId>
            <artifactId>adhar-kit-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-commons</artifactId>
        <!-- No version! -->
    </dependency>
</dependencies>

<!-- ❌ BAD -->
<dependencies>
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-commons</artifactId>
        <version>1.0.0</version> <!-- Don't specify version! -->
    </dependency>
</dependencies>
```

### 2. Keep BOM Version Current

Update BOM version to get all module updates:

```xml
<!-- Update from 1.0.0 to 1.1.0 -->
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-bom</artifactId>
    <version>1.1.0</version> <!-- Changed -->
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

### 3. Use Appropriate Scope

```xml
<dependencies>
    <!-- Runtime dependencies -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-logging</artifactId>
    </dependency>

    <!-- Test dependencies -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-test-commons</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Optional dependencies -->
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-ai</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

## 🚀 Migration Guide

### From Individual Module Versions

**Before:**
```xml
<dependencies>
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-commons</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-logging</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
</dependencies>
```

**After:**
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.adhar.kit</groupId>
            <artifactId>adhar-kit-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-commons</artifactId>
        <!-- Version managed by BOM -->
    </dependency>
    <dependency>
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-logging</artifactId>
        <!-- Version managed by BOM -->
    </dependency>
</dependencies>
```

---

## 📊 Version Compatibility

| Adhar Kit BOM | Java | Spring Boot | Spring Cloud |
|---------------|------|-------------|--------------|
| 1.0.0 | 25 | 3.4.0 | 2024.0.0 |

### Framework Compatibility

| Framework | Supported | Notes |
|-----------|-----------|-------|
| Spring Boot | ✅ | Primary framework |
| Quarkus | 🔄 | Adapters available |
| Micronaut | 🔄 | Adapters available |

---

## 🎊 Summary

**The Adhar Kit BOM provides:**

✅ **23 Production-Ready Modules** - Complete microservices toolkit  
✅ **Centralized Version Management** - No version conflicts  
✅ **Java 25 Compatible** - Latest LTS support  
✅ **Spring Boot 3.4+** - Modern Spring ecosystem  
✅ **Cloud Native** - Kubernetes, Dapr ready  
✅ **AI Ready** - Spring AI, LangChain4j  
✅ **Analytics Ready** - PostHog integration  
✅ **Full Observability** - Logging, metrics, tracing  
✅ **Production Tested** - All versions tested together  
✅ **Easy Upgrades** - Single version change  

**Perfect for:**
- 🏢 Enterprise microservices
- ☁️ Cloud-native applications  
- 🤖 AI-powered systems
- 📊 Data-intensive applications
- 🚀 High-performance services
- 🔐 Secure applications

---

## 📄 License

Copyright © 2025 Adhar Platform Team

