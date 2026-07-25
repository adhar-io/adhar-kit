# Adhar Kit Rewrite

> Automated codebase modernization with OpenRewrite - Java, Spring Boot, Quarkus, Micronaut, Helidon, Vert.x, and Jakarta EE migrations.

## Features

- **RewriteFacade** - unified access via `adhar.getRewrite()`
- **In-process execution** - `InProcessRewriteRunner` runs recipes directly against in-memory
  sources via the OpenRewrite API and returns real per-file diffs, no `mvn rewrite:run` required
- **26 Pre-built Recipe Sets** across 10 categories
- **Java Migrations** - Java 17, 21, 25 upgrades with language feature adoption
- **Spring Boot Migrations** - Spring Boot 3.x and 4.x with starter renames and package moves
- **Quarkus Migrations** - Quarkus 3.x with extension renames and Jakarta namespace
- **Micronaut Migrations** - Micronaut 4.x with Jakarta namespace and API changes
- **Jakarta EE** - javax.* to jakarta.* for EE 10/11 compliance
- **Cross-Framework** - Spring Boot to Quarkus, Spring Boot to Micronaut
- **Adhar Kit** - convenience API adoption, CloudEvents, full modernization
- **Code Quality** - unused imports, SLF4J enforcement, dependency upgrades
- **Security** - OWASP top-10 hardening recipes
- **Testing** - JUnit 5, AssertJ, Mockito 5 migration

## Installation

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-rewrite</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
@Service
public class ModernizationService {
    private final AdharFacade adhar;

    public ModernizationService(AdharFacade adhar) { this.adhar = adhar; }

    public void modernize() {
        // List available recipe sets
        adhar.getRewrite().listRecipeSetKeys();
        // [java-25, spring-boot-4, quarkus-latest, micronaut-4, jakarta-ee-11,
        //  spring-to-quarkus, spring-to-micronaut, junit-5, adhar-full-modernization, ...]

        // Apply a specific migration
        adhar.getRewrite().apply("spring-boot-4", Path.of("."));

        // Full Adhar Kit modernization
        adhar.getRewrite().apply("adhar-full-modernization", Path.of("."));
    }
}
```

## Running Recipes In-Process

`dryRun`/`apply` generate a `rewrite.yml` and an `mvn rewrite:run` command for use with the
OpenRewrite Maven plugin. For recipes that are real `org.openrewrite.Recipe` classes with a
no-arg constructor (all of `com.adhar.kit.rewrite.recipe.adhar.*`, e.g. `UseConvenienceShortcuts`
and `AdoptCloudEvents`), you can instead run them immediately, in-process, against in-memory
sources and get back a real, human-readable diff:

```java
List<InProcessRewriteRunner.SourceInput> sources = List.of(
        new InProcessRewriteRunner.SourceInput("Demo.java", """
                class Demo {
                    void run(AdharKitFacade adhar) {
                        adhar.getMetrics().increment("requests");
                    }
                }
                """)
);

// Via the facade, by catalog key or explicit recipe class names:
RewriteImpactReport report = adhar.getRewrite().runInProcess("adhar-convenience-api", sources);

// Or directly:
InProcessRewriteRunner.RewriteExecution execution =
        InProcessRewriteRunner.run(new UseConvenienceShortcuts(), sources);
RewriteImpactReport report2 = RewriteImpactReport.of(execution);

report.changedFileCount();  // 1
System.out.println(report.toMarkdown()); // or report.toJson()
```

Validate that every recipe FQN referenced by the catalog actually resolves on the classpath:

```java
RecipeCatalogValidator.ValidationReport validation = adhar.getRewrite().validateCatalog();
if (!validation.isValid()) {
    log.warn(validation.toSummary());
}
```

## Recipe Catalog

| Category | Recipe Sets |
|----------|------------|
| **Java Migration** | `java-17`, `java-21`, `java-25` |
| **Spring Migration** | `spring-boot-3`, `spring-boot-4` |
| **Quarkus Migration** | `quarkus-3`, `quarkus-latest` |
| **Micronaut Migration** | `micronaut-4` |
| **Helidon Migration** | `helidon-4` |
| **Vert.x Migration** | `vertx-4` |
| **Jakarta EE** | `jakarta-ee-10`, `jakarta-ee-11` |
| **Cross-Framework** | `spring-to-quarkus`, `spring-to-micronaut`, `spring-to-helidon`, `spring-to-vertx` |
| **Testing** | `junit-5`, `assertj`, `mockito-5` |
| **Security** | `security-best-practices` |
| **Code Quality** | `code-cleanup`, `logging-best-practices`, `dependency-upgrade` |
| **Adhar Kit** | `adhar-convenience-api`, `adhar-cloudevents`, `adhar-spring-boot-4`, `adhar-full-modernization` |

## Configuration

```yaml
adhar:
  rewrite:
    enabled: true
    default-recipe-set: adhar-full-modernization
    output-dir: target/rewrite
```

## Maven Plugin Usage

For direct Maven execution without the facade:

```xml
<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <version>6.35.0</version>
    <configuration>
        <activeRecipes>
            <recipe>com.adhar.kit.rewrite.recipe.adhar.FullModernization</recipe>
        </activeRecipes>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>com.adhar.kit</groupId>
            <artifactId>adhar-kit-rewrite</artifactId>
            <version>0.1.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
</plugin>
```

Run: `mvn rewrite:run`

## API Reference

| Method | Description |
|--------|-------------|
| `dryRun(key, dir)` | Preview changes without modifying files |
| `apply(key, dir)` | Apply recipe set to project directory |
| `listRecipeSets()` | Get all available recipe sets |
| `listRecipeSetKeys()` | List recipe set keys |
| `getRecipeSet(key)` | Get details for a specific recipe set |
| `isRecipeAvailable(key)` | Check if recipe classes are on classpath |
| `runInProcess(key, sources)` / `runInProcess(recipeNames, sources)` | Run recipe(s) in-process against in-memory sources; returns a `RewriteImpactReport` with real diffs |
| `validateCatalog()` | Validate that every catalog recipe FQN resolves on the classpath |
