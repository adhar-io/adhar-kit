package com.adhar.kit.starter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Auto-configuration for Adhar Kit Starter.
 * Aggregates all Adhar Kit modules.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AdharKitProperties.class)
@ConditionalOnProperty(prefix = "adhar.kit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdharKitAutoConfiguration {

    private final AdharKitProperties properties;

    public AdharKitAutoConfiguration(AdharKitProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        log.info("╔═══════════════════════════════════════════════════════════════╗");
        log.info("║                                                               ║");
        log.info("║              ⚡ ADHAR KIT INITIALIZED ⚡                       ║");
        log.info("║                                                               ║");
        log.info("║  Enterprise Microservices Framework                          ║");
        log.info("║  Version: 1.0.0-SNAPSHOT                                      ║");
        log.info("║  Profile: {}                                             ║",
                String.format("%-42s", properties.getProfile()));
        log.info("║                                                               ║");
        log.info("╠═══════════════════════════════════════════════════════════════╣");
        log.info("║  Active Modules:                                              ║");

        var modules = properties.getModules();
        logModule("Logging", modules.isLogging());
        logModule("Metrics", modules.isMetrics());
        logModule("Tracing", modules.isTracing());
        logModule("Resilience", modules.isResilience());
        logModule("Security", modules.isSecurity());
        logModule("Persistence", modules.isPersistence());
        logModule("Cache", modules.isCache());
        logModule("Messaging", modules.isMessaging());
        logModule("Configuration", modules.isConfig());
        logModule("Health", modules.isHealth());
        logModule("API Docs", modules.isDocs());
        logModule("AI/ML", modules.isAi());
        logModule("Analytics", modules.isAnalytics());
        logModule("Kubernetes", modules.isKubernetes());
        logModule("Dapr", modules.isDapr());
        logModule("gRPC", modules.isGrpc());

        log.info("╚═══════════════════════════════════════════════════════════════╝");
    }

    private void logModule(String name, boolean enabled) {
        String status = enabled ? "✅" : "❌";
        log.info("║  {} {:<50} ║", status, name);
    }
}

