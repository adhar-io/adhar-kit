package com.adhar.kit.commons.framework;

/**
 * Enum representing supported frameworks for Adhar Kit.
 *
 * <p>This enum identifies which framework an application or component is running on,
 * enabling framework-specific adapters and behaviors.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public enum Framework {
    /**
     * Spring Boot framework.
     */
    SPRING_BOOT,

    /**
     * Quarkus framework.
     */
    QUARKUS,

    /**
     * Micronaut framework.
     */
    MICRONAUT,

    /**
     * Other/Unknown framework.
     */
    OTHER
}

