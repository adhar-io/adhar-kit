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
     */
    @Bean
    @ConditionalOnMissingBean
    public AnalyticsFacade analyticsFacade() {
        log.info("Initializing Analytics Facade (PostHog)");
        return AnalyticsFacade.getInstance();
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

