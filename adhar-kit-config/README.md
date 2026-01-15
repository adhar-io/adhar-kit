# ⚙️ Adhar Kit Config - Enterprise Configuration Management

**Dynamic configuration management for microservices**

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.x-blue.svg)](https://quarkus.io/)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.x-blue.svg)](https://micronaut.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 1.0.0  
**Status:** ✅ Production Ready

---

## 📖 Overview

Enterprise-grade configuration management for microservices supporting multiple configuration sources, dynamic refresh, and encryption.

## 🎯 Features

### Configuration Sources
- **Environment Variables** - System environment variables with automatic conversion
- **File-based** - YAML, Properties files
- **Spring Cloud Config** - Centralized configuration server
- **HashiCorp Consul** - KV store integration
- **HashiCorp Vault** - Secrets management
- **Kubernetes** - ConfigMaps and Secrets

### Core Capabilities
- ✅ Multi-source configuration with priority-based merging
- ✅ Dynamic configuration refresh
- ✅ Property encryption/decryption (AES, DES)
- ✅ Configuration validation with rules
- ✅ Type-safe configuration binding
- ✅ Change listeners
- ✅ Health checks for config sources
- ✅ Framework-agnostic (Spring, Quarkus, Micronaut)
- ✅ File watching for auto-reload
- ✅ Property interpolation (${VAR})
- ✅ Profile-based configuration

---

## 📦 Installation

### Maven
```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-config</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
```gradle
implementation 'com.adhar.kit:adhar-kit-config:1.0.0'
```

---

## 🚀 Quick Start

### 1. Basic Configuration Manager

```java
// Create manager
ConfigManager manager = new ConfigManager();

// Add sources (higher priority = overrides lower)
manager.addSource(new EnvironmentConfigSource(200));      // Highest priority
manager.addSource(new FileConfigSource("app.yml", 100));  // Lower priority

// Get properties
String dbUrl = manager.getProperty("database.url", String.class);
Integer poolSize = manager.getProperty("database.pool.size", Integer.class, 10);

// Required properties (throws if missing)
String apiKey = manager.getRequiredProperty("api.key", String.class);
```

### 2. Environment Variables

```java
// Create environment source
ConfigSource envSource = new EnvironmentConfigSource();

// Environment variables are auto-converted:
// DATABASE_URL → database.url
// APP_SERVER_PORT → app.server.port
// SPRING_DATASOURCE_USERNAME → spring.datasource.username

// Use in manager
manager.addSource(envSource);

// Get value from environment
String dbUrl = manager.getProperty("database.url", String.class);
// Reads from DATABASE_URL environment variable
```

### 3. File-Based Configuration

```java
// Load YAML file
ConfigSource yamlSource = new FileConfigSource("classpath:application.yml", 100);
manager.addSource(yamlSource);

// Load Properties file
ConfigSource propsSource = new FileConfigSource("file:/etc/app/config.properties", 100);
manager.addSource(propsSource);

// Auto-detect file type
ConfigSource autoSource = new FileConfigSource("classpath:config.yml", 100);
```

**application.yml:**
```yaml
server:
  port: 8080
  host: localhost

database:
  url: jdbc:postgresql://localhost:5432/mydb
  pool:
    maxSize: 20
    minIdle: 10
```

**Access nested properties:**
```java
int port = manager.getProperty("server.port", Integer.class);
int maxSize = manager.getProperty("database.pool.maxSize", Integer.class);
```

### 4. Configuration Properties

```java
@ConfigurationProperties(prefix = "database")
@Data
public class DatabaseConfig {
    private String url;
    private String username;
    private String password;
    private Pool pool = new Pool();
    
    @Data
    public static class Pool {
        private int maxSize = 10;
        private int minIdle = 5;
        private long connectionTimeout = 30000;
    }
}
```

**application.yml:**
```yaml
database:
  url: jdbc:postgresql://localhost:5432/mydb
  username: dbuser
  password: ${DB_PASSWORD}  # From environment
  pool:
    max-size: 20
    min-idle: 10
    connection-timeout: 60000
```

---

## 📘 Core Components

### ConfigSource Interface

Custom configuration source:

```java
public class DatabaseConfigSource implements ConfigSource {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public String getType() {
        return "database";
    }
    
    @Override
    public int getPriority() {
        return 150;  // Medium priority
    }
    
    @Override
    public Map<String, Object> loadConfig() {
        return jdbcTemplate.query(
            "SELECT key, value FROM app_config",
            rs -> {
                Map<String, Object> config = new HashMap<>();
                while (rs.next()) {
                    config.put(rs.getString("key"), rs.getString("value"));
                }
                return config;
            }
        );
    }
    
    @Override
    public Optional<Object> getProperty(String key) {
        return Optional.ofNullable(loadConfig().get(key));
    }
    
    @Override
    public boolean supportsRefresh() {
        return true;
    }
    
    @Override
    public boolean refresh() {
        // Reload from database
        return true;
    }
}
```

### ConfigManager

Central configuration manager:

```java
ConfigManager manager = new ConfigManager();

// Add multiple sources
manager.addSource(new EnvironmentConfigSource(200));
manager.addSource(new ConsulConfigSource("http://localhost:8500", 150));
manager.addSource(new FileConfigSource("config.yml", 100));

// Get properties (environment > consul > file)
String value = manager.getProperty("some.key", String.class);

// Get properties with prefix
Map<String, Object> dbConfig = manager.getPropertiesWithPrefix("database.");

// Check if property exists
if (manager.containsProperty("feature.enabled")) {
    // ...
}

// Refresh all sources
manager.refreshAll();

// Refresh specific source
manager.refreshSource("consul");

// Health check
Map<String, Boolean> health = manager.getHealthStatus();
// {"environment": true, "consul": true, "file": true}
```

---

## 🔄 Dynamic Refresh

### Auto-refresh on Configuration Change

```java
@Component
public class CacheManager {
    
    private final ConfigManager configManager;
    private Cache cache;
    
    @PostConstruct
    public void init() {
        initializeCache();
        
        // Listen for config changes
        configManager.addChangeListener(this::handleConfigChange);
    }
    
    @RefreshConfig(keys = {"cache.ttl", "cache.maxSize"})
    public void initializeCache() {
        int ttl = configManager.getProperty("cache.ttl", Integer.class, 300);
        int maxSize = configManager.getProperty("cache.maxSize", Integer.class, 1000);
        
        cache = CacheBuilder.newBuilder()
            .expireAfterWrite(ttl, TimeUnit.SECONDS)
            .maximumSize(maxSize)
            .build();
            
        log.info("Cache initialized: ttl={}, maxSize={}", ttl, maxSize);
    }
    
    private void handleConfigChange(String key, Object oldValue, Object newValue) {
        if (key.startsWith("cache.")) {
            log.info("Cache config changed: {} = {} -> {}", key, oldValue, newValue);
            initializeCache();  // Reinitialize
        }
    }
}
```

### Scheduled Refresh

```java
@Scheduled(fixedDelay = 30000)  // Every 30 seconds
public void refreshConfiguration() {
    configManager.refreshAll();
}
```

---

## 🔐 Property Encryption

### Setup Encryptor

```java
// Create encryptor with secret key
PropertyEncryptor encryptor = new PropertyEncryptor("my-secret-key-16ch");

// Or with custom algorithm
PropertyEncryptor encryptor = new PropertyEncryptor("my-secret-key", "AES");
```

### Encrypt Sensitive Values

```java
// Encrypt a password
String encrypted = encryptor.encrypt("my-secret-password");
// Output: ENC(base64-encrypted-value)

// Use in configuration file
System.out.println("database.password=" + encrypted);
```

### Configuration with Encrypted Values

**application.yml:**
```yaml
database:
  username: dbuser
  password: ENC(k8sJ9xYzN2Lm5QwP...)  # Encrypted value
  
api:
  key: ENC(x3Fd8RtYuI9PqW...)  # Encrypted API key
  secret: ENC(m7Nv4BxC2ZaS...)  # Encrypted secret
```

### Auto-Decrypt in ConfigManager

```java
ConfigManager manager = new ConfigManager();

// Set encryptor for auto-decryption
PropertyEncryptor encryptor = new PropertyEncryptor("my-secret-key-16ch");
manager.setEncryptor(encryptor);

// Add sources
manager.addSource(new FileConfigSource("application.yml", 100));

// Get property - automatically decrypted
String password = manager.getProperty("database.password", String.class);
// Returns: "my-secret-password" (decrypted)
```

### Manual Encrypt/Decrypt

```java
PropertyEncryptor encryptor = new PropertyEncryptor("my-secret-key-16ch");

// Encrypt
String encrypted = encryptor.encrypt("sensitive-data");
System.out.println("Encrypted: " + encrypted);

// Decrypt
String decrypted = encryptor.decrypt(encrypted);
System.out.println("Decrypted: " + decrypted);

// Check if encrypted
boolean isEnc = encryptor.isEncrypted("ENC(...)");  // true

// Decrypt only if needed
String value = encryptor.decryptIfNeeded("plain-text");  // Returns as-is
String value2 = encryptor.decryptIfNeeded("ENC(...)");   // Decrypts
```

### Generate Encrypted Values

```java
public class EncryptionTool {
    public static void main(String[] args) {
        PropertyEncryptor encryptor = new PropertyEncryptor("my-secret-key-16ch");
        
        // Encrypt passwords
        System.out.println("DB Password: " + encryptor.encrypt("dbpass123"));
        System.out.println("API Key: " + encryptor.encrypt("api-key-xyz"));
        System.out.println("Secret: " + encryptor.encrypt("my-secret"));
    }
}
```

### Key Management Best Practices

1. **Environment Variable for Key:**
   ```java
   String secretKey = System.getenv("CONFIG_SECRET_KEY");
   PropertyEncryptor encryptor = new PropertyEncryptor(secretKey);
   ```

2. **Key Length Requirements:**
   - AES: 16, 24, or 32 characters
   - DES: 8 characters (legacy, not recommended)

3. **Store Keys Securely:**
   - Use environment variables
   - Use secrets management (Vault, AWS Secrets Manager)
   - Never commit keys to version control

---

## ✅ Configuration Validation

### Setup Validator

```java
ConfigValidator validator = new ConfigValidator();

// Add required properties
validator.addRequiredProperty("database.url");
validator.addRequiredProperty("api.key");

// Add pattern validation
validator.addPatternRule("database.url", "^jdbc:.*");
validator.addPatternRule("email", "^[A-Za-z0-9+_.-]+@(.+)$");

// Add range validation
validator.addRangeRule("server.port", 1024, 65535);
validator.addRangeRule("thread.pool.size", 1, 100);

// Add custom validation
validator.addCustomRule("api.endpoint", (key, value) -> {
    String url = String.valueOf(value);
    if (!url.startsWith("https://")) {
        return "API endpoint must use HTTPS";
    }
    return null;  // Valid
});
```

### Validate Configuration

```java
Map<String, Object> config = manager.loadConfig();

// Validate and get errors
List<String> errors = validator.validate(config);

if (!errors.isEmpty()) {
    log.error("Configuration errors:");
    errors.forEach(log::error);
    throw new ConfigurationException("Invalid configuration");
}

// Or validate and throw immediately
validator.validateOrThrow(config);
```

### Validation Examples

```java
// Example 1: Database configuration validation
ConfigValidator dbValidator = new ConfigValidator();
dbValidator.addRequiredProperty("database.url");
dbValidator.addRequiredProperty("database.username");
dbValidator.addRequiredProperty("database.password");
dbValidator.addPatternRule("database.url", "^jdbc:(postgresql|mysql|oracle):.*");
dbValidator.addRangeRule("database.pool.maxSize", 1, 100);
dbValidator.addRangeRule("database.pool.minIdle", 0, 50);

// Example 2: Server configuration validation
ConfigValidator serverValidator = new ConfigValidator();
serverValidator.addRequiredProperty("server.port");
serverValidator.addRangeRule("server.port", 1024, 65535);
serverValidator.addCustomRule("server.ssl.enabled", (key, value) -> {
    if (Boolean.parseBoolean(String.valueOf(value))) {
        // If SSL enabled, keystore must be configured
        if (!config.containsKey("server.ssl.keyStore")) {
            return "SSL keystore must be configured when SSL is enabled";
        }
    }
    return null;
});

// Example 3: Custom business rules
validator.addCustomRule("features.premium", (key, value) -> {
    boolean premiumEnabled = Boolean.parseBoolean(String.valueOf(value));
    String tier = String.valueOf(config.get("subscription.tier"));
    
    if (premiumEnabled && !"PREMIUM".equals(tier)) {
        return "Premium features require PREMIUM subscription tier";
    }
    return null;
});
```

---

## 🏗️ Configuration Hierarchy

Priority order (highest to lowest):

1. **Environment Variables** (200) - Overrides everything
2. **Consul/Vault** (150) - External configuration
3. **File-based** (100) - Default configuration

```java
// Example with all sources
ConfigManager manager = new ConfigManager();
manager.addSource(new EnvironmentConfigSource(200));
manager.addSource(new ConsulConfigSource("http://consul:8500", 150));
manager.addSource(new FileConfigSource("application.yml", 100));

// Property resolution:
// 1. Check environment: DATABASE_URL
// 2. If not found, check Consul: database.url
// 3. If not found, check file: database.url
// 4. If not found, use default or throw exception
```

---

## 🎯 Framework Integration

### Spring Boot

```java
@Configuration
public class AppConfig {
    
    @Bean
    public ConfigManager configManager() {
        ConfigManager manager = new ConfigManager();
        manager.addSource(new EnvironmentConfigSource());
        return manager;
    }
}

@Service
public class MyService {
    
    @Autowired
    private ConfigManager configManager;
    
    public void doSomething() {
        String apiUrl = configManager.getProperty("api.url", String.class);
        // Use apiUrl
    }
}
```

### Quarkus

```java
@ApplicationScoped
public class ConfigProducer {
    
    @Produces
    @ApplicationScoped
    public ConfigManager configManager() {
        ConfigManager manager = new ConfigManager();
        manager.addSource(new EnvironmentConfigSource());
        return manager;
    }
}
```

### Micronaut

```java
@Factory
public class ConfigFactory {
    
    @Singleton
    public ConfigManager configManager() {
        ConfigManager manager = new ConfigManager();
        manager.addSource(new EnvironmentConfigSource());
        return manager;
    }
}
```

---

## 📊 Complete Example

```java
// 1. Define configuration class
@ConfigurationProperties(prefix = "app")
@Data
public class AppConfig {
    private String name;
    private String version;
    private Server server = new Server();
    private Database database = new Database();
    
    @Data
    public static class Server {
        private int port = 8080;
        private String contextPath = "/";
        private Ssl ssl = new Ssl();
        
        @Data
        public static class Ssl {
            private boolean enabled = false;
            private String keyStore;
        }
    }
    
    @Data
    public static class Database {
        private String url;
        private String username;
        private String password;
        private int poolSize = 10;
    }
}

// 2. Configuration file (application.yml)
/*
app:
  name: my-service
  version: 1.0.0
  server:
    port: 8080
    context-path: /api
    ssl:
      enabled: true
      key-store: classpath:keystore.p12
  database:
    url: jdbc:postgresql://localhost:5432/mydb
    username: dbuser
    password: ${DB_PASSWORD}
    pool-size: 20
*/

// 3. Environment variables (override)
/*
export APP_SERVER_PORT=9090
export APP_DATABASE_PASSWORD=secret123
export APP_DATABASE_POOL_SIZE=50
*/

// 4. Use in application
@Service
public class MyService {
    
    private final ConfigManager configManager;
    private final AppConfig appConfig;
    
    public void initialize() {
        // Direct property access
        int port = configManager.getProperty("app.server.port", Integer.class);
        // Returns: 9090 (from environment, overrides yml)
        
        // Type-safe config object
        log.info("Starting {} v{}", appConfig.getName(), appConfig.getVersion());
        log.info("Server port: {}", appConfig.getServer().getPort());
        log.info("DB pool size: {}", appConfig.getDatabase().getPoolSize());
        // Output:
        // Starting my-service v1.0.0
        // Server port: 9090
        // DB pool size: 50
    }
}
```

---

## 🧪 Testing

```java
@Test
public void testConfigurationManagement() {
    ConfigManager manager = new ConfigManager();
    
    // Add test source
    Map<String, Object> testConfig = new HashMap<>();
    testConfig.put("test.value", "hello");
    testConfig.put("test.number", "42");
    
    manager.addSource(new MapConfigSource(testConfig, 100));
    
    // Assertions
    assertEquals("hello", manager.getProperty("test.value", String.class));
    assertEquals(42, manager.getProperty("test.number", Integer.class));
    assertTrue(manager.containsProperty("test.value"));
}

@Test
public void testConfigurationPriority() {
    ConfigManager manager = new ConfigManager();
    
    // Low priority
    Map<String, Object> lowPriority = Map.of("key", "low");
    manager.addSource(new MapConfigSource(lowPriority, 100));
    
    // High priority
    Map<String, Object> highPriority = Map.of("key", "high");
    manager.addSource(new MapConfigSource(highPriority, 200));
    
    // High priority wins
    assertEquals("high", manager.getProperty("key", String.class));
}
```

---

## 🔍 Best Practices

### 1. Use Environment Variables for Secrets

```yaml
database:
  url: jdbc:postgresql://localhost:5432/mydb
  username: ${DB_USERNAME}  # From environment
  password: ${DB_PASSWORD}  # From environment
```

### 2. Provide Sensible Defaults

```java
int timeout = manager.getProperty("api.timeout", Integer.class, 5000);
int retries = manager.getProperty("api.retries", Integer.class, 3);
```

### 3. Validate Required Properties

```java
@PostConstruct
public void validate() {
    configManager.getRequiredProperty("api.key", String.class);
    configManager.getRequiredProperty("database.url", String.class);
}
```

### 4. Use Type-safe Configuration Classes

```java
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    // Type-safe, IDE auto-complete, validation
}
```

### 5. Monitor Configuration Changes

```java
configManager.addChangeListener((key, oldValue, newValue) -> {
    log.info("Config changed: {} = {} -> {}", key, oldValue, newValue);
    metrics.counter("config.changes").increment();
});
```

---

## 📚 API Reference

### ConfigManager

| Method | Description |
|--------|-------------|
| `addSource(ConfigSource)` | Add configuration source |
| `removeSource(String)` | Remove source by type |
| `getProperty(String, Class<T>)` | Get typed property |
| `getProperty(String, Class<T>, T)` | Get with default |
| `getRequiredProperty(String, Class<T>)` | Get required property |
| `getPropertiesWithPrefix(String)` | Get all with prefix |
| `containsProperty(String)` | Check if exists |
| `refreshAll()` | Refresh all sources |
| `refreshSource(String)` | Refresh specific source |
| `addChangeListener(Listener)` | Listen for changes |
| `getHealthStatus()` | Get source health |

### ConfigSource

| Method | Description |
|--------|-------------|
| `getType()` | Source type identifier |
| `getPriority()` | Priority level |
| `isEnabled()` | Check if enabled |
| `loadConfig()` | Load all configuration |
| `getProperty(String)` | Get specific property |
| `supportsRefresh()` | Supports refresh? |
| `refresh()` | Refresh configuration |
| `isHealthy()` | Health check |

---

## 🤝 Contributing

Contributions welcome! Please read our contributing guidelines.

## 📄 License

Apache License 2.0

---

**Built with ❤️ by Adhar Platform Team**

