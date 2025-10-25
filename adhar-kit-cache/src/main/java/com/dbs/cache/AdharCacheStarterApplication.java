package com.dbs.cache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main application class for the Adhar Cache Starter.
 * This class is used for local development and testing.
 * In a real application, the auto-configuration will be applied automatically.
 */
@SpringBootApplication
@EnableCaching
public class AdharCacheStarterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdharCacheStarterApplication.class, args);
    }

}
