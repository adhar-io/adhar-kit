# Adhar Kit GraphQL

> GraphQL API support with schema registry, cursor pagination, query complexity limits, and security interceptor.

## Features

- **GraphQlFacade** - unified access via `adhar.getGraphQl()`
- **Schema Registry** - register types, queries, and mutations in SDL, syntax-validated with graphql-java's `SchemaParser` on registration, with `merge()` and an exposed `TypeDefinitionRegistry`
- **Cursor Pagination** - Relay-style Connection/Edge/PageInfo pattern
- **Input Validation** - Jakarta Validation integration for GraphQL inputs
- **DataLoader Registry** - real N+1 prevention: registered batch loaders are adapted into graphql-java `DataLoader`s and installed into every request's `DataLoaderRegistry` (via Spring GraphQL's `DataLoaderRegistrar`/`BatchLoaderRegistry`), so concurrent loads batch into a single call
- **Query Complexity** - configurable depth and complexity limits, enforced **before** execution (via graphql-java's `MaxQueryComplexityInstrumentation`/`MaxQueryDepthInstrumentation`) so over-limit queries are rejected without invoking a single `DataFetcher`
- **Automatic Persisted Queries** - Apollo APQ protocol support (`extensions.persistedQuery.sha256Hash`) with a bounded in-memory cache
- **Security Interceptor** - authentication enforcement, structural (AST-based) introspection detection
- **Custom Scalars** - LocalDateTime scalar included

## Installation

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-graphql</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
@Service
public class ProductService {
    private final AdharFacade adhar;

    public ProductService(AdharFacade adhar) { this.adhar = adhar; }

    public void setup() {
        // Register GraphQL types
        adhar.getGraphQl().registerType("Product", "type Product { id: ID!, name: String!, price: Float! }");
        adhar.getGraphQl().registerQuery("products", "products(first: Int, after: String): ProductConnection");

        // Paginate results
        var connection = adhar.getGraphQl().paginate(productList, 10, afterCursor);
    }
}
```

## Configuration

```yaml
adhar:
  graphql:
    enabled: true
    introspection-enabled: false   # disable in production
    max-query-depth: 10
    max-query-complexity: 200
    cors-enabled: true
    pagination:
      default-page-size: 20
      max-page-size: 100
    security:
      require-authentication: false
    persisted-queries:
      enabled: false        # set true to enable Automatic Persisted Queries
      max-cache-size: 1000
```

## DataLoader Batching

Batch loaders registered on the facade are automatically adapted into real graphql-java
`DataLoader`s for every request:

```java
adhar.getGraphQl().registerBatchLoader("users", keys ->
    CompletableFuture.supplyAsync(() -> userRepository.findAllById(keys)));
```

Multiple `DataLoader.load(key)` calls issued while resolving a single request (e.g. once
per row in a list) are coalesced into a single call to the registered function once the
loader is dispatched - no per-row queries.

## Automatic Persisted Queries (APQ)

When `adhar.graphql.persisted-queries.enabled=true`, the module implements Apollo's APQ
protocol: clients send `extensions.persistedQuery.sha256Hash` with each request.

- **Hash only**: looked up in the cache; a cache miss returns a `PersistedQueryNotFound`
  error so the client can retry with the full query attached.
- **Hash + query**: the SHA-256 of the query is verified against the hash, then the
  query is cached for subsequent hash-only requests.

## API Reference

| Method | Description |
|--------|-------------|
| `registerType(name, sdl)` | Register a GraphQL type definition |
| `registerQuery(name, sdl)` | Register a query field |
| `registerMutation(name, sdl)` | Register a mutation field |
| `getSchema()` | Get merged SDL schema |
| `paginate(list, first, cursor)` | Create Relay-style pagination |
| `validate(input)` | Validate input with Jakarta Validation |
| `registerBatchLoader(name, fn)` | Register DataLoader for N+1 prevention |
