# 🔧 Adhar Kit Maven Plugin - Enterprise Build Tooling

**Maven plugin for versioning, release management, and code generation**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 0.1.0-SNAPSHOT  
**Status:** ✅ Production Ready

---

## 📖 Overview

Enterprise Maven plugin for microservices development, providing spec-first OpenAPI code generation, versioning, release management, code generation, and standardization features.

## 🎯 Features

### 📐 Spec-first OpenAPI (`adhar:openapi`, `adhar:openapi-lint`, `adhar:openapi-diff`, `adhar:catalog`)

A service's API is written first, as one OpenAPI 3.0.3 document per major version at the
repository root, and the code is generated from it:

```
api/
├── v1/openapi.yaml      # paths /api/v1/..., info.version 1.x.y
└── v2/openapi.yaml      # paths /api/v2/..., info.version 2.x.y
```

`adhar-kit-parent` binds the goals, so a service only declares the plugin:

```xml
<plugin>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-maven-plugin</artifactId>
    <configuration>
        <basePackage>com.example.orders</basePackage>   <!-- default: ${project.groupId} -->
    </configuration>
</plugin>
```

- **`openapi-lint`** (`validate`) fails the build when a spec breaks the API contract.
- **`openapi`** (`generate-sources`) generates `<basePackage>.api.v<major>` interfaces and
  `<basePackage>.api.v<major>.model` models into `target/generated-sources/openapi` (added as a
  source root) and copies each spec into the jar as `api/v<major>/openapi.yaml`, where
  `adhar-kit-commons` serves it at `GET /api/v<major>/openapi.yaml`. Generation is skipped
  when neither the specs nor the options changed, and the same spec always produces the same
  bytes. Without a spec both goals do nothing.

The controller implements the generated interface; a missing operation is a compile error,
and the spec's constraints (`minLength`, `minimum`, `required`) arrive as Bean Validation:

```java
@RestController
public class OrdersController implements com.example.orders.api.v1.OrdersApi {
    @Override
    public ResponseEntity<Order> getOrder(Long orderId) { ... }
}
```

| Preset | Generator | Generates |
|---|---|---|
| `spring` (default) | openapi-generator `spring` | Spring MVC interfaces for Spring Boot 4: Jackson 3, Jakarta, JSpecify, Bean Validation, no default methods, no springdoc/swagger annotations. Needs `spring-boot-starter-webmvc` and `spring-boot-starter-validation` (adhar-kit-core brings both) |
| `jaxrs` | `jaxrs-spec`, library `quarkus` | `jakarta.ws.rs` interfaces for Quarkus REST (verified against Quarkus 3.15) |
| `spring-client` | `spring`, library `spring-http-interface` | `@HttpExchange` clients for calling another service, e.g. through the Dapr sidecar (`http://localhost:3500/v1.0/invoke/<app-id>/method`); not linted, not shipped |

Other specs - typically another service's API to call - are configured explicitly; the major
comes from a `v<major>` directory, else from `info.version`:

```xml
<configuration>
    <specs>
        <spec>
            <file>${project.basedir}/api-clients/payments/openapi.yaml</file>
            <preset>spring-client</preset>   <!-- default package: <basePackage>.client.v<major> -->
        </spec>
    </specs>
    <additionalProperties>                     <!-- overrides a preset option; rarely needed -->
        <useOptional>true</useOptional>
    </additionalProperties>
</configuration>
```

| Property | Default | |
|---|---|---|
| `adhar.openapi.skip` | `false` | skip every OpenAPI goal |
| `adhar.openapi.apiDirectory` | `${project.basedir}/api` | where `v<major>/openapi.yaml` is found |
| `adhar.openapi.discover` | `true` | find specs by convention |
| `adhar.openapi.preset` | `spring` | preset of discovered specs |
| `adhar.openapi.basePackage` | `${project.groupId}` | package prefix (made a valid Java package) |
| `adhar.openapi.shipSpecs` | `true` | copy the service's specs into the jar |
| `adhar.openapi.lint.skip` / `.failOnWarnings` | `false` | |

**The API contract `openapi-lint` checks** (errors fail the build):

| Rule | |
|---|---|
| `openapi-version` | `openapi: 3.0.x` - every generator in use supports 3.0, not all support 3.1 |
| `info-version` | a semantic `info.version` whose major is the directory's (`api/v2` → `2.x.y`) |
| `path-prefix` | every path starts with `/api/v<major>` |
| `servers` | no server URL with a base path (the paths are already absolute) |
| `operation-id`, `operation-tags` | a unique operationId (the method) and a tag (the interface, `<Tag>Api`) on every operation |
| `responses`, `problem-json` | responses declared; every `4xx`, `5xx` and `default` response is `application/problem+json` |
| `external-ref`, `unresolved-ref` | every `$ref` stays in the document and resolves - the shipped file is the whole contract |
| `inline-schema` (warning) | bodies `$ref` a schema under `components.schemas` |

**`openapi-diff`** (not bound; a CI step) fails when a spec breaks the clients of its major -
a removed operation, a new required request field, a changed type. Such a change belongs in
a new `api/v<major+1>/openapi.yaml`, served alongside. The comparison is openapi-diff's, so a
new response field is compatible and a new required request field is not; every change is
written to `target/openapi-diff.md`:

```bash
mvn adhar:openapi-diff -Dadhar.openapi.diff.against=origin/main      # a git revision
mvn adhar:openapi-diff -Dadhar.openapi.diff.baseline=1.4.0           # this project's released jar
mvn adhar:openapi-diff -Dadhar.openapi.diff.baseline=com.example:orders:1.4.0
mvn adhar:openapi-diff -Dadhar.openapi.diff.against=v1.4.0 -Dadhar.openapi.diff.requireVersionBump=true
```

**`catalog`** (not bound) declares the APIs in `catalog-info.yaml`: one `kind: API` entity per
major (`<component>-v<major>`, `spec.definition.$text: ./api/v<major>/openapi.yaml`, owner and
lifecycle from the Component) and the Component's `spec.providesApis`. Only the entities it
generated (annotated `adhar.io/generated-by: adhar-kit-maven-plugin`) and the `providesApis`
entry are rewritten; everything else in the file stays as written.

```bash
mvn adhar:catalog                               # write catalog-info.yaml
mvn adhar:catalog -Dadhar.catalog.check=true    # CI: fail when it lags behind the specs
```

The plugin itself needs a Java 25 JDK; the code it generates does not.

### 🔢 Semantic Versioning (`adhar:version`)
Automatic version management based on Conventional Commits:
- **Automatic version calculation** from commit messages
- **Manual version bumping** (major, minor, patch)
- **Git tag creation** and management
- **POM version updates**
- **Conventional Commits support**

```bash
# Auto-detect version from commits
mvn adhar:version

# Manual version bump
mvn adhar:version -Dversion.type=major
mvn adhar:version -Dversion.type=minor
mvn adhar:version -Dversion.type=patch

# Create Git tag
mvn adhar:version -Dversion.tag=true

# Push tag to remote
mvn adhar:version -Dversion.tag=true -Dversion.push=true
```

### 🚀 Release Management (`adhar:release`)
Automated release process:
- **Version bumping** and validation
- **Changelog generation** from commits
- **Release notes generation**
- **Git tag creation**
- **Artifact signing**
- **Deployment automation**
- **GitHub/GitLab releases**

```bash
# Standard release
mvn adhar:release

# Major release with deployment
mvn adhar:release -Drelease.type=major -Drelease.deploy=true

# Dry run (no actual changes)
mvn adhar:release -Drelease.dryRun=true

# Skip tests
mvn adhar:release -Drelease.skipTests=true
```

### ⚙️ Code Generation (`adhar:generate`)
Generate boilerplate code following Adhar Kit patterns:
- **Entity classes** with JPA annotations
- **Repository interfaces** with QueryDSL
- **Service layer** (interface + implementation)
- **REST Controllers** with OpenAPI docs
- **DTOs** with validation
- **Unit and integration tests**

```bash
# Generate all components for an entity
mvn adhar:generate -Dgenerate.type=all -Dgenerate.name=User

# Generate specific components
mvn adhar:generate -Dgenerate.type=service -Dgenerate.name=UserService
mvn adhar:generate -Dgenerate.type=controller -Dgenerate.name=UserController
mvn adhar:generate -Dgenerate.type=repository -Dgenerate.name=UserRepository
mvn adhar:generate -Dgenerate.type=dto -Dgenerate.name=User

# Customize generation
mvn adhar:generate \
  -Dgenerate.type=all \
  -Dgenerate.name=Product \
  -Dgenerate.package=com.example.shop \
  -Dgenerate.withTests=true \
  -Dgenerate.useLombok=true \
  -Dgenerate.withOpenApi=true
```

### ✅ Code Validation (`adhar:validate`)
Enforce enterprise standards and best practices:
- **Package structure** conventions
- **Naming conventions** validation
- **Annotation presence** checking
- **API documentation** completeness
- **Exception handling** patterns
- **Logging usage** validation
- **Layered architecture** dependency direction (controller → service → repository, no reverse dependencies)
- **Test coverage** requirements

```bash
# Validate code standards
mvn adhar:validate

# Fail build on errors
mvn adhar:validate -Dvalidate.fail=true

# Generate validation report
mvn adhar:validate -Dvalidate.report=true

# Customize validation
mvn adhar:validate \
  -Dvalidate.packageStructure=true \
  -Dvalidate.naming=true \
  -Dvalidate.annotations=true \
  -Dvalidate.documentation=true \
  -Dvalidate.architecture=true \
  -Dvalidate.minCoverage=80
```

The architecture check (`ArchitectureRuleValidator`) determines each class's layer
from its actual `package` declaration and inspects its `import` statements - it
does not rely on file-path substring matching, so it works the same on Windows
and Unix checkouts and isn't fooled by incidental path segments. It flags any
`repository` class importing a `service`/`controller` type, or any `service`
class importing a `controller` type.

### 📦 Dependency Hygiene (`adhar:deps`)
Offline dependency hygiene report - reads only the already-built Maven project
model (no artifact resolution, no repository/network access, no CVE/OWASP
database downloads):
- **Duplicate dependencies** - the same `groupId:artifactId` declared more than once
- **Version convergence conflicts** - a declared version that diverges from the
  version already managed for it in `<dependencyManagement>`/an imported BOM
- **Dependencies not managed by a BOM** - a hard-coded version with no
  corresponding `<dependencyManagement>` entry at all

```bash
# Run the dependency hygiene check
mvn adhar:deps

# Fail the build on duplicates/convergence conflicts (unmanaged findings are informational only)
mvn adhar:deps -Ddeps.fail=true

# Customize the report location
mvn adhar:deps -Ddeps.reportFile=target/my-dependency-report.txt
```

## Installation

### Add to Project POM

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.adhar.kit</groupId>
            <artifactId>adhar-kit-maven-plugin</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <executions>
                <!-- Auto-validation on compile -->
                <execution>
                    <id>validate-standards</id>
                    <phase>validate</phase>
                    <goals>
                        <goal>validate</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <validatePackageStructure>true</validatePackageStructure>
                <validateNaming>true</validateNaming>
                <validateAnnotations>true</validateAnnotations>
                <failOnError>false</failOnError>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Plugin Settings

```xml
<settings>
    <pluginGroups>
        <pluginGroup>com.adhar.kit</pluginGroup>
    </pluginGroups>
</settings>
```

## Configuration

### Versioning Configuration

```xml
<configuration>
    <versionType>auto</versionType>              <!-- auto, major, minor, patch -->
    <createTag>false</createTag>                 <!-- Create Git tag -->
    <pushTag>false</pushTag>                     <!-- Push tag to remote -->
    <tagPrefix>v</tagPrefix>                     <!-- Tag prefix (e.g., v0.1.0-SNAPSHOT) -->
    <updatePom>true</updatePom>                  <!-- Update pom.xml -->
    <gitDirectory>${project.basedir}</gitDirectory>
    <snapshotSuffix>SNAPSHOT</snapshotSuffix>
</configuration>
```

### Release Configuration

```xml
<configuration>
    <releaseType>auto</releaseType>              <!-- auto, major, minor, patch -->
    <deploy>false</deploy>                       <!-- Deploy artifacts -->
    <signArtifacts>false</signArtifacts>        <!-- Sign with GPG -->
    <generateChangelog>true</generateChangelog>  <!-- Generate CHANGELOG.md -->
    <generateReleaseNotes>true</generateReleaseNotes> <!-- Generate release notes -->
    <createRelease>false</createRelease>         <!-- Create GitHub release -->
    <releaseNotesFile>RELEASE_NOTES.md</releaseNotesFile>
    <changelogFile>CHANGELOG.md</changelogFile>
    <dryRun>false</dryRun>                       <!-- Dry run mode -->
    <releaseBranch>main</releaseBranch>         <!-- Release branch -->
    <skipTests>false</skipTests>                 <!-- Skip tests -->
</configuration>
```

### Code Generation Configuration

```xml
<configuration>
    <type>all</type>                             <!-- entity, service, controller, repository, dto, all -->
    <name>User</name>                            <!-- Entity/class name -->
    <basePackage>${project.groupId}</basePackage>
    <outputDirectory>target/generated-sources/adhar</outputDirectory>
    <testOutputDirectory>target/generated-test-sources/adhar</testOutputDirectory>
    <generateTests>true</generateTests>          <!-- Generate tests (type=all only) -->
    <useLombok>true</useLombok>                  <!-- Use Lombok -->
    <withOpenApi>true</withOpenApi>              <!-- OpenAPI docs -->
    <tableName></tableName>                      <!-- Optional @Table name override (entity generation) -->
</configuration>
```

### Validation Configuration

```xml
<configuration>
    <failOnError>false</failOnError>             <!-- Fail build on errors -->
    <validatePackageStructure>true</validatePackageStructure>
    <validateNaming>true</validateNaming>
    <validateAnnotations>true</validateAnnotations>
    <validateDocumentation>true</validateDocumentation>
    <validateExceptions>true</validateExceptions>
    <validateLogging>true</validateLogging>
    <validateArchitecture>true</validateArchitecture>  <!-- Layered dependency direction check -->
    <minCoverage>80</minCoverage>                <!-- Minimum test coverage -->
    <generateReport>true</generateReport>
    <reportFile>target/adhar-validation-report.txt</reportFile>
</configuration>
```

### Dependency Hygiene Configuration

```xml
<configuration>
    <failOnError>false</failOnError>             <!-- Fail on duplicates/convergence conflicts -->
    <generateReport>true</generateReport>
    <reportFile>target/adhar-dependency-report.txt</reportFile>
</configuration>
```

## Conventional Commits

The plugin supports Conventional Commits specification for automatic versioning:

### Commit Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Commit Types

| Type | Version Bump | Description |
|------|--------------|-------------|
| `feat:` | Minor | New feature |
| `fix:` | Patch | Bug fix |
| `BREAKING CHANGE:` | Major | Breaking API change |
| `perf:` | Patch | Performance improvement |
| `refactor:` | None | Code refactoring |
| `docs:` | None | Documentation only |
| `test:` | None | Adding tests |
| `chore:` | None | Build/tooling changes |

### Examples

```bash
# Feature (bumps minor version)
git commit -m "feat(user): add user registration endpoint"

# Bug fix (bumps patch version)
git commit -m "fix(auth): resolve token expiration issue"

# Breaking change (bumps major version)
git commit -m "feat(api): redesign REST API

BREAKING CHANGE: API endpoints restructured"

# Multiple types
git commit -m "feat(user): add user profile
fix(auth): fix login validation
docs(readme): update API documentation"
```

## Generated Code Structure

When generating code with `type=all`, the plugin creates (sources under
`generate.outputDir`, tests under `generate.testOutputDir`):

```
target/generated-sources/adhar/
└── com/example/project/
    ├── entity/
    │   └── User.java                              # JPA entity with auditing (@Entity/@Table/@Id/@Version)
    ├── repository/
    │   └── UserRepository.java                     # Spring Data JPA repository
    ├── service/
    │   ├── UserService.java                        # Service interface
    │   └── impl/
    │       └── UserServiceImpl.java                # Service implementation
    ├── controller/
    │   └── UserController.java                      # REST controller with OpenAPI
    └── dto/
        ├── UserCreateDto.java                       # Creation DTO
        ├── UserUpdateDto.java                        # Update DTO
        └── UserResponseDto.java                      # Response DTO

target/generated-test-sources/adhar/     (when generate.withTests=true)
└── com/example/project/
    ├── service/
    │   └── UserServiceIntegrationTest.java          # JUnit 5 / Spring Boot test skeleton
    └── controller/
        └── UserControllerIntegrationTest.java       # MockMvc test skeleton
```

The entity generator (`EntityGenerator`) derives a default `@Table` name from the
entity name (e.g. `OrderItem` → `order_items`) unless `-Dgenerate.tableName=...`
is supplied. The integration test generator (`IntegrationTestGenerator`) produces
compiling `@Test` method stubs wired against the generated service/controller -
they are a starting point (marked with `TODO` comments), not a finished suite.

## Validation Rules

### Package Structure
- Required packages: `controller`, `service`, `repository`, `model`, `dto`, `config`

### Naming Conventions
- Controllers: End with `Controller`
- Services: End with `Service` or `ServiceImpl`
- Repositories: End with `Repository`
- DTOs: End with `Dto` or `DTO`

### Annotations
- Controllers: `@RestController` or `@Controller`
- Services: `@Service`
- Repositories: `@Repository` or extend Spring Data interface
- Transactional methods: `@Transactional`

### Documentation
- Public classes: Javadoc required
- Public methods: Javadoc recommended
- APIs: OpenAPI annotations

### Exception Handling
- Controllers: `@ExceptionHandler` or `@ControllerAdvice`
- Services: Proper exception propagation

### Architecture Layering
- Allowed dependency direction: `controller` → `service` → `repository`
- A `repository` class must never import a `service` or `controller` type
- A `service` class must never import a `controller` type
- Determined from actual `package`/`import` declarations, not file paths

### Logging
- Services: Logger field (SLF4J recommended)
- No `System.out.println()` usage
- Proper log levels

## Examples

### Complete Release Workflow

```bash
# 1. Develop features with conventional commits
git commit -m "feat(user): add user management"
git commit -m "fix(auth): resolve login issue"

# 2. Validate code standards
mvn adhar:validate

# 3. Calculate next version
mvn adhar:version -Dversion.type=auto

# 4. Perform release
mvn adhar:release \
  -Drelease.type=auto \
  -Drelease.changelog=true \
  -Drelease.notes=true \
  -Drelease.tag=true

# 5. Deploy to repository
mvn adhar:release \
  -Drelease.deploy=true \
  -Drelease.sign=true
```

### Microservice Generation

```bash
# Generate complete microservice structure
mvn adhar:generate -Dgenerate.type=all -Dgenerate.name=Order
mvn adhar:generate -Dgenerate.type=all -Dgenerate.name=Payment
mvn adhar:generate -Dgenerate.type=all -Dgenerate.name=Inventory

# Validate generated code
mvn adhar:validate

# Compile and test
mvn clean test
```

## Integration with CI/CD

### GitHub Actions

```yaml
name: Release
on:
  push:
    branches: [main]

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: '25'
          
      - name: Validate Code Standards
        run: mvn adhar:validate -Dvalidate.fail=true
        
      - name: Create Release
        run: |
          mvn adhar:release \
            -Drelease.type=auto \
            -Drelease.deploy=true \
            -Drelease.createRelease=true
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## Dependencies

- Maven 3.9.9+
- Java 25+
- JGit (for Git operations)
- Semver4j (for semantic versioning)
- JavaPoet (for code generation)

## License

Apache License 2.0

## Support

For issues and feature requests, please visit:
https://github.com/adhar-platform/adhar-kit/issues

---

**Generated by Adhar Kit Maven Plugin v0.1.0-SNAPSHOT**

