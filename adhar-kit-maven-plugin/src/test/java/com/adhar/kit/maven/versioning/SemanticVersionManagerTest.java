package com.adhar.kit.maven.versioning;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for semantic versioning operations.
 */
class SemanticVersionManagerTest {

    @Test
    void testBumpMajorVersion() {
        SemanticVersionManager manager = createManager();

        String newVersion = manager.bumpVersion("1.2.3", "major");

        assertThat(newVersion).isEqualTo("2.0.0");
    }

    @Test
    void testBumpMinorVersion() {
        SemanticVersionManager manager = createManager();

        String newVersion = manager.bumpVersion("1.2.3", "minor");

        assertThat(newVersion).isEqualTo("1.3.0");
    }

    @Test
    void testBumpPatchVersion() {
        SemanticVersionManager manager = createManager();

        String newVersion = manager.bumpVersion("1.2.3", "patch");

        assertThat(newVersion).isEqualTo("1.2.4");
    }

    @Test
    void testBumpVersionWithSnapshot() {
        SemanticVersionManager manager = createManager();

        String newVersion = manager.bumpVersion("1.2.3-SNAPSHOT", "minor");

        assertThat(newVersion).isEqualTo("1.3.0");
    }

    @Test
    void testInvalidBumpType() {
        SemanticVersionManager manager = createManager();

        assertThatThrownBy(() -> manager.bumpVersion("1.2.3", "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid bump type");
    }

    private SemanticVersionManager createManager() {
        try {
            return new SemanticVersionManager(System.getProperty("user.dir"), new SystemStreamLog());
        } catch (Exception e) {
            // Return a mock if Git is not available - use null for the path to skip git initialization
            try {
                return new SemanticVersionManager(null, new SystemStreamLog());
            } catch (Exception ex) {
                throw new RuntimeException("Failed to create SemanticVersionManager", ex);
            }
        }
    }
}

