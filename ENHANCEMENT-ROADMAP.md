# Adhar Kit — Enterprise Enhancement Roadmap

Consolidated module-by-module review (July 2026). Each module was reviewed for purpose, maturity
and missing enterprise-microservices features. Findings are grouped into priority waves:
**Wave 1** fixes correctness gaps (documented features that are stubbed or broken),
**Wave 2** adds high-value enterprise features, **Wave 3** covers advanced/optional capabilities.

## Cross-cutting findings

1. **Inert annotations** — many modules declare annotations with no backing aspect/interceptor:
   cache (all 12), core (`@Retry`, `@Async`, `@Memoize`), commons (`@Idempotent`, `@ApiVersion`,
   `@PublishEvent`), config (`@RefreshConfig`), kubernetes (`@LeaderElected`, `@KubernetesAutoScale`),
   dapr (`@DaprActor`, `@DaprLock`, `@DaprSubscribe`).
2. **Disconnected facades** — `MessagingFacade`, `SecurityFacade`, `ConfigFacade`, `AiFacade` are
   stubs not wired to the real implementations that exist in the same module.
3. **READMEs overstate maturity** — several "Production Ready" claims are not backed by code.
4. **Missing inter-module wiring** — resilience events → metrics (`PlatformMetrics` already has the
   methods), circuit-breaker state → health indicator, persistence outbox → event-sourcing bus,
   tenant context (persistence) → cache partitioning.
5. **Package inconsistency** — cache and logging use `com.adhar.adharkit.*`; others `com.adhar.kit.*`.
6. **Starter module toggles are cosmetic** — `adhar.kit.modules.*` flags gate nothing.

## Per-module status and plan

### Foundation

| Module | Solid today | Wave 1 (fix) | Wave 2 (add) |
|---|---|---|---|
| **commons** | ApiResponse/ErrorResponse, exceptions, CloudEvents model, FrameworkDetector | Wire `AdharExceptionHandler` to a `@RestControllerAdvice`; `NotNullOrEmptyValidator` (constraint has no validator); Idempotency aspect + store for `@Idempotent` | `TenantContext`/`TenantContextFilter`, `CorrelationIdFilter` (headers already in `CommonConstants`), `@ApiVersion` interceptor with Deprecation/Sunset headers, `ErrorCatalog` with i18n |
| **core** | Result/Specification/RetryUtil/Memoizer/Lazy | `CoreAutoConfiguration` binding the dead `AdharCoreProperties`; aspects for `@Retry`/`@Async`/`@Memoize`; real `SnowflakeIdGenerator` | `Result.recover/fold`, `Try`/`Either`, context-propagating executor, `TypeConverter` SPI |
| **config** | ConfigManager (priority merge, listeners), PropertyEncryptor, sources SPI | **Link `ConfigFacade` to `ConfigManager`** (currently a dead HashMap); AES-GCM + PBKDF2 in `PropertyEncryptor` (currently ECB); apply decryption in `getProperty`; `@RefreshConfig` listener registration | Vault/Consul/K8s ConfigMap sources, feature-flag service with rollout %, config-change audit events, actuator endpoint |
| **starter** | Facade breadth, module registry | Make `adhar.kit.modules.*` toggles gate bean creation; fix hardcoded version; fix singleton race | `/actuator/adhar` module health endpoint, coordinated graceful shutdown, facade customizer SPI |

### Data

| Module | Solid today | Wave 1 (fix) | Wave 2 (add) |
|---|---|---|---|
| **cache** | CacheFacade/CacheManager (Caffeine), Redis/Kafka invalidation pieces | **Aspect/interceptor runtime for the 12 annotations** (all inert today), starting with `@Cacheable`/`@CacheEvict`/`@CachePut` | Single-flight stampede protection (`@CacheLock`), L1/L2 multi-level cache, Micrometer stats binder, refresh-ahead scheduler, tenant key partitioning |
| **persistence** | Auditing, optimistic locking, outbox, SoftDeleteRepository, SpecificationBuilder | Automatic soft-delete filtering (Hibernate `@SoftDelete` — plain `findAll()` currently returns deleted rows); real Hibernate multi-tenancy SPI wiring (resolver is a stub) | Outbox retry/dead-letter + `SKIP LOCKED` + Kafka relay, optimistic-lock retry template, Envers revision history, domain-event→outbox bridge, N+1 detector |
| **event-sourcing** | Event store w/ optimistic concurrency, aggregate replay, CloudEvents bus | **Snapshotting** (`snapshot-interval` config exists, no `SnapshotStore`) | Projections + checkpoints, event upcasting, typed event registry, Kafka event bus, catch-up subscriptions, saga manager |
| **batch** | Scheduler (real), partitioner, retry/skip builders, metrics | Wire `BatchProperties.maxRetries/retryOnFailure` into `RetryableStepBuilder`; enforce `max-concurrent-jobs` | `BatchOperator` (restart/stop), step/chunk/skip listeners feeding metrics, ShedLock for multi-instance cron, more readers/writers (JDBC/JSON/Kafka), failure notifications |

### Communication

| Module | Solid today | Wave 1 (fix) | Wave 2 (add) |
|---|---|---|---|
| **messaging** | Kafka/Rabbit publishers+listeners, CloudEventAdapter | **Wire `MessagingFacade` to the real Kafka/Rabbit beans** (facade is a stub); auto-config registering them | DLQ routing, retry/backoff (config already exists), idempotent consumer store, transactional outbox, request-reply, publish/consume metrics |
| **notification** | Channel abstraction, retry, templates, CloudEvent emission | Replace blocking-sleep retry with scheduled retry queue; real SMS provider | Preference/opt-out service, rate limiting, persistent history, HTML templating + localization, digest/batching, idempotency keys |
| **grpc** | AdharGrpcServer, client factory, LoggingInterceptor | **Fix `RetryInterceptor`** (never actually retries); apply configured `default-timeout`; real TLS/mTLS from existing props | Auth interceptor, metrics + tracing interceptors, `@GrpcClient`/`@GrpcService` bean processing, concurrency-limit interceptor |
| **graphql** | Pagination, validation, security interceptor, scalar | **Move complexity/depth checks pre-execution** (currently run after the query executes); integrate `DataLoaderRegistrar` with real graphql-java `DataLoaderRegistry` | Persisted queries (APQ), query allow-listing, per-client cost rate limiting, field-level authorization, resolver tracing |

### Operations

| Module | Solid today | Wave 1 (fix) | Wave 2 (add) |
|---|---|---|---|
| **security** | OAuth2 resource server, TokenRefreshService (rotation+family), RateLimitingFilter, audit logger | **Implement `SpringSecurityAdapter`** (facade is a stub); pluggable `RefreshTokenStore` (in-memory only) | `@RequiresRole`/`@RequiresPermission` aspects, Redis rate limiting, API-key filter, key rotation/JWKS, audit sink SPI, token relay |
| **resilience** | Annotation set, registries, metrics read-out | **Honor annotation attributes** (`maxAttempts` etc. silently ignored); ordered composition of stacked annotations | Event listeners → `PlatformMetrics`, actuator endpoint + health contributor, reactive support, fallback cache, chaos hooks |
| **health** | HealthRegistry (parallel+timeout), 7 indicators | Complete facade adapters (throws for Spring/Quarkus/Micronaut); result caching/TTL | Groups (liveness/readiness/startup) + weighted aggregation, readiness gate for graceful shutdown, downstream service checks, cert-expiry/threadpool/CB indicators, health event stream |
| **metrics** | PlatformMetrics, EnhancedMetricsAspect, JVM/K8s collectors | **Fix broken gauge** (`GaugeValue.setValue` never called); fill empty `@Histogram` SLO buckets; real HTTP status capture (hardcoded 200/500) | SLO/error-budget recorder, exemplars/trace correlation, business KPI annotation, K8s resource polling, cardinality limiter, Grafana dashboards |

### Observability & AI

| Module | Solid today | Wave 1 (fix) | Wave 2 (add) |
|---|---|---|---|
| **tracing** | TracingAspect (6 annotations), config surface | **Real baggage** (current impl is a local map, not W3C baggage); fix double-`proceed()` bug in `handleAsyncSpan`; implement no-op `wrapWithTraceContext` | `@SpanTag` param annotation, MDC correlation filter, web server-span filter, tail sampling, span→RED metrics bridge |
| **perf-profiler** | Facade/registry/aspect/endpoint coherent | **Fix unbounded duration list** (memory leak; use HdrHistogram/rolling window) | JFR continuous profiling, flame graph export, sampling/overhead guards, slow-call alert events, GC/thread-contention analytics |
| **analytics** | Annotation surface, EventAggregator, PostHog + Kafka paths | **Real batching + flush** (config exists, events sent one-by-one); fix endpoint routing bug (identify/alias/group all hit `/capture`) | Funnel/retention computation, flag cache TTL + local eval, retry/offline buffer, consent/PII scrubber, streaming report export |
| **ai** | Metrics collector, rate limiter, security validator, Spring-AI-wired `AiServiceImpl` | **Wire `AiFacade` to real providers** (annotation path throws today); invoke rate limiter + guardrails from the service; real streaming | Token/cost tracking end-to-end, prompt template registry, semantic response cache, guardrail chain, function-calling loop |

### Platform & tooling

| Module | Solid today | Wave 1 (fix) | Wave 2 (add) |
|---|---|---|---|
| **kubernetes** | fabric8 client wrapper, deployment ops, utils | Runtime for `@LeaderElected` (fabric8 LeaderElector), ConfigMap/Secret watch (informers) | HPA reconciler for `@KubernetesAutoScale`, graceful-shutdown/pre-stop manager, probe contributor, cached service discovery |
| **dapr** | Client wrapper, state/pubsub/invoke/secrets | `@DaprSubscribe` registrar (`/dapr/subscribe` endpoint); resilience wrapper on invocation | Actor runtime, distributed lock (SDK bump), outbox publisher, typed state repository, workflow facade |
| **docs** | SpringDoc customizers, annotation processor | Implement no-op builder methods (`addCommonHeaders/Responses/Examples`); Jackson-based spec parsing | Spec export at build time, breaking-change diff (CI gate), RFC 9457 problem+json alignment, AsyncAPI for topics, grouped versioning |
| **rewrite** | Recipe catalog, rewrite.yml generation | In-process recipe runner (currently prints a Maven command) | Real `Recipe` subclasses with visitors, adhar-kit version migration recipe, impact report, catalog validator |
| **maven-plugin** | Version/release managers, JavaPoet generators | Implement stubbed `generateEntity()` and test generation; semver-aware tag sort | Dependency/CVE check mojo, BOM alignment mojo, full microservice scaffold, ArchUnit-style architecture rules, ADR mojo |
| **test-commons** | Container facade + 4 containers, base classes | Base classes wiring Kafka/Mongo/Redis containers (only Postgres wired); unify duplicate container mechanisms | WireMock support, contract testing base, Toxiproxy chaos, LocalStack/Dapr containers, database seeding, JUnit extension |

### Already enhanced
- **logging** — AppLogEvent pipeline, business/operation/audit/batch/performance/API logging,
  masking strategies, 5 new annotations, interceptor, logger injection (July 2026, this branch).

## Execution order

1. **Wave 1a (foundation correctness):** commons, core, config, cache — everything else builds on these.
2. **Wave 1b (ops correctness):** resilience, metrics, security, health + inter-module wiring.
3. **Wave 1c (comms/data correctness):** messaging, grpc, graphql, persistence, event-sourcing, analytics, tracing, perf-profiler, ai.
4. **Wave 2:** feature additions per tables above.
5. **Wave 3:** platform/tooling (kubernetes, dapr, docs, rewrite, maven-plugin, test-commons).
