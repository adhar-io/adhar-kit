package com.adhar.kit.security.config;

import com.adhar.kit.security.jwks.JwksController;
import com.adhar.kit.security.jwks.JwksKeyManager;
import com.adhar.kit.security.properties.AdharSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for JWKS key management and publishing.
 *
 * <p>Activated only when the Nimbus JOSE library is on the classpath (via
 * {@link ConditionalOnClass}) and {@code adhar.security.jwks.enabled=true}. Because
 * Nimbus is an optional dependency, this configuration—and the classes it wires—are
 * never loaded when it is absent, so the module compiles and runs without it.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.nimbusds.jose.jwk.RSAKey")
@ConditionalOnProperty(prefix = "adhar.security.jwks", name = "enabled", havingValue = "true")
public class JwksConfiguration {

    /**
     * Configures the JWKS key manager holding the RSA signing keys.
     *
     * @param properties the security properties (key size and retention)
     * @return the JWKS key manager
     */
    @Bean
    @ConditionalOnMissingBean
    public JwksKeyManager jwksKeyManager(AdharSecurityProperties properties) {
        AdharSecurityProperties.JwksProperties jwks = properties.getJwks();
        log.info("Configuring JWKS key manager (path: {})", jwks.getPath());
        return new JwksKeyManager(jwks.getKeySize(), jwks.getRetainKeys());
    }

    /**
     * Configures the controller publishing the public JWKS.
     *
     * @param keyManager the JWKS key manager
     * @return the JWKS controller
     */
    @Bean
    @ConditionalOnMissingBean
    public JwksController jwksController(JwksKeyManager keyManager) {
        return new JwksController(keyManager);
    }
}
