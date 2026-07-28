package com.adhar.kit.config.source.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ConfigMapConfigSourceTest {

    @Test
    void readsKeyFilesFromMountedDirectory(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("database.url"), "jdbc:pg\n");
        Files.writeString(dir.resolve("log.level"), "DEBUG");

        ConfigMapConfigSource source = new ConfigMapConfigSource(dir.toString(), 130, false);

        assertThat(source.getType()).isEqualTo("configmap");
        assertThat(source.getPriority()).isEqualTo(130);
        assertThat(source.supportsRefresh()).isTrue();
        assertThat(source.getProperty("database.url")).contains("jdbc:pg");
        assertThat(source.getProperty("log.level")).contains("DEBUG");
        assertThat(source.loadConfig()).hasSize(2);
        source.close();
    }

    @Test
    void skipsKubernetesAtomicUpdateArtifacts(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("real.key"), "value");
        Files.writeString(dir.resolve("..data"), "should-be-skipped");
        Files.createDirectory(dir.resolve("..2024_10_10"));

        ConfigMapConfigSource source = new ConfigMapConfigSource(dir.toString());

        assertThat(source.loadConfig()).containsOnlyKeys("real.key");
        source.close();
    }

    @Test
    void missingDirectoryIsDisabledAndEmpty(@TempDir Path dir) {
        Path missing = dir.resolve("does-not-exist");
        ConfigMapConfigSource source = new ConfigMapConfigSource(missing.toString());
        assertThat(source.isEnabled()).isFalse();
        assertThat(source.loadConfig()).isEmpty();
        assertThat(source.refresh()).isFalse();
        source.close();
    }

    @Test
    void refreshPicksUpNewFiles(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a"), "1");
        ConfigMapConfigSource source = new ConfigMapConfigSource(dir.toString());
        assertThat(source.loadConfig()).hasSize(1);

        Files.writeString(dir.resolve("b"), "2");
        assertThat(source.refresh()).isTrue();
        assertThat(source.loadConfig()).hasSize(2);
        source.close();
    }

    @Test
    void watchReloadsOnFileChange(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a"), "1");
        ConfigMapConfigSource source = new ConfigMapConfigSource(dir.toString(), 130, true);
        assertThat(source.getProperty("a")).contains("1");

        Files.writeString(dir.resolve("b"), "2");

        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(source.getProperty("b")).contains("2"));
        source.close();
    }
}
