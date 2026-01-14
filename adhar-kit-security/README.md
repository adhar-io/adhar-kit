# Adhar Security Starter

A comprehensive security starter for enterprise applications based on Spring Security, OAuth2, and OpenID Connect standards.

## Features

- **OAuth2/OpenID Connect Integration**: Seamless integration with OAuth2 and OpenID Connect providers
- **JWT Token Validation**: Robust JWT token validation with customizable claims
- **CORS Configuration**: Flexible CORS configuration for cross-origin requests
- **Security Headers**: Comprehensive security headers configuration
- **Content Security Policy**: Configurable Content Security Policy
- **Authorization**: Fine-grained URL-based authorization

## Getting Started

### Prerequisites

- Java 25 or higher
- Spring Boot 4.0.0 or higher

### Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.example.adhar</groupId>
    <artifactId>adhar-security-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Configuration

The starter is auto-configured with sensible defaults, but you can customize it using the following properties in your `application.yml` or `application.properties` file:

```yaml
adhar:
  security:
    enabled: true # Enable/disable security features

    # JWT Configuration
    jwt:
      enabled: true
      issuer-uri: https://your-identity-provider.com
      jwk-set-uri: https://your-identity-provider.com/.well-known/jwks.json
      audience:
        - your-audience-1
        - your-audience-2
      authorities-claim-name: roles
      username-claim-name: preferred_username
      name-claim-name: name
      email-claim-name: email
      additional-claims:
        - custom_claim1
        - custom_claim2

    # OAuth2 Configuration
    oauth2:
      enabled: true
      client-id: your-client-id
      client-secret: your-client-secret
      authorization-uri: https://your-identity-provider.com/authorize
      token-uri: https://your-identity-provider.com/token
      user-info-uri: https://your-identity-provider.com/userinfo
      redirect-uri: https://your-application.com/login/oauth2/code/
      scopes:
        - openid
        - profile
        - email

    # CORS Configuration
    cors:
      enabled: true
      allowed-origins:
        - https://your-frontend.com
      allowed-methods:
        - GET
        - POST
        - PUT
        - DELETE
        - OPTIONS
      allowed-headers:
        - Authorization
        - Content-Type
      exposed-headers:
        - X-Custom-Header
      allow-credentials: true
      max-age: 3600

    # Content Security Policy
    csp:
      enabled: true
      directives:
        default-src: "'self'"
        script-src: "'self' https://trusted-cdn.com"
        style-src: "'self' https://trusted-cdn.com"
        img-src: "'self' data: https://trusted-cdn.com"
        font-src: "'self' https://trusted-cdn.com"
        connect-src: "'self'"
        frame-src: "'self'"
        object-src: "'none'"
      report-only: false
      report-uri: /csp-report

    # Security Headers
    headers:
      enabled: true
      xss-protection: true
      content-type-options: true
      frame-options: true
      frame-options-value: DENY
      hsts: true
      hsts-max-age-seconds: 31536000
      hsts-include-subdomains: true
      hsts-preload: false
      cache-control: true
      cache-control-value: no-cache, no-store, max-age=0, must-revalidate
      pragma: true
      pragma-value: no-cache
      expires: true
      expires-value: "0"
      referrer-policy: true
      referrer-policy-value: strict-origin-when-cross-origin

    # CSRF Protection
    csrf:
      enabled: true
      ignore-ant-matchers:
        - /api/public/**
        - /actuator/**
      header-name: X-CSRF-TOKEN
      parameter-name: _csrf
      cookie-name: XSRF-TOKEN
      cookie-enabled: false
      cookie-path: /
      cookie-secure: true
      cookie-http-only: true

    # Authorization
    authorization:
      enabled: true
      permit-all:
        - /public/**
        - /actuator/health
        - /actuator/info
      authenticated:
        - /api/**
      authorities:
        /admin/**:
          - ROLE_ADMIN
        /user/**:
          - ROLE_USER
          - ROLE_ADMIN
```

## Usage

### Securing REST APIs

The starter automatically secures all endpoints except those configured in `adhar.security.authorization.permit-all`. You can customize the authorization rules using the `adhar.security.authorization` properties.

### Accessing JWT Claims

You can access JWT claims in your controllers using the `JwtUtils` class:

```java
@RestController
@RequestMapping("/api")
public class ApiController {

    private final JwtUtils jwtUtils;

    public ApiController(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getUserInfo(Authentication authentication) {
        Jwt jwt = jwtUtils.extractJwt(authentication);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", jwtUtils.extractUsername(jwt));
        userInfo.put("name", jwtUtils.extractName(jwt));
        userInfo.put("email", jwtUtils.extractEmail(jwt));

        return ResponseEntity.ok(userInfo);
    }
}
```

### CSRF Protection

The starter includes CSRF protection by default. You can customize it using the `adhar.security.csrf` properties:

```yaml
adhar:
  security:
    csrf:
      enabled: true                 # Enable/disable CSRF protection
      ignore-ant-matchers:          # URL patterns to ignore for CSRF protection
        - /api/public/**
        - /actuator/**
      header-name: X-CSRF-TOKEN     # The CSRF token header name
      parameter-name: _csrf         # The CSRF token parameter name
      cookie-name: XSRF-TOKEN       # The CSRF cookie name
      cookie-enabled: false         # Whether to use CSRF cookie
      cookie-path: /                # The CSRF cookie path
      cookie-secure: true           # Whether the CSRF cookie is secure
      cookie-http-only: true        # Whether the CSRF cookie is HTTP only
```

To include CSRF tokens in your requests:

1. **Form Submissions**:
   ```html
   <form action="/api/resource" method="post">
     <input type="hidden" name="_csrf" value="${_csrf.token}" />
     <!-- other form fields -->
     <button type="submit">Submit</button>
   </form>
   ```

2. **JavaScript/AJAX Requests**:
   ```javascript
   // Get the CSRF token from the meta tag
   const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
   const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

   // Include the token in your fetch request
   fetch('/api/resource', {
     method: 'POST',
     headers: {
       'Content-Type': 'application/json',
       [csrfHeader]: csrfToken
     },
     body: JSON.stringify({ /* your data */ })
   });
   ```

3. **Using Cookies (if cookie-enabled is true)**:
   ```javascript
   // The CSRF token is automatically included in the XSRF-TOKEN cookie
   // You need to read it and include it in your requests
   function getCookie(name) {
     const value = `; ${document.cookie}`;
     const parts = value.split(`; ${name}=`);
     if (parts.length === 2) return parts.pop().split(';').shift();
   }

   const csrfToken = getCookie('XSRF-TOKEN');

   fetch('/api/resource', {
     method: 'POST',
     headers: {
       'Content-Type': 'application/json',
       'X-CSRF-TOKEN': csrfToken
     },
     body: JSON.stringify({ /* your data */ })
   });
   ```

### Method-Level Security

You can use Spring Security's method-level security annotations to secure your methods:

```java
@Service
public class UserService {

    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers() {
        // Only accessible to users with ROLE_ADMIN
        return userRepository.findAll();
    }

    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public User getUserById(Long id) {
        // Accessible to users with ROLE_USER or ROLE_ADMIN
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @PreAuthorize("#username == authentication.principal.username or hasRole('ADMIN')")
    public User getUserByUsername(String username) {
        // Accessible to the user themselves or users with ROLE_ADMIN
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
```

## Advanced Configuration

### Custom Security Filter Chain

If you need to customize the security filter chain, you can provide your own `SecurityFilterChain` bean:

```java
@Configuration
public class CustomSecurityConfig {

    @Bean
    public SecurityFilterChain customSecurityFilterChain(HttpSecurity http) throws Exception {
        // Your custom security configuration
        return http.build();
    }
}
```

### Custom JWT Authentication Converter

If you need to customize the JWT authentication converter, you can provide your own `Converter<Jwt, AbstractAuthenticationToken>` bean:

```java
@Configuration
public class CustomJwtConfig {

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> customJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("custom_roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.
