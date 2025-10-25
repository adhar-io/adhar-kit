# Adhar Kit Docs

OpenAPI/Swagger integration for automatic API documentation generation.

## Features

- **OpenAPI 3.0** - Modern API documentation standard
- **Swagger UI** - Interactive API explorer
- **Security Documentation** - OAuth2, JWT, API Key support
- **Custom Annotations** - Simplified API documentation
- **Multiple API Groups** - Organize APIs by modules
- **Auto-configuration** - Zero-config setup

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-docs</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Usage

### Basic Controller Documentation

```java
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User management APIs")
public class UserController {
    
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves a user by their unique identifier")
    @StandardApiResponses
    public ResponseEntity<User> getUser(
            @Parameter(description = "User ID") @PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }
    
    @PostMapping
    @Operation(summary = "Create user", description = "Creates a new user")
    @StandardApiResponses
    public ResponseEntity<User> createUser(
            @RequestBody @Valid UserRequest request) {
        return ResponseEntity.ok(userService.create(request));
    }
}
```

### With Pagination

```java
@GetMapping
@Operation(summary = "List all users")
@PageableParameters
@StandardApiResponses
public ResponseEntity<Page<User>> listUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.findAll(pageable));
}
```

### Security Documentation

```yaml
adhar:
  docs:
    security:
      enabled: true
      scheme-name: bearerAuth
      scheme: bearer
      bearer-format: JWT
```

```java
@GetMapping("/profile")
@Operation(summary = "Get user profile", 
           security = @SecurityRequirement(name = "bearerAuth"))
public ResponseEntity<UserProfile> getProfile() {
    return ResponseEntity.ok(userService.getCurrentUserProfile());
}
```

### API Grouping

```yaml
adhar:
  docs:
    groups:
      - name: public-api
        display-name: Public API
        packages-to-scan: com.example.api.public
        paths-to-match: /api/public/**
      - name: admin-api
        display-name: Admin API
        packages-to-scan: com.example.api.admin
        paths-to-match: /api/admin/**
```

### Custom Annotations

Use pre-defined annotations for common scenarios:

```java
@RestController
@StandardApiResponses  // Adds standard HTTP response codes
public class ProductController {
    
    @GetMapping
    @PageableParameters  // Adds pagination parameters
    public Page<Product> list(Pageable pageable) {
        return productService.findAll(pageable);
    }
}
```

### Schema Documentation

```java
@Schema(description = "User registration request")
public class UserRequest {
    
    @Schema(description = "User email address", example = "user@example.com", required = true)
    @Email
    @NotNull
    private String email;
    
    @Schema(description = "User full name", example = "John Doe", required = true)
    @NotBlank
    private String name;
    
    @Schema(description = "User password", format = "password", minLength = 8)
    @NotNull
    private String password;
}
```

### Examples in Responses

```java
@GetMapping("/{id}")
@Operation(summary = "Get user")
@ApiResponse(responseCode = "200", description = "Success",
    content = @Content(mediaType = "application/json",
        schema = @Schema(implementation = User.class),
        examples = @ExampleObject(
            name = "user-example",
            value = """
                {
                  "id": 1,
                  "email": "john@example.com",
                  "name": "John Doe"
                }
                """
        )))
public ResponseEntity<User> getUser(@PathVariable Long id) {
    return ResponseEntity.ok(userService.findById(id));
}
```

## Configuration

### application.yml

```yaml
adhar:
  docs:
    enabled: true
    
    api-info:
      title: My API
      description: API for my application
      version: 1.0.0
      contact:
        name: API Support
        email: support@example.com
      license:
        name: Apache 2.0
        url: https://www.apache.org/licenses/LICENSE-2.0
    
    swagger-ui:
      enabled: true
      path: /swagger-ui.html
      try-it-out-enabled: true
    
    security:
      enabled: true
      scheme-name: bearerAuth
      scheme: bearer
      bearer-format: JWT
```

## Accessing Documentation

Once configured, access the documentation at:

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs
- **OpenAPI YAML:** http://localhost:8080/v3/api-docs.yaml

## Advanced Features

### Custom OpenAPI Customizer

```java
@Bean
public OpenApiCustomizer openApiCustomizer() {
    return openApi -> {
        openApi.info(openApi.getInfo()
                .description("Enhanced API Documentation"));
        
        // Add servers
        openApi.addServersItem(new Server()
                .url("https://api.production.com")
                .description("Production"));
        openApi.addServersItem(new Server()
                .url("https://api.staging.com")
                .description("Staging"));
    };
}
```

### Hide Endpoints

```java
@GetMapping("/internal")
@Hidden  // Hides from documentation
public String internalEndpoint() {
    return "Not documented";
}
```

### Deprecation

```java
@GetMapping("/old-endpoint")
@Operation(summary = "Old endpoint", deprecated = true)
@Deprecated
public String oldEndpoint() {
    return "Use /new-endpoint instead";
}
```

## Best Practices

1. **Use @Operation**: Provide clear summaries and descriptions
2. **Document Parameters**: Use @Parameter for clarity
3. **Schema Descriptions**: Add examples and constraints
4. **Group Related APIs**: Use tags for organization
5. **Security Documentation**: Document authentication requirements
6. **Version Your API**: Include version in URL or headers
7. **Provide Examples**: Include request/response examples

## Integration with Security

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

## Dependencies

- Springdoc OpenAPI
- Swagger UI
- Spring Web

## License

Copyright © 2025 Adhar Platform Team
package com.adhar.kit.docs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Adhar Docs module.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.docs")
public class DocsProperties {

    /**
     * Enable/disable API documentation.
     */
    private boolean enabled = true;

    /**
     * API information.
     */
    private ApiInfo apiInfo = new ApiInfo();

    /**
     * Swagger UI configuration.
     */
    private SwaggerUi swaggerUi = new SwaggerUi();

    /**
     * Security configuration.
     */
    private Security security = new Security();

    /**
     * Group configuration for multiple API groups.
     */
    private List<GroupConfig> groups = new ArrayList<>();

    @Data
    public static class ApiInfo {
        private String title = "API Documentation";
        private String description = "REST API Documentation";
        private String version = "1.0.0";
        private String termsOfService = "";
        private Contact contact = new Contact();
        private License license = new License();
    }

    @Data
    public static class Contact {
        private String name = "API Support";
        private String email = "support@example.com";
        private String url = "";
    }

    @Data
    public static class License {
        private String name = "Apache 2.0";
        private String url = "https://www.apache.org/licenses/LICENSE-2.0";
    }

    @Data
    public static class SwaggerUi {
        private boolean enabled = true;
        private String path = "/swagger-ui.html";
        private boolean displayRequestDuration = true;
        private boolean showExtensions = true;
        private String defaultModelsExpandDepth = "1";
        private String defaultModelExpandDepth = "1";
        private boolean tryItOutEnabled = true;
    }

    @Data
    public static class Security {
        private boolean enabled = false;
        private String schemeName = "bearerAuth";
        private String scheme = "bearer";
        private String bearerFormat = "JWT";
        private String description = "JWT Authentication";
    }

    @Data
    public static class GroupConfig {
        private String name;
        private String displayName;
        private String packagesToScan;
        private String pathsToMatch;
    }
}

