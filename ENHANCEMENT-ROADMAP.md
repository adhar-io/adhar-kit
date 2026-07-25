# Adhar Kit — Enterprise Enhancement Roadmap

Status update **2026-07-25**: every item was verified against the code (module-by-module audit).
✅ = implemented and verified in code. Items under *Open* are the remaining work, currently being
implemented (this pass); this file will be updated again once they land.

## Per-module status

### Foundation

- **commons** — ✅ `AdharExceptionHandler` wired via `SpringGlobalExceptionHandler` (`@RestControllerAdvice`), `NotNullOrEmptyValidator`, idempotency aspect + TTL store, `TenantContext`/`TenantContextFilter`/`CorrelationIdFilter`, `@ApiVersion` interceptor with Deprecation/Sunset headers.
  *Open:* ErrorCatalog with i18n.
- **core** — ✅ `CoreAutoConfiguration` binding `AdharCoreProperties`, aspects for `@Retry`/`@Async`/`@Memoize`, real `SnowflakeIdGenerator`, `Result.recover/fold`.
  *Open:* `Try`/`Either` types, TypeConverter SPI (currently static-only), pluggable context propagation beyond MDC.
- **config** — ✅ `ConfigFacade` → `ConfigManager` delegation, AES-GCM + PBKDF2 encryption (ECB legacy decrypt-only), decryption in `getProperty`, `@RefreshConfig` listener registration.
  *Open:* Vault/Consul/K8s ConfigMap sources (Vault is a log-only stub), feature-flag service with rollout %, config-change audit events, actuator endpoint.
- **starter** — ✅ `adhar.kit.modules.*` toggles gate facade access, build-derived version, singleton race fixed (volatile + DCL), `/actuator/adhar` endpoint, facade customizer SPI.
  *Open:* coordinated graceful shutdown.

### Data

- **cache** — ✅ aspect runtime for `@Cacheable`/`@CachePut`/`@CacheEvict`, single-flight `@CacheLock`, L1/L2 `MultiLevelCacheService`, Micrometer `CacheMetricsBinder`, refresh-ahead scheduler.
  *Open:* aspects for `@CacheCircuitBreaker`/`@CacheMonitor`/`@CacheRateLimit`/`@CacheWarmup`/`@CachePartition`, tenant key partitioning.
- **persistence** — ✅ automatic soft-delete filtering (`@SQLRestriction`), real Hibernate multi-tenancy SPI wiring, outbox retry/dead-letter + `SKIP LOCKED`, `OptimisticLockRetryTemplate`, domain-event→outbox bridge.
  *Open:* Kafka outbox relay (SPI exists, only ApplicationEvent impl), Envers revision history, N+1 detector.
- **event-sourcing** — ✅ snapshotting (interval-driven `JpaSnapshotStore`), projections + checkpoints, event upcasting, typed event registry.
  *Open:* Kafka event bus, catch-up subscriptions (replay exists, no catch-up-then-live primitive), saga manager.
- **batch** — ✅ scheduler/partitioner/retry-skip builders, `max-concurrent-jobs` enforced.
  *Open:* wire `BatchProperties.maxRetries`/`retryOnFailure` into `RetryableStepBuilder` (currently inert), `BatchOperator` (restart/stop), listeners feeding `BatchMetrics`, ShedLock for multi-instance cron, JDBC/JSON/Kafka readers/writers, failure notifications.

### Communication

- **messaging** — ✅ facade wired to real Kafka/Rabbit beans, DLQ routing, retry/backoff, idempotent consumer store, publish/consume metrics.
  *Open:* transactional outbox, request-reply (`sendAndReceive` is a stub returning null).
- **notification** — ⚠️ Wave 1 still open: retry uses blocking `Thread.sleep`; SMS channel is a logging stub.
  *Open:* scheduled retry queue, real SMS provider, preference/opt-out service, rate limiting, persistent history (in-memory only), HTML templating + localization, digest/batching, idempotency keys.
- **grpc** — ✅ real retry (grpc-java retry policy), default-timeout deadlines, TLS/mTLS, auth + metrics interceptors, `@GrpcClient`/`@GrpcService` bean processing.
  *Open:* tracing interceptor, concurrency-limit interceptor.
- **graphql** — ✅ pre-execution complexity/depth checks, real `DataLoaderRegistry` integration, persisted queries (APQ).
  *Open:* query allow-listing, per-client cost rate limiting, field-level authorization, resolver tracing.

### Operations

- **security** — ✅ `SpringSecurityAdapter`, `@RequiresRole`/`@RequiresPermission` aspects, API-key filter, audit sink SPI, refresh-token rotation + family revocation, JWKS consumption.
  *Open:* Redis `RefreshTokenStore` (SPI exists, in-memory only), Redis rate limiting, signing-key rotation + JWKS publishing, token relay.
- **resilience** — ✅ annotation attributes honored, ordered composition of stacked annotations, actuator endpoint.
  *Open:* events → metrics `PlatformMetrics` bridge, circuit-breaker health contributor, reactive support, fallback cache, chaos hooks.
- **health** — ✅ Spring/Quarkus/Micronaut adapters complete, result caching/TTL, liveness/readiness/startup groups, readiness gate for graceful shutdown, downstream + cert-expiry + threadpool indicators.
  *Open:* weighted aggregation, circuit-breaker indicator, health event stream (currently callback listener only).
- **metrics** — ✅ gauge fix, `@Histogram` SLO buckets, real HTTP status capture, SLO/error-budget recorder, business KPI annotation, cardinality limiter.
  *Open:* exemplars/trace correlation, scheduled K8s resource polling, Grafana dashboards.

### Observability & AI

- **tracing** — ✅ real W3C baggage, async double-`proceed()` fix, `wrapWithTraceContext`, `@SpanTag`, MDC correlation filter.
  *Open:* web server-span filter, tail sampling, span→RED metrics bridge.
- **perf-profiler** — ✅ bounded HdrHistogram + rolling windows, sampling/overhead guards, slow-call alert events, GC stats.
  *Open:* JFR continuous profiling, flame graph export, thread-contention analytics.
- **analytics** — ✅ real batching + flush to `/batch/`, endpoint routing fixed (`$identify`/`$create_alias`/`$groupidentify`), flag cache TTL, consent gateway + PII scrubber, streaming report export.
  *Open:* funnel/retention computation, local flag evaluation, retry/offline buffer.
- **ai** — ✅ service path real (guardrails + rate limiter invoked, Flux streaming, token/cost tracking).
  *Open:* wire `AiFacade`'s provider (DefaultAiProvider still throws — bypassed, not wired), prompt template registry, semantic response cache (exact-hash only), pluggable guardrail chain, function-calling loop.

### Platform & tooling

- **kubernetes** — ✅ fabric8 `LeaderElector` runtime, ConfigMap/Secret informers, HPA reconciler, graceful-shutdown handler.
  *Open:* probe HealthIndicator contribution, cached service discovery.
- **dapr** — ✅ `/dapr/subscribe` registrar + dispatch, resilience-wrapped invocation, typed state repository.
  *Open:* actor runtime, distributed lock, outbox publisher, workflow facade (actors/lock/workflow throw `UnsupportedOperationException`).
- **docs** — ✅ common headers/responses/examples customizers, Jackson spec parsing, spec export (json+yaml), RFC 9457 problem+json, grouped versioning.
  *Open:* breaking-change diff (CI gate), AsyncAPI generation.
- **rewrite** — ✅ in-process recipe runner, impact report, catalog validator, 2 visitor-based adhar recipes.
  *Open:* version-migration recipe, visitor-based migration recipes (most are YAML-string generators).
- **maven-plugin** — ✅ entity + integration-test generators, semver-aware tag sort, architecture rules, dependency report (convergence + BOM coverage).
  *Open:* CVE check mojo, BOM alignment mojo, microservice scaffold, ADR mojo.
- **test-commons** — ✅ Kafka/Mongo/Redis/composite base classes, unified `TestContainerRegistry`, WireMock support, database seeding.
  *Open:* contract-testing base, Toxiproxy chaos, LocalStack/Dapr containers, unifying JUnit 5 extension.

### Already enhanced
- **logging** — AppLogEvent pipeline, business/operation/audit/batch/performance/API logging, masking strategies, 5 annotations, interceptor, logger injection.

## Cross-cutting open items

1. **Package inconsistency (broader than first noted):** `com.adhar.adharkit.*` is used in cache, logging, messaging, perf-profiler, security, and starter; other modules use `com.adhar.kit.*`. Renaming is a breaking change — decide and schedule separately (an opt-in OpenRewrite migration recipe is planned in adhar-kit-rewrite).
2. **README maturity claims** — re-verify per-module READMEs once the open items above land.
