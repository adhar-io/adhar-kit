package com.adhar.kit.test.annotation;

import com.adhar.kit.test.base.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UnitTest annotation.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@UnitTest
@DisplayName("UnitTest Annotation Tests")
class UnitTestAnnotationTest extends BaseUnitTest {

    @Test
    @DisplayName("Should have UnitTest annotation")
    void testHasUnitTestAnnotation() {
        // When
        boolean hasAnnotation = this.getClass().isAnnotationPresent(UnitTest.class);

        // Then
        assertTrue(hasAnnotation, "Test class should have UnitTest annotation");
    }

    @Test
    @DisplayName("Should have Tag annotation")
    void testHasTagAnnotation() {
        // When
        UnitTest annotation = this.getClass().getAnnotation(UnitTest.class);
        Tag tag = annotation.annotationType().getAnnotation(Tag.class);

        // Then
        assertNotNull(tag, "UnitTest should be annotated with Tag");
        assertEquals("unit", tag.value(), "Tag value should be 'unit'");
    }

    @Test
    @DisplayName("Should be a meta-annotation")
    void testMetaAnnotation() {
        // When
        Annotation[] annotations = this.getClass().getAnnotation(UnitTest.class)
                .annotationType().getAnnotations();

        // Then
        assertTrue(annotations.length > 0, "UnitTest should have meta-annotations");
    }

    @Test
    @DisplayName("Should have runtime retention")
    void testRuntimeRetention() {
        // When
        UnitTest annotation = this.getClass().getAnnotation(UnitTest.class);

        // Then
        assertNotNull(annotation, "Annotation should be available at runtime");
    }
}

