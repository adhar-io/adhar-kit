package com.adhar.adharkit.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main Spring Boot Application for Adhar AI Kit.
 * Provides AI capabilities using Spring AI framework with enterprise features.
 */
@SpringBootApplication
@EnableConfigurationProperties
@EnableCaching
@EnableAsync
public class AdharAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdharAiApplication.class, args);
    }
}
