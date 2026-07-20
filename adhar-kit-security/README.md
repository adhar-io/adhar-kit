# 🔐 Adhar Kit Security - Enterprise Security Module

**Comprehensive security for enterprise applications**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.17+-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.8+-blue.svg)](https://micronaut.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 0.1.0-SNAPSHOT  
**Status:** ✅ Production Ready

---

## 📖 Overview

A comprehensive security starter for enterprise applications based on Spring Security, OAuth2, and OpenID Connect standards.

## 🎯 Features

- **OAuth2/OpenID Connect Integration**: Seamless integration with OAuth2 and OpenID Connect providers
- **JWT Token Validation**: Robust JWT token validation with customizable claims
- **CORS Configuration**: Flexible CORS configuration for cross-origin requests
- **Security Headers**: Comprehensive security headers configuration
- **Content Security Policy**: Configurable Content Security Policy
- **Authorization**: Fine-grained URL-based authorization
- **Rate Limiting**: IP-based request throttling with configurable limits
- **Security Audit Logging**: Structured Jackson-JSON logging for authentication events with a pluggable `AuditEventSink`
- **Token Refresh**: JWT token refresh with optional rotation and a pluggable `RefreshTokenStore` (in-memory default, Redis-ready)
- **Unified SecurityService**: Framework-agnostic `SecurityFacade`/`SecurityService` backed by a Spring Security adapter
- **RBAC Annotations**: `@RequiresRole` / `@RequiresPermission` (any-of/all-of) enforced by an AOP aspect
- **API-Key Authentication**: Header-based API keys validated against SHA-256 hashes (constant-time compare)

## Getting Started

### Prerequisites

- Java 25 or higher
- Spring Boot 4.1.0 or higher

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

## Rate Limiting

Protect your APIs from abuse with configurable rate limiting.

### Configuration

```yaml
adhar:
  security:
    rate-limit:
      enabled: true
      max-requests: 100      # Maximum requests per window
      window-seconds: 60     # Time window in seconds
```

### Response Headers

When rate limiting is enabled, responses include standard headers:

| Header | Description |
|--------|-------------|
| `X-RateLimit-Limit` | Maximum requests allowed |
| `X-RateLimit-Remaining` | Requests remaining in current window |
| `X-RateLimit-Reset` | Unix timestamp when the window resets |
| `Retry-After` | Seconds until requests allowed (only on 429) |

### Rate Limit Exceeded Response

```json
{
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again in 45 seconds.",
  "retryAfter": 45
}
```

---

## Security Audit Logging

Track authentication events for security monitoring and compliance.

### Configuration

```yaml
adhar:
  security:
    audit:
      enabled: true
      log-successful-auth: true
      log-failed-auth: true
      log-logout: true
```

### Audit Log Format

Logs are written to the `SECURITY_AUDIT` logger:

```json
{
  "event": "AUTHENTICATION_SUCCESS",
  "timestamp": "2024-01-15T10:30:00Z",
  "principal": "user@example.com",
  "ipAddress": "192.168.1.100",
  "sessionId": "ABC123***",
  "authorities": ["ROLE_USER", "ROLE_ADMIN"],
  "details": {}
}
```

### Event Types

| Event Type | Description |
|------------|-------------|
| `AUTHENTICATION_SUCCESS` | Successful login |
| `AUTHENTICATION_FAILURE_BAD_CREDENTIALS` | Invalid username/password |
| `AUTHENTICATION_FAILURE_LOCKED` | Account locked |
| `LOGOUT_SUCCESS` | User logged out |

### Custom Audit Events

```java
@Service
public class MyService {

    @Autowired
    private SecurityAuditLogger auditLogger;

    public void sensitiveOperation(String userId) {
        auditLogger.logCustomEvent(
            SecurityAuditLogger.SecurityEventType.AUTHENTICATION_SUCCESS,
            userId,
            "192.168.1.100",
            Map.of("action", "sensitive-data-access")
        );
    }
}
```

---

## Token Refresh

Implement secure token refresh with optional refresh token rotation.

### Configuration

```yaml
adhar:
  security:
    token-refresh:
      enabled: true
      access-token-validity-seconds: 900     # 15 minutes
      refresh-token-validity-seconds: 604800 # 7 days
      rotate-refresh-tokens: true            # Recommended for security
      secret: your-256-bit-secret-key-here   # Must be 32+ characters
```

### Usage

```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private TokenRefreshService tokenRefreshService;

    @PostMapping("/login")
    public TokenRefreshService.TokenResponse login(@RequestBody LoginRequest request) {
        // Authenticate user...
        User user = authenticate(request.getEmail(), request.getPassword());

        // Create token pair
        return tokenRefreshService.createTokenPair(
            user.getId(),
            Map.of(
                "email", user.getEmail(),
                "roles", user.getRoles()
            )
        );
    }

    @PostMapping("/refresh")
    public TokenRefreshService.TokenResponse refresh(@RequestBody RefreshRequest request) {
        return tokenRefreshService.refreshAccessToken(request.getRefreshToken());
    }

    @PostMapping("/logout")
    public void logout(@RequestBody LogoutRequest request) {
        tokenRefreshService.revokeRefreshToken(request.getRefreshToken());
    }
}
```

### Token Response

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "accessTokenExpiresIn": 900,
  "refreshTokenExpiresIn": 604800,
  "tokenType": "Bearer"
}
```

### Refresh Token Rotation

When `rotate-refresh-tokens: true`:

1. Each refresh generates a new refresh token
2. Old refresh token is invalidated
3. Reuse of old tokens triggers family revocation (security feature)
4. Protects against token theft

### Revoking User Tokens

```java
// Revoke all refresh tokens for a user (e.g., on password change)
tokenRefreshService.revokeAllUserTokens(userId);
```

### Pluggable Refresh-Token Store

Token families and revocations live behind the `RefreshTokenStore` interface
(String keys/values, TTL hints per write). The default is `InMemoryRefreshTokenStore`;
provide your own bean (e.g. Redis-backed) to share state across nodes:

```java
@Bean
public RefreshTokenStore refreshTokenStore(StringRedisTemplate redis) {
    return new RedisRefreshTokenStore(redis); // your implementation
}
```

---

## Unified SecurityService / SecurityFacade

The auto-configuration registers a `SpringSecurityAdapter` (a `SecurityService` bean) and
wires it as the delegate of the singleton `SecurityFacade`, so portable code can do:

```java
SecurityService security = SecurityFacade.getInstance();

String userId = security.getCurrentUserId();        // from SecurityContextHolder (JWT-aware)
boolean admin  = security.hasRole("ADMIN");          // matches ADMIN or ROLE_ADMIN
boolean canDo  = security.hasPermission("order:create"); // exact or SCOPE_-prefixed authority
String encoded = security.encodePassword(raw);       // DelegatingPasswordEncoder by default
```

Token operations (`generateToken`, `validateToken`, `extractUserId`) delegate to
`TokenRefreshService` and therefore require `adhar.security.token-refresh.enabled=true`.
Provide your own `PasswordEncoder` or `SecurityService` bean to override the defaults.
Without Spring (no delegate registered) the facade falls back to inert stub behavior.

---

## RBAC Annotations

`@RequiresRole` and `@RequiresPermission` are enforced by `AccessControlAspect`
(enabled by default; disable with `adhar.security.rbac.enabled=false`). Both support
class-level and method-level placement (method wins) and `anyOf`/`allOf` modes.
Failures throw `com.adhar.kit.security.exception.AccessDeniedException`.

```java
@RequiresRole("ADMIN")
public void deleteOrder(Long id) { ... }

@RequiresRole(value = {"AUDITOR", "COMPLIANCE"}, mode = CheckMode.ALL_OF)
public Report complianceReport() { ... }

@RequiresPermission(value = {"order:read", "order:export"}) // any-of by default
public byte[] exportOrders() { ... }
```

---

## API-Key Authentication

Off by default. Keys are configured as lowercase-hex SHA-256 hashes (never plain text)
and compared in constant time. A valid key populates the `SecurityContext` with the
configured principal and roles; an invalid key is rejected with `401`; requests without
the header pass through to other authentication mechanisms.

```yaml
adhar:
  security:
    api-key:
      enabled: true
      header-name: X-API-Key          # default
      keys:
        - key-hash: 2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae
          principal: reporting-service
          roles:
            - ROLE_SERVICE
            - report:read
```

Generate a hash: `echo -n 'my-api-key' | shasum -a 256`

---

## Custom Audit Event Sink

Audit events are serialized with Jackson and written to the `SECURITY_AUDIT` logger by
the default `Slf4jAuditEventSink`. Ship them elsewhere by providing your own bean:

```java
@Bean
public AuditEventSink auditEventSink(KafkaTemplate<String, String> kafka) {
    return (eventType, auditData) -> kafka.send("security-audit", toJson(auditData));
}
```

---

## Complete Configuration Example

```yaml
adhar:
  security:
    enabled: true

    jwt:
      enabled: true
      issuer-uri: https://your-identity-provider.com
      jwk-set-uri: https://your-identity-provider.com/.well-known/jwks.json

    cors:
      enabled: true
      allowed-origins:
        - https://your-frontend.com
      allowed-methods:
        - GET
        - POST
        - PUT
        - DELETE

    rate-limit:
      enabled: true
      max-requests: 100
      window-seconds: 60

    audit:
      enabled: true
      log-successful-auth: true
      log-failed-auth: true
      log-logout: true

    token-refresh:
      enabled: true
      access-token-validity-seconds: 900
      refresh-token-validity-seconds: 604800
      rotate-refresh-tokens: true
      secret: ${TOKEN_REFRESH_SECRET}

    rbac:
      enabled: true

    api-key:
      enabled: true
      header-name: X-API-Key
      keys:
        - key-hash: ${REPORTING_API_KEY_SHA256}
          principal: reporting-service
          roles:
            - ROLE_SERVICE

    csrf:
      enabled: true
      ignore-ant-matchers:
        - /api/public/**
        - /actuator/**

    authorization:
      permit-all:
        - /public/**
        - /auth/**
        - /actuator/health
      authenticated:
        - /api/**
```

---

## License

This project is licensed under the MIT License - see the LICENSE file for details.
