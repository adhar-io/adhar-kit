package com.adhar.kit.maven.adr;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AdrGenerator}: sequential numbering, slugification, and the
 * rendered template.
 */
class AdrGeneratorTest {

    private final Log log = mock(Log.class);

    @Test
    void slugifyProducesFilesystemFriendlySlug() {
        assertThat(AdrGenerator.slugify("Use PostgreSQL for persistence!"))
                .isEqualTo("use-postgresql-for-persistence");
        assertThat(AdrGenerator.slugify("   ---   ")).isEqualTo("adr");
    }

    @Test
    void nextNumberIsOneForEmptyDirectory(@TempDir Path dir) {
        AdrGenerator generator = new AdrGenerator(dir.toFile(), log);
        assertThat(generator.nextNumber()).isEqualTo(1);
    }

    @Test
    void nextNumberSkipsPastHighestExisting(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("0001-first.md"), "x");
        Files.writeString(dir.resolve("0007-seventh.md"), "x");
        Files.writeString(dir.resolve("notes.md"), "x"); // ignored - not NNNN-*

        AdrGenerator generator = new AdrGenerator(dir.toFile(), log);
        assertThat(generator.nextNumber()).isEqualTo(8);
    }

    @Test
    void createWritesSequentiallyNumberedTemplatedFile(@TempDir Path dir) throws Exception {
        AdrGenerator generator = new AdrGenerator(new File(dir.toFile(), "docs/adr"), log);

        File first = generator.create("Use PostgreSQL for persistence", "Accepted");
        File second = generator.create("Adopt hexagonal architecture", null);

        assertThat(first.getName()).isEqualTo("0001-use-postgresql-for-persistence.md");
        assertThat(second.getName()).isEqualTo("0002-adopt-hexagonal-architecture.md");

        String content = Files.readString(first.toPath());
        assertThat(content).contains("# 1. Use PostgreSQL for persistence");
        assertThat(content).contains("## Status");
        assertThat(content).contains("Accepted");
        assertThat(content).contains("## Context");
        assertThat(content).contains("## Decision");
        assertThat(content).contains("## Consequences");

        // Null status defaults to "Proposed".
        assertThat(Files.readString(second.toPath())).contains("Proposed");
    }

    @Test
    void renderTemplateDefaultsBlankStatusToProposed() {
        AdrGenerator generator = new AdrGenerator(new File("."), log);
        assertThat(generator.renderTemplate(3, "Title", "  ")).contains("Proposed");
        assertThat(generator.getAdrDirectory()).isNotNull();
    }
}
