# 🧪 Adhar Kit Test Commons - Enterprise Testing Utilities

**Comprehensive testing utilities for microservices**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.x-blue.svg)](https://micronaut.io/)
[![Testcontainers](https://img.shields.io/badge/Testcontainers-1.19+-blue.svg)](https://www.testcontainers.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 0.1.0-SNAPSHOT  
**Status:** ✅ Production Ready

---

## 📖 Overview

Comprehensive testing utilities and base classes for microservices integration and unit testing.

## 🎯 Features

- **Testcontainers Integration** - PostgreSQL, Redis, Kafka, MongoDB containers
- **Base Test Classes** - Pre-configured base classes for unit, integration, and controller tests,
  including single-service (`MongoIntegrationTest`, `RedisIntegrationTest`, `KafkaIntegrationTest`)
  and multi-service (`CompositeIntegrationTest`) variants
- **Shared Container Lifecycle** - `TestContainerRegistry` reconciles the `TestContainerFacade`
  instance API and the static `*TestContainer` helpers behind one ordered start/stop sequence and
  one shared Testcontainers `Network`
- **WireMock Utilities** - `WireMockTestServer` for richer HTTP stubbing/verification, plus
  `WireMockIntegrationTest` for `@DynamicPropertySource`-based wiring
- **Database Seeding** - `DatabaseSeeder` for loading SQL scripts or row builders into a test datasource
- **Test Data Builders** - Fluent API for creating test data
- **Custom Assertions** - Domain-specific assertions
- **Test Annotations** - Convenient test markers

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-test-commons</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

## Usage

### Integration Tests with Testcontainers

```java
@IntegrationTest
class UserServiceIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void shouldSaveUser() {
        // PostgreSQL container is automatically started
        User user = new User("john@example.com", "John Doe");
        User saved = userRepository.save(user);
        
        assertThat(saved.getId()).isNotNull();
    }
}
```

### Individual Containers

```java
class CustomIntegrationTest {
    
    @BeforeAll
    static void setup() {
        // Start PostgreSQL
        PostgresTestContainer.start();
        
        // Start Redis
        RedisTestContainer.start();
        
        // Start Kafka
        KafkaTestContainer.start();
        
        // Start MongoDB
        MongoTestContainer.start();
    }
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainer::getJdbcUrl);
        registry.add("spring.redis.host", RedisTestContainer::getHost);
        registry.add("spring.redis.port", RedisTestContainer::getPort);
        registry.add("spring.kafka.bootstrap-servers", KafkaTestContainer::getBootstrapServers);
        registry.add("spring.data.mongodb.uri", MongoTestContainer::getConnectionString);
    }
}
```

### Unit Tests

```java
@UnitTest
class UserServiceTest extends BaseUnitTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void shouldFindUserById() {
        User user = new User("john@example.com", "John Doe");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        
        Optional<User> found = userService.findById(1L);
        
        assertThat(found).isPresent();
    }
}
```

### Controller Tests

```java
class UserControllerTest extends BaseControllerTest {
    
    @MockBean
    private UserService userService;
    
    @Test
    void shouldGetUser() throws Exception {
        User user = new User("john@example.com", "John Doe");
        when(userService.findById(1L)).thenReturn(Optional.of(user));
        
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }
    
    @Test
    void shouldCreateUser() throws Exception {
        UserRequest request = new UserRequest("john@example.com", "John Doe");
        
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request)))
                .andExpect(status().isCreated());
    }
}
```

### WireMock for External APIs

```java
class ExternalApiTest {
    
    @BeforeAll
    static void setup() {
        MockRestServer.start(8080);
    }
    
    @AfterAll
    static void tearDown() {
        MockRestServer.stop();
    }
    
    @BeforeEach
    void resetMocks() {
        MockRestServer.reset();
    }
    
    @Test
    void shouldCallExternalApi() {
        // Stub the external API
        WireMock.stubFor(WireMock.get("/api/data")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"success\"}")));
        
        // Test your service that calls the external API
        Response response = externalService.fetchData();
        
        assertThat(response.getStatus()).isEqualTo("success");
    }
}
```

### WireMockTestServer (richer stubbing)

`WireMockTestServer` goes beyond `MockRestServer`/the static WireMock helper above: it is
instance-based (so you can run several independent stub servers side by side), and offers
convenience methods for stubbing every HTTP verb, adding response headers/delays, and verifying
call counts.

```java
class ExternalApiWithWireMockTestServer {

    private WireMockTestServer server;

    @BeforeEach
    void setUp() {
        server = WireMockTestServer.start(); // dynamic port
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void shouldCallExternalApi() {
        server.stubGetJson("/api/data", 200, "{\"status\":\"success\"}");

        Response response = externalService.fetchData(server.baseUrl());

        assertThat(response.getStatus()).isEqualTo("success");
        server.verifyGetCalled("/api/data", 1);
    }
}
```

For Spring context tests, extend `WireMockIntegrationTest` instead: it starts the server in a
static `@BeforeAll`, publishes `wiremock.server.base-url` via `@DynamicPropertySource`, and resets
stubs after every test.

```java
class ExternalApiIntegrationTest extends WireMockIntegrationTest {

    @Test
    void shouldCallExternalApi() {
        wireMockServer.stubGetJson("/api/data", 200, "{\"status\":\"success\"}");
        // ... exercise the Spring bean wired to wiremock.server.base-url
    }
}
```

### Test Data Builders

```java
class TestDataBuilderExample {
    
    @Test
    void shouldBuildTestData() {
        // Create single object
        User user = new TestDataBuilder<>(new User())
                .with(u -> u.setEmail(TestDataBuilder.randomEmail()))
                .with(u -> u.setName(TestDataBuilder.randomName()))
                .with(u -> u.setCreatedAt(TestDataBuilder.now()))
                .build();
        
        // Create multiple objects
        List<User> users = new TestDataBuilder<>(new User())
                .with(u -> u.setEmail(TestDataBuilder.randomEmail()))
                .with(u -> u.setName(TestDataBuilder.randomName()))
                .buildList(10);
        
        assertThat(users).hasSize(10);
    }
}
```

### Custom Assertions

```java
import static com.adhar.kit.test.assertion.CustomAssertions.*;

class CustomAssertionsExample {
    
    @Test
    void shouldUseCustomAssertions() {
        User user = userService.createUser(request);
        
        // Assert recent timestamp
        assertRecentTimestamp(user.getCreatedAt());
        
        // Assert valid UUID
        assertValidUUID(user.getId());
        
        // Assert valid email
        assertValidEmail(user.getEmail());
        
        // Assert same properties (excluding specific fields)
        User expected = new User();
        assertSameProperties(user, expected, "id", "createdAt");
    }
}
```

### Database Seeding

```java
class DatabaseSeederExample {

    @Test
    void shouldSeedTestData(DataSource dataSource) {
        DatabaseSeeder seeder = DatabaseSeeder.forDataSource(dataSource);

        seeder.runScriptFromClasspath("seed/users.sql");
        seeder.insert("users", Map.of("id", 1, "email", "a@b.com"));
        seeder.insertAll("orders", List.of(
                Map.of("id", 1, "user_id", 1),
                Map.of("id", 2, "user_id", 1)));

        assertThat(seeder.countRows("orders")).isEqualTo(2);

        seeder.truncate("orders", "users");
    }
}
```

## Available Containers

### PostgreSQL
```java
PostgresTestContainer.start();
String jdbcUrl = PostgresTestContainer.getJdbcUrl();
String username = PostgresTestContainer.getUsername();
String password = PostgresTestContainer.getPassword();
```

### Redis
```java
RedisTestContainer.start();
String host = RedisTestContainer.getHost();
Integer port = RedisTestContainer.getPort();
String connectionUrl = RedisTestContainer.getConnectionUrl();
```

### Kafka
```java
KafkaTestContainer.start();
String bootstrapServers = KafkaTestContainer.getBootstrapServers();
```

### MongoDB
```java
MongoTestContainer.start();
String connectionString = MongoTestContainer.getConnectionString();
```

## Base Classes

### BaseIntegrationTest
- Auto-configures PostgreSQL testcontainer
- Sets up Spring Boot test context
- Configures database properties

### MongoIntegrationTest / RedisIntegrationTest / KafkaIntegrationTest
- Mirror `BaseIntegrationTest`'s `@DynamicPropertySource` approach for MongoDB, Redis, and Kafka
  respectively
- Start their container through the shared `TestContainerRegistry`
- Wire `spring.data.mongodb.uri`, `spring.data.redis.host`/`port`, and
  `spring.kafka.bootstrap-servers` respectively

### CompositeIntegrationTest
- Starts PostgreSQL, MongoDB, Redis, and Kafka together (in that order) via
  `TestContainerRegistry`, wiring properties for all four
- Prefer the single-service base classes above when a test only needs one backing service

### WireMockIntegrationTest
- Starts a `WireMockTestServer` once per test class and publishes its base URL as
  `wiremock.server.base-url`
- Resets stubs after every test method

### BaseUnitTest
- Configures Mockito
- Extends JUnit 5

### BaseControllerTest
- Configures MockMvc
- Provides JSON conversion utilities
- Auto-configures Spring MVC test

## Container Lifecycle Registry

`TestContainerRegistry` is the single source of truth behind both container mechanisms in this
module: the `TestContainerFacade` instance API and the static `*TestContainer`/`base.*IntegrationTest`
helpers. Every container started through either mechanism is registered here, in start order, and
can join a shared Testcontainers `Network`.

```java
TestContainerRegistry registry = TestContainerRegistry.getInstance();

// Start (or reuse) a container, joining the shared network if it hasn't started yet
registry.registerAndStart("redis", RedisTestContainer.getInstance());

registry.isRegistered("redis");       // true
registry.registrationOrder();         // e.g. ["postgres", "mongo", "redis", "kafka"]

// Tear everything down in reverse start order
registry.stopAll();
```

## Annotations

### @IntegrationTest
Composite annotation that:
- Marks test as integration test
- Activates `test` profile
- Configures Spring Boot test context

### @UnitTest
- Marks test as unit test
- Adds `unit` tag for filtering

## Test Data Utilities

```java
// Random data generators
String id = TestDataBuilder.randomId();
String email = TestDataBuilder.randomEmail();
String name = TestDataBuilder.randomName();
String phone = TestDataBuilder.randomPhone();
int random = TestDataBuilder.randomInt(1, 100);
boolean bool = TestDataBuilder.randomBoolean();
LocalDateTime now = TestDataBuilder.now();

// Additional factories
String token = TestDataBuilder.randomAlphanumeric(16);
String status = TestDataBuilder.randomElement(List.of("PENDING", "ACTIVE", "CLOSED"));
LocalDateTime createdBefore = TestDataBuilder.pastTimestamp(30);   // 30 minutes ago
LocalDateTime expiresAt = TestDataBuilder.futureTimestamp(60);     // 60 minutes from now
```

## Best Practices

1. **Extend Base Classes**: Use `BaseIntegrationTest`, `BaseUnitTest`, or `BaseControllerTest`
2. **Use Annotations**: Mark tests with `@IntegrationTest` or `@UnitTest`
3. **Reuse Containers**: Containers are configured with `withReuse(true)` for faster tests
4. **Clean State**: Reset WireMock stubs between tests
5. **Parameterized Tests**: Use JUnit 5 parameterized tests for multiple scenarios
6. **Test Data**: Use TestDataBuilder for consistent test data

## Dependencies

- JUnit 5
- Mockito
- Spring Boot Test
- Testcontainers
- WireMock (`wiremock-standalone`)
- AssertJ
- Rest Assured
- Awaitility

## Docker Requirement

Tests that actually start a Testcontainers container (PostgreSQL, MongoDB, Redis, Kafka) are
gated behind the `testcontainers.enabled` system property (`@EnabledIfSystemProperty(named =
"testcontainers.enabled", matches = "true")`) and are skipped, not failed, when Docker is
unavailable. Everything else in this module - including `TestContainerRegistry` bookkeeping,
the new `base.*IntegrationTest` static hooks, and all of `WireMockTestServer`/`DatabaseSeeder` -
is exercised with Mockito-mocked containers/JDBC objects or a real embedded WireMock server, so
the build passes without Docker.

## License

Copyright © 2025 Adhar Platform Team

