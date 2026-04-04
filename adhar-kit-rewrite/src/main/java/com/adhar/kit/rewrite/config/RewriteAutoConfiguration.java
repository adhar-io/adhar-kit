package com.adhar.kit.rewrite.config;

import com.adhar.kit.rewrite.facade.RewriteFacade;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Adhar Kit Rewrite module.
 *
 * <p>Registers {@link RewriteFacade} as a Spring bean when the module is on the classpath
 * and {@code adhar.rewrite.enabled} is {@code true} (the default).</p>
 *
 * <p><b>Configuration Properties:</b></p>
 * <pre>{@code
 * adhar:
 *   rewrite:
 *     enabled: true
 *     default-recipe-set: adhar-full-modernization
 *     output-dir: target/rewrite
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(RewriteProperties.class)
@ConditionalOnProperty(
    prefix = "adhar.rewrite",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class RewriteAutoConfiguration {

    @PostConstruct
    public void logRewriteConfiguration() {
        log.info("Adhar Rewrite module initialized - automated codebase modernization enabled");
    }

    /**
     * Creates the RewriteFacade bean.
     *
     * @return the singleton RewriteFacade instance
     */
    @Bean
    @ConditionalOnMissingBean
    public RewriteFacade rewriteFacade() {
        log.info("Initializing RewriteFacade");
        return RewriteFacade.getInstance();
    }
}
