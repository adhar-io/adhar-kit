# Adhar Kit Config

Centralized configuration management with Spring Cloud Config, HashiCorp Vault, and property encryption.

## Features

- **Spring Cloud Config Integration** - Centralized configuration server
- **HashiCorp Vault** - Secure secret management  
- **Property Encryption** - Jasypt-based encryption
- **Dynamic Refresh** - Auto and manual configuration refresh
- **Multiple Profiles** - Environment-specific configurations

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-config</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Usage

### Spring Cloud Config

#### Configuration

```yaml
adhar:
  config:
    cloud-config:
      enabled: true
      uri: http://config-server:8888
      label: master
      profile: production
      username: admin
      password: secret
      fail-fast: true
```

#### bootstrap.yml

```yaml
spring:
  application:
    name: my-service
  cloud:
    config:
      uri: http://config-server:8888
      label: master
      profile: production
```

#### Programmatic Access

```java
@Service
@RequiredArgsConstructor
public class ConfigService {
    
    private final ConfigServerClient configClient;
    
    public void fetchConfig() {
        Map<String, Object> config = configClient.fetchConfig(
            "my-service", 
            "production", 
            "master"
        );
        
        log.info("Configuration: {}", config);
    }
}
```

### HashiCorp Vault

#### Configuration

```yaml
adhar:
  config:
    vault:
      enabled: true
      host: vault-server
      port: 8200
      scheme: https
      token: ${VAULT_TOKEN}
      backend: secret
```

#### Usage

```java
@Service
@RequiredArgsConstructor
public class SecretService {
    
    private final VaultClient vaultClient;
    
    public String getDatabasePassword() {
        Map<String, Object> secrets = vaultClient.readSecret("database/prod");
        return (String) secrets.get("password");
    }
    
    public void storeApiKey(String apiKey) {
        Map<String, Object> data = Map.of("apiKey", apiKey);
        vaultClient.writeSecret("api/external", data);
    }
}
```

### Property Encryption

#### Configuration

```yaml
adhar:
  config:
    encryption:
      enabled: true
      password: ${JASYPT_PASSWORD}
      algorithm: PBEWithMD5AndDES
```

#### Encrypt Properties

Use Jasypt CLI to encrypt values:

```bash
java -cp jasypt-1.9.3.jar org.jasypt.intf.cli.JasyptPBEStringEncryptionCLI \\
  input="mySecretValue" \\
  password="encryptionPassword" \\
  algorithm=PBEWithMD5AndDES
```

#### application.yml

```yaml
database:
  username: admin
  password: ENC(encrypted_value_here)
  
api:
  key: ENC(another_encrypted_value)
```

### Configuration Refresh

#### Auto Refresh

```yaml
adhar:
  config:
    refresh:
      auto-refresh: true
      refresh-interval: 60000  # 1 minute
```

#### Manual Refresh

```java
@Service
@RequiredArgsConstructor
public class AdminService {
    
    private final ConfigRefreshManager refreshManager;
    
    public void refreshConfiguration() {
        refreshManager.refresh();
    }
}
```

#### Refresh Endpoint

Enable actuator refresh endpoint:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: refresh,health,info
```

Trigger refresh:

```bash
curl -X POST http://localhost:8080/actuator/refresh
```

### @RefreshScope

Use @RefreshScope to reload beans on configuration change:

```java
@Service
@RefreshScope
public class DynamicConfigService {
    
    @Value("${feature.enabled}")
    private boolean featureEnabled;
    
    public boolean isFeatureEnabled() {
        return featureEnabled;
    }
}
```

## Configuration Priority

Configuration sources in order of priority (highest to lowest):

1. Command line arguments
2. Java System properties
3. OS environment variables
4. Profile-specific properties (application-{profile}.yml)
5. Application properties (application.yml)
6. Spring Cloud Config Server
7. Vault secrets
8. Default values

## Best Practices

1. **Use Vault for Secrets**: Never commit secrets to version control
2. **Encrypt Sensitive Data**: Use Jasypt for properties encryption
3. **Environment Variables**: Use for environment-specific values
4. **Fail Fast**: Enable fail-fast in production for config server
5. **Profiles**: Use different profiles for dev, test, prod
6. **Refresh Scope**: Mark beans that need refresh with @RefreshScope
7. **Bootstrap Context**: Use bootstrap.yml for config server settings

## Environment-Specific Configuration

### Local Development

```yaml
# application-local.yml
adhar:
  config:
    cloud-config:
      enabled: false
    vault:
      enabled: false
    encryption:
      enabled: false
```

### Production

```yaml
# application-prod.yml
adhar:
  config:
    cloud-config:
      enabled: true
      uri: https://config.production.com
      fail-fast: true
    vault:
      enabled: true
      scheme: https
      token: ${VAULT_TOKEN}
    encryption:
      enabled: true
      password: ${JASYPT_PASSWORD}
```

## Monitoring

Enable config monitoring via actuator:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: configprops,env
```

Access endpoints:
- `/actuator/configprops` - View all configuration properties
- `/actuator/env` - View environment properties
- `/actuator/refresh` - Refresh configuration

## Dependencies

- Spring Cloud Config
- Spring Cloud Vault
- Jasypt Spring Boot
- Spring Boot Actuator

## License

Copyright © 2025 Adhar Platform Team
package com.adhar.kit.config.autoconfigure;

import com.adhar.kit.config.properties.ConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Auto-configuration for Adhar Config module.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = "com.adhar.kit.config")
@EnableConfigurationProperties(ConfigProperties.class)
@ConditionalOnProperty(prefix = "adhar.config", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConfigAutoConfiguration {

    @Bean
    public RestTemplate configRestTemplate(ConfigProperties properties) {
        log.info("Initializing Config RestTemplate");
        
        var cloudConfig = properties.getCloudConfig();
        
        return new RestTemplateBuilder()
                .rootUri(cloudConfig.getUri())
                .setConnectTimeout(Duration.ofMillis(cloudConfig.getConnectTimeout()))
                .setReadTimeout(Duration.ofMillis(cloudConfig.getReadTimeout()))
                .basicAuthentication(cloudConfig.getUsername(), cloudConfig.getPassword())
                .build();
    }
}

