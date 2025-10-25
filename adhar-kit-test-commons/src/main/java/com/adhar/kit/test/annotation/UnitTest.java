package com.adhar.kit.test.annotation;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.*;

/**
 * Annotation to mark unit tests.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("unit")
public @interface UnitTest {
}


