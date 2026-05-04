# Adhar Kit GraphQL

> GraphQL API support with schema registry, cursor pagination, query complexity limits, and security interceptor.

## Features

- **GraphQlFacade** - unified access via `adhar.getGraphQl()`
- **Schema Registry** - register types, queries, and mutations in SDL
- **Cursor Pagination** - Relay-style Connection/Edge/PageInfo pattern
- **Input Validation** - Jakarta Validation integration for GraphQL inputs
- **DataLoader Registry** - N+1 prevention with batch loading pattern
- **Query Complexity** - configurable depth and complexity limits
- **Security Interceptor** - authentication enforcement, introspection control
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
```

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
