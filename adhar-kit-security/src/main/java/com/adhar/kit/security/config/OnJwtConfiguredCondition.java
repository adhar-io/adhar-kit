package com.adhar.kit.security.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Condition that matches when JWT validation is actually configured, i.e. when
 * either an issuer URI or a JWK set URI is provided.
 * <p>
 * This prevents the JWT decoder (and the OAuth2 resource server) from being
 * created when no JWT endpoint is configured, which would otherwise fail
 * application context startup for consumers that do not use JWT-based security.
 * </p>
 */
public class OnJwtConfiguredCondition extends AnyNestedCondition {

    public OnJwtConfiguredCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(prefix = "adhar.security.jwt", name = "issuer-uri")
    static class IssuerUri {
    }

    @ConditionalOnProperty(prefix = "adhar.security.jwt", name = "jwk-set-uri")
    static class JwkSetUri {
    }

    /**
     * {@code REGISTER_BEAN}-phase variant of {@link OnJwtConfiguredCondition} for use on
     * {@code @Bean} methods (bean-method conditions are evaluated at the register-bean phase,
     * so a {@code PARSE_CONFIGURATION} condition would be silently ignored there).
     */
    public static class OnBeanMethod extends AnyNestedCondition {

        public OnBeanMethod() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "adhar.security.jwt", name = "issuer-uri")
        static class IssuerUri {
        }

        @ConditionalOnProperty(prefix = "adhar.security.jwt", name = "jwk-set-uri")
        static class JwkSetUri {
        }
    }
}
