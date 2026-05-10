# 📦 Adhar Kit BOM (Bill of Materials)

**Centralized dependency management for the Adhar Kit Enterprise Platform**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.17+-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.8+-blue.svg)](https://micronaut.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

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
                <version>0.1.0-SNAPSHOT</version>
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
| **Event Sourcing** | `adhar-kit-event-sourcing` | Event sourcing patterns, aggregates, and replay support |

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

### Platform Extensions (5 modules)

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| **Batch** | `adhar-kit-batch` | Batch orchestration, retries, and scheduling patterns |
| **Notification** | `adhar-kit-notification` | Notification channels and delivery abstractions |
| **Perf Profiler** | `adhar-kit-perf-profiler` | Performance profiling and runtime diagnostics |
| **Rewrite** | `adhar-kit-rewrite` | Automated code modernization and migration recipes |
| **Maven Plugin** | `adhar-kit-maven-plugin` | Build tooling for release and governance workflows |

**Total: 28 Production-Ready Modules (excluding BOM + parent POM)**

---

## 💡 Usage Examples

### Basic Spring Boot Application

```xml
<project>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.2</version>
    </parent>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.adhar.kit</groupId>
                <artifactId>adhar-kit-bom</artifactId>
                <version>0.1.0-SNAPSHOT</version>
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
        <groupId>com.adhar.kit</groupId>
        <artifactId>adhar-kit-event-sourcing</artifactId>
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
            <version>0.1.0-SNAPSHOT</version>
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
            <version>0.1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- Spring Cloud BOM -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
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
            <version>0.1.0-SNAPSHOT</version>
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
        <version>0.1.0-SNAPSHOT</version> <!-- Don't specify version! -->
    </dependency>
</dependencies>
```

### 2. Keep BOM Version Current

Update BOM version to get all module updates:

```xml
<!-- Example: bump from an older snapshot to the latest snapshot -->
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-bom</artifactId>
    <version>0.1.0-SNAPSHOT</version> <!-- Changed -->
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

## 🎊 Summary

**The Adhar Kit BOM provides:**

✅ **28 Production-Ready Modules** - Complete microservices toolkit  
✅ **Centralized Version Management** - No version conflicts  
✅ **Java 25 Compatible** - Latest Java support  
✅ **Spring Boot 4.0+** - Modern Spring ecosystem  
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

