# Contributing to Adhar Kit

Thank you for considering contributing to Adhar Kit. This guide explains how to get started.

## Prerequisites

- Java 25+
- Maven 3.9+
- Git

## Getting Started

```bash
# Clone
git clone https://github.com/adhar-io/adhar-kit.git
cd adhar-kit

# Build (skip tests for speed)
mvn install -DskipTests -Djacoco.skip=true

# Build with tests
mvn verify

# Build a single module
mvn compile -pl adhar-kit-persistence -am
```

## Project Structure

```
adhar-kit/
  adhar-kit-bom/           # Bill of Materials (version management)
  adhar-kit-parent/        # Parent POM (build config, plugins)
  adhar-kit-commons/       # Shared utilities, AdharCloudEvent
  adhar-kit-core/          # ID gen, JSON, retry, async
  adhar-kit-resilience/    # Circuit breaker, retry, rate limiter
  adhar-kit-metrics/       # Micrometer metrics, PlatformMetrics
  adhar-kit-tracing/       # OpenTelemetry distributed tracing
  adhar-kit-logging/       # Structured logging with MDC
  adhar-kit-cache/         # Caffeine/Redis caching
  adhar-kit-health/        # Health indicators
  adhar-kit-test-commons/  # TestContainers helpers
  adhar-kit-messaging/     # Kafka/RabbitMQ with CloudEvents
  adhar-kit-docs/          # OpenAPI documentation
  adhar-kit-grpc/          # gRPC services
  adhar-kit-graphql/       # GraphQL API support
  adhar-kit-persistence/   # JPA with auditing, multi-tenancy
  adhar-kit-security/      # OAuth2/JWT security
  adhar-kit-config/        # Configuration management
  adhar-kit-ai/            # AI/LLM integration
  adhar-kit-analytics/     # Event tracking, feature flags
  adhar-kit-kubernetes/    # Kubernetes integration
  adhar-kit-dapr/          # Dapr runtime integration
  adhar-kit-batch/         # Spring Batch processing
  adhar-kit-notification/  # Multi-channel notifications
  adhar-kit-event-sourcing/# Event sourcing and CQRS
  adhar-kit-perf-profiler/ # Performance profiling
  adhar-kit-rewrite/       # OpenRewrite code modernization
  adhar-kit-starter/       # Unified AdharFacade
  adhar-kit-maven-plugin/  # Build tooling
```

## Development Guidelines

### Code Style

- Use Java 25 features: records, sealed interfaces, pattern matching, text blocks
- Follow existing package naming: `com.adhar.kit.<module>.*`
- Use `@Slf4j` for logging (Lombok)
- Every public class needs JavaDoc with `@author` and `@since`
- No emojis in source code

### Architecture Patterns

- **Facade pattern** - Each module exposes a `*Facade` class as the primary API
- **Singleton + Spring bean** - Facades support both `getInstance()` and DI
- **AutoConfiguration** - Every module has `@AutoConfiguration` with `@ConditionalOnProperty`
- **Optional dependencies** - Framework-specific deps are marked `<optional>true</optional>`
- **CloudEvents** - All events follow CloudEvents 1.0 specification via `AdharCloudEvent`

### Adding a New Module

1. Create directory `adhar-kit-<name>/`
2. Create `pom.xml` with parent `adhar-kit-parent`
3. Create facade class at `com.adhar.kit.<name>.<Name>Facade`
4. Create `<Name>AutoConfiguration` with `@AutoConfiguration`
5. Create `<Name>Properties` with `@ConfigurationProperties(prefix = "adhar.<name>")`
6. Register in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
7. Add module to root `pom.xml` `<modules>` section
8. Add to `adhar-kit-bom/pom.xml` dependency management
9. Add to `adhar-kit-starter/pom.xml` dependencies
10. Add accessor to `AdharFacade` (lazy initialization)
11. Add to `AdharKitProperties.Modules` and `AdharKitAutoConfiguration` registry
12. Create `README.md` for the module
13. Write unit tests (target 80%+ coverage)
14. Update root `CHANGELOG.md`

### Testing

- JUnit 5 + AssertJ + Mockito
- Target 80% instruction coverage (enforced by JaCoCo)
- Test file pattern: `*Test.java` or `*Tests.java`
- Use TestContainers for integration tests
- Run module tests: `mvn test -pl adhar-kit-<module>`

### Commit Messages

Follow conventional commits:

```
feat(persistence): add SpecificationBuilder for type-safe queries
fix(cache): handle null keys in CacheFacade.get()
docs(readme): update module count to 28
refactor(metrics): extract PlatformMetrics from MetricsFacade
test(notification): add NotificationRetryHandler tests
chore(deps): update Spring Boot to 4.1.0
```

## Pull Request Process

1. Fork and create a feature branch from `main`
2. Make your changes with tests
3. Ensure `mvn verify` passes
4. Update CHANGELOG.md under `[Unreleased]`
5. Submit PR with description of changes

## Release Process

Releases are managed via Maven Release Plugin. See [RELEASING.md](RELEASING.md) for details.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
