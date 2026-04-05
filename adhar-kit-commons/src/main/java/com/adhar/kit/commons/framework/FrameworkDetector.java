package com.adhar.kit.commons.framework;

import lombok.extern.slf4j.Slf4j;

/**
 * Detects the runtime framework automatically via classpath scanning.
 *
 * <p>Supports all five Adhar Kit frameworks: Spring Boot, Quarkus, Micronaut,
 * Helidon, and Vert.x. Detection result is cached after first invocation.</p>
 *
 * <p>Detection order (first match wins):</p>
 * <ol>
 *   <li>Spring Boot - {@code org.springframework.boot.SpringApplication}</li>
 *   <li>Quarkus - {@code io.quarkus.runtime.Quarkus}</li>
 *   <li>Micronaut - {@code io.micronaut.context.ApplicationContext}</li>
 *   <li>Helidon - {@code io.helidon.webserver.WebServer}</li>
 *   <li>Vert.x - {@code io.vertx.core.Vertx}</li>
 *   <li>OTHER - fallback when no framework detected</li>
 * </ol>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public final class FrameworkDetector {

    private static volatile Framework detectedFramework;

    private FrameworkDetector() {}

    /**
     * Detect the current framework. Result is cached after first detection.
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

    /** @return true if running on Spring Boot */
    public static boolean isSpringBoot() {
        return detect() == Framework.SPRING_BOOT;
    }

    /** @return true if running on Quarkus */
    public static boolean isQuarkus() {
        return detect() == Framework.QUARKUS;
    }

    /** @return true if running on Micronaut */
    public static boolean isMicronaut() {
        return detect() == Framework.MICRONAUT;
    }

    /** @return true if running on Helidon */
    public static boolean isHelidon() {
        return detect() == Framework.HELIDON;
    }

    /** @return true if running on Vert.x */
    public static boolean isVertx() {
        return detect() == Framework.VERTX;
    }

    private static Framework performDetection() {
        // Spring Boot (most common in enterprise)
        if (isClassPresent("org.springframework.boot.SpringApplication")) {
            return Framework.SPRING_BOOT;
        }
        // Quarkus
        if (isClassPresent("io.quarkus.runtime.Quarkus")) {
            return Framework.QUARKUS;
        }
        // Micronaut
        if (isClassPresent("io.micronaut.context.ApplicationContext")) {
            return Framework.MICRONAUT;
        }
        // Helidon (SE or MP)
        if (isClassPresent("io.helidon.webserver.WebServer")
                || isClassPresent("io.helidon.microprofile.cdi.Main")) {
            return Framework.HELIDON;
        }
        // Vert.x
        if (isClassPresent("io.vertx.core.Vertx")) {
            return Framework.VERTX;
        }

        log.warn("No supported framework detected. Using standalone/fallback mode.");
        return Framework.OTHER;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, FrameworkDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Reset detection (for testing). */
    static void resetDetection() {
        detectedFramework = null;
    }

    /** Force a specific framework (for testing). */
    static void forceFramework(Framework framework) {
        detectedFramework = framework;
    }
}
