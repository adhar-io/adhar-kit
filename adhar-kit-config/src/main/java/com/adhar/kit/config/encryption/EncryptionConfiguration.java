package com.adhar.kit.config.encryption;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for property encryption using Jasypt.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Configuration
@EnableEncryptableProperties
@ConditionalOnProperty(prefix = "adhar.config.encryption", name = "enabled", havingValue = "true")
public class EncryptionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EncryptionConfiguration.class);

    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor(com.adhar.kit.config.properties.ConfigProperties properties) {
        log.info("Initializing Jasypt String Encryptor");

        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();

        var encryptionConfig = properties.getEncryption();

        config.setPassword(encryptionConfig.getPassword());
        config.setAlgorithm(encryptionConfig.getAlgorithm());
        config.setKeyObtentionIterations(encryptionConfig.getKeyObtentionIterations());
        config.setPoolSize(encryptionConfig.getPoolSize());
        config.setSaltGeneratorClassName(encryptionConfig.getSaltGeneratorClassName());
        config.setStringOutputType(encryptionConfig.getStringOutputType());

        encryptor.setConfig(config);

        return encryptor;
    }
}

