package com.adhar.kit.commons.framework;

import lombok.extern.slf4j.Slf4j;

/**
 * Detects the runtime framework automatically.
 * Supports Spring Boot, Quarkus, Micronaut, and Helidon.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public final class FrameworkDetector {

    private static volatile Framework detectedFramework;

    private FrameworkDetector() {
        // Utility class
    }

    /**
     * Detect the current framework.
     * Result is cached after first detection.
     *
     * @return detected framework
     */
    public static Framework detect() {
        if (detectedFramework == null) {
            synchronized (FrameworkDetector.class) {
                if (detectedFramework == null) {
                    detectedFramework = performDetection();
                    log.info("Detected framework: {}", detectedFramework);
                }
            }
        }
        return detectedFramework;
    }

    /**
     * Check if running on Spring Boot.
     *
     * @return true if Spring Boot is detected
     */
    public static boolean isSpringBoot() {
        return detect() == Framework.SPRING_BOOT;
    }

    /**
     * Check if running on Quarkus.
     *
     * @return true if Quarkus is detected
     */
    public static boolean isQuarkus() {
        return detect() == Framework.QUARKUS;
    }

    /**
     * Check if running on Micronaut.
     *
     * @return true if Micronaut is detected
     */
    public static boolean isMicronaut() {
        return detect() == Framework.MICRONAUT;
    }

    /**
     * Perform framework detection by checking for framework-specific classes.
     *
     * @return detected framework
     */
    private static Framework performDetection() {
        // Check Spring Boot first (most common)
        if (isClassPresent("org.springframework.boot.SpringApplication")) {
            return Framework.SPRING_BOOT;
        }

        // Check Quarkus
        if (isClassPresent("io.quarkus.runtime.Quarkus")) {
            return Framework.QUARKUS;
        }

        // Check Micronaut
        if (isClassPresent("io.micronaut.context.ApplicationContext")) {
            return Framework.MICRONAUT;
        }

        log.warn("No supported framework detected. Using OTHER mode.");
        return Framework.OTHER;
    }

    /**
     * Check if a class is present on the classpath.
     *
     * @param className fully qualified class name
     * @return true if class is present
     */
    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, FrameworkDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Reset detection (for testing purposes).
     */
    static void resetDetection() {
        detectedFramework = null;
    }

    /**
     * Force a specific framework (for testing purposes).
     *
     * @param framework framework to force
     */
    static void forceFramework(Framework framework) {
        detectedFramework = framework;
    }
}

