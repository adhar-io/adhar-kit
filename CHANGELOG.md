# Changelog

All notable changes to the Adhar Kit project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **adhar-kit-tracing** — outbound calls are traced and carry the trace context. The kit now
  supplies the `Propagator` (and depends on `spring-boot-micrometer-tracing`), so Boot's tracing
  observation handlers record into the kit's SDK: `RestClient`/`RestTemplate`/`WebClient` calls,
  Kafka sends and `@KafkaListener` receives (observation switched on by default), RabbitMQ,
  Lettuce commands and JDBC queries (datasource-micrometer, sanitized SQL, no parameter values)
  are spans, and W3C `traceparent` is injected/extracted. Incoming WebFlux requests get SERVER
  spans too; the servlet `TracingServerSpanFilter` defers to Boot's server observation instead
  of nesting a duplicate SERVER span.

### Fixed

- **adhar-kit-metrics / adhar-kit-tracing** — inside a pod, a hard-coded
  `deployment.environment=local` in `OTEL_RESOURCE_ATTRIBUTES` (older scaffolds' base Rollout)
  no longer labels every environment `local`: the environment comes from `ADHAR_ENVIRONMENT`,
  else the namespace, for metrics, logs and the trace resource.

- **adhar-kit-cache** — Redis/Valkey caching now works as a Spring Boot auto-configuration:
  it reuses Spring Boot's Redis connection (`spring.data.redis.*`), registers before Boot's
  cache auto-configuration, stores typed JSON (a cache hit used to come back as a
  `LinkedHashMap` and fail with `ClassCastException`), and becomes the `@MultiLevelCache` L2.
- **adhar-kit-cache** — Kafka cache synchronization is opt-in (`adhar.cache.kafka.enabled`).
  Kafka on the classpath no longer replaces the application's Kafka beans (its
  `KafkaTemplate` and listeners), each instance consumes in its own group so every instance
  is notified, remote operations are no longer re-published (instances echoed them
  indefinitely), topics are actually created, and bootstrap servers default to
  `spring.kafka.bootstrap-servers`.
- **adhar-kit-cache** — no longer ships an `application.properties` (it set
  `spring.application.name=adhar-cache-starter` in applications without their own).
- **adhar-kit-bom** — `adhar-kit-cache` was listed under the wrong groupId.
- **adhar-kit-tracing** — no longer uses semconv's removed `ResourceAttributes`; every
  service failed at startup with `NoClassDefFoundError` under Spring Boot 4.1.
- **adhar-kit-persistence** — a service without a database (no `spring.datasource.url`)
  starts: JDBC/JPA auto-configuration is switched off with a log line. The facade no longer
  fails startup choosing between itself and the real `PersistenceService`.
- **adhar-kit-event-sourcing** — the event store defaults to in-memory; `jpa` is chosen
  explicitly, falls back when there is no `EntityManagerFactory`, and registers its entities.
- **adhar-kit-ai** — model providers (OpenAI, Ollama, Anthropic) are optional; OpenAI's
  auto-configuration refused to start any service without an API key.
- **adhar-kit-security** — one filter chain (the OAuth2 chain displaced the main one), the
  JWT decoder only when an issuer/JWK set is configured, health/info endpoints open for
  Kubernetes probes, CSRF off by default for the stateless API, and a single CORS source
  that does not combine credentials with the `*` origin.

## [1.0.0] - 2026-04-04

### Platform

- Java 25 baseline with records, sealed interfaces, virtual threads, pattern matching
- Spring Boot 4.1.0 with Spring Framework 7.1.0
- Spring Cloud 2025.0.0
- Quarkus 3.21+ and Micronaut 4.8+ framework support
- 28 production-ready modules with unified AdharFacade entry point
- 40+ convenience shortcuts on AdharFacade for common operations
- CloudEvents 1.0 specification across all event-producing modules
- Auto-metrics collection (JVM, persistence, cache, messaging, HTTP) via PlatformMetrics
- Apache License 2.0

### Core Modules (TIER-1)

- **adhar-kit-commons** - Framework detection, base utilities, AdharCloudEvent record
- **adhar-kit-core** - ID generation (UUID/short/snowflake), JSON, retry with backoff, async execution
- **adhar-kit-resilience** - Circuit breaker, retry, rate limiter, bulkhead, time limiter via Resilience4j 2.3.0
- **adhar-kit-metrics** - Micrometer 1.15 metrics with PlatformMetrics, @Measured annotation, JVM auto-collection
- **adhar-kit-tracing** - OpenTelemetry 1.51 distributed tracing with Zipkin and OTLP exporters
- **adhar-kit-logging** - Structured JSON logging with MDC context, Logstash encoder 8.1
- **adhar-kit-cache** - Caffeine 3.2 in-memory cache with Redis and Kafka-based distributed sync

### Integration Modules (TIER-2)

- **adhar-kit-health** - Health checks (liveness, readiness) with Redis, Kafka, MongoDB, Elasticsearch, gRPC indicators
- **adhar-kit-test-commons** - TestContainers 1.21 helpers for Postgres, MySQL, MongoDB, Kafka, RabbitMQ, Vault
- **adhar-kit-messaging** - Kafka and RabbitMQ messaging with CloudEvents support
- **adhar-kit-docs** - OpenAPI 3.0 documentation with SpringDoc 2.8.6, global headers, security schemes
- **adhar-kit-grpc** - gRPC 1.73 with Protobuf 4.29, auto-service discovery, lifecycle management
- **adhar-kit-graphql** - GraphQL with schema registry, Relay cursor pagination, query complexity limits, DataLoader, security interceptor

### Enterprise Modules (TIER-3)

- **adhar-kit-persistence** - JPA with auditing, multi-tenancy (schema/discriminator/database), soft delete, SpecificationBuilder, transactional outbox, PersistenceMetricsCollector, pagination, bulk operations
- **adhar-kit-security** - OAuth2/OIDC, JWT with JJWT 0.12.6, CORS, CSRF, rate limiting, security audit logging
- **adhar-kit-config** - Spring Cloud Config, Vault, Jasypt encryption, dynamic refresh
- **adhar-kit-ai** - Spring AI with OpenAI, Azure OpenAI, Ollama; chat, embeddings, RAG, image generation, function calling
- **adhar-kit-analytics** - PostHog integration, event tracking, feature flags, CloudEvent publishing
- **adhar-kit-kubernetes** - Fabric8 7.3 client for ConfigMaps, Secrets, deployment scaling, pod listing, service discovery
- **adhar-kit-dapr** - Dapr SDK 1.14 for state, pub/sub, service invocation, bindings, actors, secrets, configuration
- **adhar-kit-batch** - Spring Batch 6 with job scheduling, CSV/JPA readers/writers, BatchMetrics, range partitioning
- **adhar-kit-notification** - Multi-channel (email, webhook, in-app, SMS), templates, retry with backoff, notification history
- **adhar-kit-event-sourcing** - Event store (JPA/in-memory), AggregateRoot, domain events, EventBus, CloudEvent envelopes
- **adhar-kit-perf-profiler** - @Profiled annotation, ProfilingRegistry, MemoryProfiler, Actuator endpoint, hotspot detection
- **adhar-kit-rewrite** - OpenRewrite 8.78 with 22 recipe sets for Java, Spring Boot, Quarkus, Micronaut, Jakarta EE, cross-framework migration
- **adhar-kit-starter** - Unified AdharFacade with all 28 module accessors and convenience shortcuts

### Build Infrastructure

- **adhar-kit-bom** - Bill of Materials for consistent dependency management
- **adhar-kit-parent** - Parent POM with JaCoCo 80% coverage enforcement, Maven Enforcer (Java 25+, Maven 3.8+)
- **adhar-kit-maven-plugin** - Release management, changelog generation, code generation (DTO, Controller, Repository)

### Dependencies

| Library | Version |
|---------|---------|
| Spring Boot | 4.1.0 |
| Spring Cloud | 2025.0.0 |
| OpenTelemetry | 1.51.0 |
| Resilience4j | 2.3.0 |
| Micrometer | 1.15.0 |
| Flyway | 11.8.2 |
| gRPC | 1.73.0 |
| Fabric8 K8s Client | 7.3.1 |
| Dapr SDK | 1.14.1 |
| Spring AI | 1.0.0-SNAPSHOT |
| OpenRewrite | 8.78.1 |
| TestContainers | 1.21.3 |
| Mockito | 5.18.0 |

[Unreleased]: https://github.com/adhar-io/adhar-kit/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/adhar-io/adhar-kit/releases/tag/v1.0.0
