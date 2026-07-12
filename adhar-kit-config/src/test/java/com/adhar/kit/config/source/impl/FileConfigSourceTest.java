package com.adhar.kit.config.source.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileConfigSourceTest {

    @Test
    void loadsPropertiesFromClasspath() {
        FileConfigSource source = new FileConfigSource("classpath:test-config.properties", 100);
        assertThat(source.getType()).isEqualTo("file");
        assertThat(source.getPriority()).isEqualTo(100);
        assertThat(source.supportsRefresh()).isTrue();

        Map<String, Object> config = source.loadConfig();
        assertThat(config).containsEntry("database.url", "jdbc:postgresql://localhost:5432/mydb")
                .containsEntry("server.port", "8080");
    }

    @Test
    void getPropertyReturnsFlattenedKey() {
        FileConfigSource source = new FileConfigSource("classpath:test-config.properties", 100);
        assertThat(source.getProperty("database.url")).contains("jdbc:postgresql://localhost:5432/mydb");
        assertThat(source.getProperty("database.pool.maxSize")).contains("20");
    }

    @Test
    void getPropertyMissingReturnsEmpty() {
        FileConfigSource source = new FileConfigSource("classpath:test-config.properties", 100);
        assertThat(source.getProperty("no.such.key")).isEmpty();
    }

    @Test
    void missingClasspathFileLoadsEmpty() {
        FileConfigSource source = new FileConfigSource("classpath:does-not-exist.properties", 100);
        assertThat(source.loadConfig()).isEmpty();
    }

    @Test
    void unsupportedLocationSchemeLoadsEmpty() {
        FileConfigSource source = new FileConfigSource("http://example.com/config", 100);
        assertThat(source.loadConfig()).isEmpty();
    }

    @Test
    void yamlTypeLoadsEmptyMapPlaceholder() {
        // YAML parsing is a simplified stub that returns an empty map.
        FileConfigSource source = new FileConfigSource("classpath:test-config.yml", 100);
        assertThat(source.loadConfig()).isEmpty();
    }

    @Test
    void explicitYamlTypeIsHonored() {
        FileConfigSource source = new FileConfigSource(
                "classpath:test-config.yml", 100, FileConfigSource.FileType.YAML);
        assertThat(source.loadConfig()).isEmpty();
    }

    @Test
    void explicitPropertiesTypeOnUnknownExtension() {
        FileConfigSource source = new FileConfigSource(
                "classpath:test-config.properties", 100, FileConfigSource.FileType.PROPERTIES);
        assertThat(source.loadConfig()).containsEntry("server.port", "8080");
    }

    @Test
    void refreshReloadsAndReturnsTrue() {
        FileConfigSource source = new FileConfigSource("classpath:test-config.properties", 100);
        assertThat(source.refresh()).isTrue();
        assertThat(source.loadConfig()).containsEntry("server.port", "8080");
    }

    @Test
    void loadsFromFileSystemLocation(@TempDir Path tempDir) throws IOException {
        Path propsFile = tempDir.resolve("app.properties");
        Files.writeString(propsFile, "foo.bar=baz\n");

        FileConfigSource source = new FileConfigSource("file:" + propsFile, 100);
        assertThat(source.getProperty("foo.bar")).contains("baz");
    }

    @Test
    void fileSystemMissingFileLoadsEmpty() {
        FileConfigSource source = new FileConfigSource("file:/nonexistent/path/app.properties", 100);
        assertThat(source.loadConfig()).isEmpty();
    }

    @Test
    void autoDetectYamlExtensionVariants() {
        // .yaml extension should be detected as YAML (empty stub result)
        FileConfigSource source = new FileConfigSource("classpath:missing.yaml", 100);
        assertThat(source.loadConfig()).isEmpty();
    }
}
