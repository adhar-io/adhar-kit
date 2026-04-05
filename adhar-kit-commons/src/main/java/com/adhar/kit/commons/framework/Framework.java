package com.adhar.kit.commons.framework;

/**
 * Enum representing all supported frameworks for Adhar Kit.
 *
 * <p>Adhar Kit provides framework-agnostic APIs with framework-specific adapters
 * for each of these runtime environments. The {@link FrameworkDetector} automatically
 * identifies which framework is active at runtime.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public enum Framework {

    /** Spring Boot 4.0+ (Spring Framework 7.0+). */
    SPRING_BOOT,

    /** Quarkus 3.21+ with CDI (ArC) and SmallRye. */
    QUARKUS,

    /** Micronaut 4.8+ with compile-time DI. */
    MICRONAUT,

    /** Oracle Helidon 4.x (SE and MP). */
    HELIDON,

    /** Eclipse Vert.x 4.x (reactive toolkit). */
    VERTX,

    /** Other/Unknown framework - uses standalone/fallback implementations. */
    OTHER
}
