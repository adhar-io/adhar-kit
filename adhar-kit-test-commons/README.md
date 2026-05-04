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
- **Base Test Classes** - Pre-configured base classes for unit, integration, and controller tests
- **WireMock Utilities** - Easy HTTP mocking for external services
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

### BaseUnitTest
- Configures Mockito
- Extends JUnit 5

### BaseControllerTest
- Configures MockMvc
- Provides JSON conversion utilities
- Auto-configures Spring MVC test

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
- WireMock
- AssertJ
- Rest Assured
- Awaitility

## License

Copyright © 2025 Adhar Platform Team

