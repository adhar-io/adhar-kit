package com.adhar.kit.analytics.config;

import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.analytics.aspect.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Auto-configuration for Adhar Kit Analytics (PostHog integration).
 *
 * <p><b>Configuration Properties:</b></p>
 * <pre>
 * adhar.analytics.enabled=true
 * adhar.analytics.annotations.enabled=true
 * </pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Configuration
@EnableAspectJAutoProxy
@EnableAsync
@EnableConfigurationProperties(AnalyticsProperties.class)
@ConditionalOnProperty(
    prefix = "adhar.analytics",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@Slf4j
public class AnalyticsAutoConfiguration {

    /**
     * Creates AnalyticsFacade bean.
     *
     * <p>The facade itself is a singleton created lazily from environment
     * variables ({@code AnalyticsFacade.getInstance()}); this bean method
     * additionally applies the bound {@link AnalyticsProperties} (batch size,
     * flush interval, feature-flag TTL, consent opt-out list, PII redaction
     * keys) so Spring Boot configuration actually takes effect instead of
     * being dead config. {@code destroyMethod = "shutdown"} ensures buffered
     * events are flushed when the application context closes.</p>
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public AnalyticsFacade analyticsFacade(AnalyticsProperties properties) {
        log.info("Initializing Analytics Facade (PostHog)");
        AnalyticsFacade facade = AnalyticsFacade.getInstance();
        facade.configure(properties);
        return facade;
    }

    /**
     * Creates TrackEventAspect for @TrackEvent annotation processing.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "adhar.analytics.annotations",
        name = "track-event-enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public TrackEventAspect trackEventAspect() {
        log.info("Enabling @TrackEvent annotation support");
        return new TrackEventAspect();
    }

    /**
     * Creates TrackGroupAspect for @TrackGroup annotation processing.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "adhar.analytics.annotations",
        name = "track-group-enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public TrackGroupAspect trackGroupAspect() {
        log.info("Enabling @TrackGroup annotation support");
        return new TrackGroupAspect();
    }

    /**
     * Creates AliasUserAspect for @AliasUser annotation processing.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "adhar.analytics.annotations",
        name = "alias-user-enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public AliasUserAspect aliasUserAspect() {
        log.info("Enabling @AliasUser annotation support");
        return new AliasUserAspect();
    }
}

