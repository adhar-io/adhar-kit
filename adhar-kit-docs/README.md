# 📚 Adhar Kit Docs - OpenAPI/Swagger Integration

**Enterprise-grade API documentation for microservices**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.x-blue.svg)](https://micronaut.io/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-3.0-brightgreen.svg)](https://www.openapis.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 0.1.0-SNAPSHOT  
**Status:** ✅ Production Ready

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Quick Start](#quick-start)
- [Annotations](#annotations)
- [Configuration](#configuration)
- [Examples](#examples)
- [Customization](#customization)
- [Best Practices](#best-practices)

---

## 🎯 Overview

The **adhar-kit-docs** module provides comprehensive OpenAPI/Swagger integration for enterprise microservices with:

- 🚀 **Auto-configuration** - Zero-code documentation setup
- 🔐 **Security Integration** - JWT, OAuth2, API Key support
- 📝 **Standard Annotations** - Simplified API documentation
- 🎨 **Customizable UI** - Swagger UI with branding
- 🌍 **Multi-environment** - Dev, staging, production servers
- 📊 **Rich Examples** - Auto-generated request/response examples
- 🔍 **Filtering** - Package and path-based filtering

---

## ✨ Features

### Core Features

✅ **OpenAPI 3.0 Specification** - Latest standard  
✅ **Swagger UI** - Interactive API documentation  
✅ **Security Schemes** - JWT, OAuth2, API Key, Basic Auth  
✅ **Standard Responses** - Common HTTP status codes  
✅ **Pagination Support** - Pageable documentation  
✅ **Error Responses** - Standardized error schemas  
✅ **Request/Response Examples** - Auto-generated examples  
✅ **Multi-server Configuration** - Environment-specific endpoints  

### Enterprise Features

✅ **Common Headers** - Correlation ID, Request ID  
✅ **Custom Annotations** - Simplified documentation  
✅ **API Grouping** - Organize by tags/modules  
✅ **Version Management** - API versioning support  
✅ **Contact & License** - API metadata  
✅ **Terms of Service** - Legal documentation  

---

## 🚀 Quick Start

### 1. Add Dependency

**Maven:**
```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-docs</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'com.adhar.kit:adhar-kit-docs:0.1.0-SNAPSHOT'
```

### 2. Configure Application

**application.yml:**
```yaml
adhar:
  docs:
    enabled: true
    info:
      title: "Order Service API"
      version: "1.0.0"
      description: "Microservice for order management"
    security:
      jwt-enabled: true
```

### 3. Add OpenAPI Bean (Spring Boot)

```java
@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return AdharOpenApiConfig.builder()
            .title("Order Service API")
            .version("1.0.0")
            .description("Comprehensive order management API")
            .contact("API Team", "api@example.com", "https://example.com")
            .license("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0")
            .server("http://localhost:8080", "Local Development")
            .server("https://api.example.com", "Production")
            .withJwtSecurity()
            .build();
    }
}
```

### 4. Access Documentation

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`
- **OpenAPI YAML:** `http://localhost:8080/v3/api-docs.yaml`

---

## 🎯 Annotations

### @EnableOpenApiDocs

Enables automatic OpenAPI documentation generation (works with all frameworks).

```java
// Spring Boot
@SpringBootApplication
@EnableOpenApiDocs(
    title = "Order Service API",
    version = "1.0.0",
    description = "Order management API",
    enableJwtSecurity = true,
    packagesToScan = {"com.example.order.api"}
)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

// Quarkus
@ApplicationPath("/api")
@EnableOpenApiDocs(title = "Order Service API", version = "1.0.0")
public class ApiApplication extends Application {
}

// Micronaut
@EnableOpenApiDocs(title = "Order Service API", version = "1.0.0")
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
```

### @ApiGroup

Groups related endpoints together in documentation.

```java
@RestController
@RequestMapping("/api/orders")
@ApiGroup(
    name = "Orders",
    description = "Order management operations",
    priority = 1
)
public class OrderController {
    // All endpoints grouped under "Orders"
}
```

### @DeprecatedApi

Marks endpoints as deprecated with migration information.

```java
@GetMapping("/api/v1/orders")
@DeprecatedApi(
    since = "2.0.0",
    migrateToUrl = "/api/v2/orders",
    removalDate = "2025-12-31",
    reason = "Please use v2 API for better performance"
)
public ResponseEntity<List<Order>> getOrdersV1() {
    return ResponseEntity.ok(orderService.getOrders());
}
```

### @StandardApiResponses

Adds standard HTTP response documentation to endpoints.

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @GetMapping("/{id}")
    @StandardApiResponses
    @Operation(summary = "Get order by ID")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }
}
```

**Automatically adds:**
- ✅ 200 OK - Success
- ✅ 400 Bad Request - Validation error
- ✅ 401 Unauthorized - Authentication required
- ✅ 403 Forbidden - Access denied
- ✅ 404 Not Found - Resource not found
- ✅ 500 Internal Server Error

### @PageableDocumentation

Documents pagination parameters for pageable endpoints.

```java
@GetMapping("/api/orders")
@PageableDocumentation
@Operation(summary = "Get all orders with pagination")
public ResponseEntity<Page<Order>> getOrders(
    @PageableDefault(size = 20) Pageable pageable
) {
    return ResponseEntity.ok(orderService.getOrders(pageable));
}
```

**Automatically documents:**
- `page` - Page number (0-based)
- `size` - Number of items per page
- `sort` - Sort criteria (e.g., "name,asc")

### @SecuredEndpoint

Marks endpoints as secured with JWT authentication.

```java
@RestController
@RequestMapping("/api/orders")
@SecuredEndpoint  // All endpoints require JWT
public class OrderController {
    
    @PostMapping
    @Operation(summary = "Create new order")
    public ResponseEntity<Order> createOrder(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }
}
```


---

## 🌐 Multi-Framework Support

### Spring Boot Integration

```java
// Method 1: Using @EnableOpenApiDocs
@SpringBootApplication
@EnableOpenApiDocs(
    title = "Order Service API",
    version = "1.0.0",
    enableJwtSecurity = true
)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

// Method 2: Programmatic Configuration
@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return SpringBootOpenApiIntegration.createOpenAPI()
            .title("Order Service API")
            .version("1.0.0")
            .description("Comprehensive order management")
            .withJwtSecurity()
            .build();
    }
}
```

### Quarkus Integration

```java
// Method 1: Using @EnableOpenApiDocs
@ApplicationPath("/api")
@EnableOpenApiDocs(
    title = "Order Service API",
    version = "1.0.0",
    enableJwtSecurity = true
)
public class ApiApplication extends Application {
}

// Method 2: CDI Producer
@ApplicationScoped
public class OpenApiConfig {
    
    @Produces
    public OpenAPI customOpenAPI() {
        return QuarkusOpenApiIntegration.createOpenAPI()
            .title("Order Service API")
            .version("1.0.0")
            .withJwtSecurity()
            .build();
    }
}

// Method 3: application.properties
quarkus.smallrye-openapi.info-title=Order Service API
quarkus.smallrye-openapi.info-version=1.0.0
adhar.docs.enabled=true
adhar.docs.security.jwt-enabled=true
```

### Micronaut Integration

```java
// Method 1: Using @EnableOpenApiDocs
@EnableOpenApiDocs(
    title = "Order Service API",
    version = "1.0.0",
    enableJwtSecurity = true
)
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}

// Method 2: Factory Bean
@Factory
public class OpenApiConfig {
    
    @Bean
    @Singleton
    public OpenAPI customOpenAPI() {
        return MicronautOpenApiIntegration.createOpenAPI()
            .title("Order Service API")
            .version("1.0.0")
            .withJwtSecurity()
            .build();
    }
}

// Method 3: application.yml
micronaut:
  router:
    static-resources:
      swagger:
        paths: classpath:META-INF/swagger
adhar:
  docs:
    enabled: true
    info:
      title: Order Service API
      version: 1.0.0
```

---

## 📦 Models

### ApiErrorResponse

Standard error response model for consistent error handling.

```java
// Automatic usage in exception handlers
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
        ValidationException ex,
        HttpServletRequest request
    ) {
        ApiErrorResponse error = ApiErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(400)
            .error("Bad Request")
            .message("Validation failed")
            .path(request.getRequestURI())
            .correlationId(request.getHeader("X-Correlation-ID"))
            .errors(ex.getValidationErrors())
            .build();
            
        return ResponseEntity.badRequest().body(error);
    }
}
```

### PageableResponse

Standard pageable response model for consistent pagination.

```java
@GetMapping("/api/orders")
public ResponseEntity<PageableResponse<Order>> getOrders(Pageable pageable) {
    Page<Order> page = orderService.getOrders(pageable);
    
    PageableResponse<Order> response = PageableResponse.<Order>builder()
        .content(page.getContent())
        .pagination(PaginationMetadata.builder()
            .page(page.getNumber())
            .size(page.getSize())
            .totalPages(page.getTotalPages())
            .totalElements(page.getTotalElements())
            .first(page.isFirst())
            .last(page.isLast())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build())
        .build();
        
    return ResponseEntity.ok(response);
}
```

---

## ⚙️ Configuration

### Complete Configuration Example

**application.yml:**
```yaml
adhar:
  docs:
    enabled: true
    
    # API Information
    info:
      title: "E-Commerce API"
      version: "2.0.0"
      description: "Comprehensive e-commerce microservices API"
      terms-of-service: "https://example.com/terms"
      
      contact:
        name: "API Support Team"
        email: "api-support@example.com"
        url: "https://example.com/support"
        
      license:
        name: "Apache 2.0"
        url: "https://www.apache.org/licenses/LICENSE-2.0"
    
    # Multi-environment Servers
    servers:
      - url: "http://localhost:8080"
        description: "Local Development"
      - url: "https://api-dev.example.com"
        description: "Development Environment"
      - url: "https://api-staging.example.com"
        description: "Staging Environment"
      - url: "https://api.example.com"
        description: "Production Environment"
    
    # Security Configuration
    security:
      enabled: true
      jwt-enabled: true
      api-key-enabled: false
      oauth2-enabled: false
      api-key-header: "X-API-KEY"
    
    # Swagger UI Customization
    swagger-ui:
      enabled: true
      path: "/swagger-ui.html"
      display-request-duration: true
      doc-expansion: "none"
      filter: true
      show-request-headers: true
    
    # Path Configuration
    paths:
      packages-to-scan:
        - "com.example.order.api"
        - "com.example.product.api"
      paths-to-match:
        - "/api/**"
      paths-to-exclude:
        - "/api/internal/**"
        - "/api/admin/**"
```

---

## 💡 Examples

### Complete REST Controller Example

```java
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order management operations")
@SecuredEndpoint
@StandardApiResponses
public class OrderController {
    
    private final OrderService orderService;
    
    @GetMapping
    @Operation(summary = "Get all orders", description = "Retrieve paginated list of orders")
    @PageableDocumentation
    public ResponseEntity<Page<Order>> getOrders(
        @PageableDefault(size = 20, sort = "createdDate,desc") Pageable pageable
    ) {
        return ResponseEntity.ok(orderService.getOrders(pageable));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    @ApiResponse(responseCode = "200", description = "Order found",
        content = @Content(schema = @Schema(implementation = Order.class)))
    public ResponseEntity<Order> getOrder(
        @Parameter(description = "Order ID", example = "ORD-12345")
        @PathVariable String id
    ) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }
    
    @PostMapping
    @Operation(summary = "Create new order")
    @ApiResponse(responseCode = "201", description = "Order created")
    public ResponseEntity<Order> createOrder(
        @RequestBody @Valid OrderRequest request
    ) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update order")
    public ResponseEntity<Order> updateOrder(
        @PathVariable String id,
        @RequestBody @Valid OrderRequest request
    ) {
        return ResponseEntity.ok(orderService.updateOrder(id, request));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete order")
    @ApiResponse(responseCode = "204", description = "Order deleted")
    public ResponseEntity<Void> deleteOrder(@PathVariable String id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
```

### Model Documentation Example

```java
@Schema(description = "Order information")
public class Order {
    
    @Schema(description = "Order ID", example = "ORD-12345", accessMode = Schema.AccessMode.READ_ONLY)
    private String id;
    
    @Schema(description = "Customer ID", example = "CUST-67890", required = true)
    private String customerId;
    
    @Schema(description = "Order status", example = "PENDING")
    private OrderStatus status;
    
    @Schema(description = "Order items", required = true)
    private List<OrderItem> items;
    
    @Schema(description = "Order total amount", example = "99.99")
    private BigDecimal totalAmount;
    
    @Schema(description = "Order creation timestamp", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdDate;
    
    // Getters and setters
}
```

### Request DTO Example

```java
@Schema(description = "Order creation request")
public class OrderRequest {
    
    @Schema(description = "Customer ID", example = "CUST-67890", required = true)
    @NotBlank
    private String customerId;
    
    @Schema(description = "Order items", required = true)
    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;
    
    @Schema(description = "Delivery address", required = true)
    @NotNull
    @Valid
    private Address deliveryAddress;
    
    @Schema(description = "Payment method", example = "CREDIT_CARD")
    private PaymentMethod paymentMethod;
    
    // Getters and setters
}
```

---

## 🎨 Customization

### Custom OpenAPI Customizer

`AdharOpenApiCustomizer.builder()` only applies the features you explicitly opt into.
`addCommonHeaders()` adds `X-Correlation-ID`/`X-Request-ID` request headers to every
operation, `addCommonResponses()` adds standard 400/401/403/404/500 error responses
(reusing a shared error schema), and `addExamples()` attaches representative example
payloads to request bodies and response content that don't already declare one
(including the error responses added above). A plain `new AdharOpenApiCustomizer()`
keeps the historical default of headers + responses (no examples).

```java
@Bean
public OpenApiCustomizer customOpenApiCustomizer() {
    return AdharOpenApiCustomizer.builder()
        .addCommonHeaders()
        .addCommonResponses()
        .addExamples()
        .build();
}
```

### RFC 9457 Problem Details (`application/problem+json`)

`ProblemDetailCustomizer` documents error responses using the RFC 9457
"Problem Details for HTTP APIs" shape (`type`, `title`, `status`, `detail`,
`instance`) as an alternative/complement to the `ApiErrorResponse`-based schema above.

```java
@Bean
public OpenApiCustomizer problemDetailCustomizer() {
    ProblemDetailCustomizer customizer = new ProblemDetailCustomizer();
    return customizer::customize;
}
```

### Exporting a Static Spec (`openapi.json` / `openapi.yaml`)

`OpenApiSpecExporter` writes the generated `OpenAPI` model (or a raw spec string) to
`openapi.json`/`openapi.yaml` — handy for CI pipelines, contract testing, or client
generation without a running server. It defaults to `target/` if no directory is given.

```java
OpenApiSpecExporter exporter = new OpenApiSpecExporter();
OpenApiSpecExporter.ExportResult result = exporter.exportAll(openApi, Path.of("target", "openapi"));
// result.jsonPath(), result.yamlPath()

// Or, from the facade using its currently configured/live spec:
ApiDocsFacade.getInstance().exportOpenApiSpec(Path.of("target", "openapi"));
```

### API Group → SpringDoc Grouped Specs

`GroupedApiCustomizer.buildGroupedOpenApis(...)` turns `@ApiGroup`-annotated
controllers into SpringDoc `GroupedOpenApi` definitions — one per distinct group
name, each producing its own `/v3/api-docs/{group}` document and Swagger UI selector
entry — while still applying the group's `Tag` (name/description/priority) to the
grouped spec, so existing tag-based grouping keeps working.

```java
@Bean
public List<GroupedOpenApi> groupedOpenApis() {
    return GroupedApiCustomizer.buildGroupedOpenApis(List.of(
        OrderController.class,
        PaymentController.class
    ));
}
```

### Custom Security Scheme

```java
@Bean
public OpenAPI customSecurityOpenAPI() {
    return AdharOpenApiConfig.builder()
        .title("Secure API")
        .version("1.0.0")
        .withJwtSecurity()
        .withApiKeySecurity("X-API-KEY")
        .withOAuth2Security()
        .build();
}
```

---

## 📊 Best Practices

### 1. Use Descriptive Summaries

```java
@Operation(
    summary = "Create new customer order",
    description = "Creates a new order for the specified customer with the provided items and delivery address"
)
```

### 2. Provide Examples

```java
@Parameter(
    description = "Order ID",
    example = "ORD-2024-001234",
    required = true
)
```

### 3. Document Error Responses

```java
@ApiResponse(responseCode = "400", description = "Invalid order data",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "Customer not found")
```

### 4. Group Related Endpoints

```java
@Tag(name = "Orders", description = "Order management operations")
@Tag(name = "Payments", description = "Payment processing")
```

### 5. Version Your APIs

```java
@RequestMapping("/api/v1/orders")  // Version 1
@RequestMapping("/api/v2/orders")  // Version 2
```

### 6. Use Schema Descriptions

```java
@Schema(
    description = "Order status",
    allowableValues = {"PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"}
)
```

---

## 🔧 Framework Support

### Spring Boot

```java
@SpringBootApplication
@EnableOpenApi  // If using custom annotation
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Quarkus

```java
@ApplicationPath("/api")
@OpenAPIDefinition(
    info = @Info(title = "Order API", version = "1.0.0")
)
public class OrderApplication extends Application {
}
```

### Micronaut

```yaml
micronaut:
  router:
    static-resources:
      swagger:
        paths: classpath:META-INF/swagger
        mapping: /swagger/**
```

---

## 📚 API Reference

### AdharOpenApiConfig

| Method | Description |
|--------|-------------|
| `builder()` | Creates configuration builder |
| `title(String)` | Sets API title |
| `version(String)` | Sets API version |
| `description(String)` | Sets API description |
| `contact(...)` | Adds contact information |
| `license(...)` | Adds license information |
| `server(...)` | Adds server configuration |
| `withJwtSecurity()` | Enables JWT authentication |
| `withApiKeySecurity(...)` | Enables API Key authentication |
| `withOAuth2Security()` | Enables OAuth2 authentication |
| `build()` | Builds OpenAPI instance |

### Annotations

| Annotation | Purpose |
|------------|---------|
| `@StandardApiResponses` | Adds standard HTTP responses |
| `@PageableDocumentation` | Documents pagination |
| `@SecuredEndpoint` | Marks as secured endpoint |

---

## 🌟 Features Matrix

| Feature | Status | Description |
|---------|--------|-------------|
| OpenAPI 3.0 | ✅ | Latest specification |
| Swagger UI | ✅ | Interactive documentation |
| JWT Security | ✅ | Bearer token auth |
| API Key Security | ✅ | Header-based auth |
| OAuth2 Security | ✅ | OAuth2 flows |
| Basic Auth | ✅ | Basic authentication |
| Pagination | ✅ | Pageable support |
| Error Responses | ✅ | Standard error schemas |
| Examples | ✅ | Request/response examples |
| Multi-server | ✅ | Environment configs |
| Custom Headers | ✅ | Correlation/Request ID |
| API Versioning | ✅ | Version management |

---

## 📦 Dependencies

```xml
<!-- OpenAPI -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>

<!-- Lombok (for cleaner code) -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

---

## 🤝 Contributing

Contributions are welcome! Please follow our [contribution guidelines](../CONTRIBUTING.md).

---

## 📄 License

Apache License 2.0 - see [LICENSE](../LICENSE) for details.

---

## 🔗 Related Modules

- [adhar-kit-commons](../adhar-kit-commons) - Common utilities
- [adhar-kit-security](../adhar-kit-security) - Security features
- [adhar-kit-core](../adhar-kit-core) - Core patterns

---

**Built with ❤️ by Adhar Platform Team**

