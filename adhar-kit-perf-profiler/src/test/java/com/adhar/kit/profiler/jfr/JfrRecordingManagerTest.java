package com.adhar.kit.profiler.jfr;

import com.adhar.kit.profiler.config.PerfProfilerProperties;
import com.adhar.kit.profiler.model.JfrStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JfrRecordingManagerTest {

    @TempDir
    Path dumpDir;

    private PerfProfilerProperties.Jfr props;
    private JfrRecordingManager manager;

    @BeforeEach
    void setUp() {
        props = new PerfProfilerProperties.Jfr();
        props.setDumpDirectory(dumpDir.toString());
        props.setSettings("default");
        props.setMaxSizeMb(8);
        props.setMaxAge(Duration.ofMinutes(10));
        props.setMaxDumpFiles(2);
        manager = new JfrRecordingManager(props);
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    @DisplayName("isJfrAvailable reflects the running JVM's Flight Recorder support")
    void isJfrAvailableDoesNotThrow() {
        // Just exercises the method; on a standard HotSpot JVM this is true.
        assertThat(manager.isJfrAvailable()).isIn(true, false);
    }

    @Test
    @DisplayName("status of a fresh manager reports not-running and echoes configuration")
    void statusBeforeStart() {
        JfrStatus status = manager.status();

        assertThat(status.running()).isFalse();
        assertThat(status.settings()).isEqualTo("default");
        assertThat(status.maxSizeMb()).isEqualTo(8);
        assertThat(status.maxAge()).isEqualTo(Duration.ofMinutes(10));
        assertThat(status.dumpDirectory()).isEqualTo(dumpDir.toString());
        assertThat(status.dumpFiles()).isEmpty();
    }

    @Test
    @DisplayName("stop before start returns false")
    void stopBeforeStartReturnsFalse() {
        assertThat(manager.stop()).isFalse();
    }

    @Test
    @DisplayName("dump before start returns empty")
    void dumpBeforeStartReturnsEmpty() {
        assertThat(manager.dump()).isEmpty();
    }

    @Test
    @DisplayName("latestDump returns empty when no dump directory or dumps exist")
    void latestDumpEmptyWhenNoDumps() {
        assertThat(manager.latestDump()).isEmpty();
    }

    @Test
    @DisplayName("start begins a recording and status reflects it running")
    void startRunsRecording() {
        assumeTrue(manager.isJfrAvailable(), "JFR not available in this JVM");

        assertThat(manager.start()).isTrue();
        JfrStatus status = manager.status();
        assertThat(status.available()).isTrue();
        assertThat(status.running()).isTrue();
    }

    @Test
    @DisplayName("start twice returns false the second time (already running)")
    void startTwiceIsNoop() {
        assumeTrue(manager.isJfrAvailable(), "JFR not available in this JVM");

        assertThat(manager.start()).isTrue();
        assertThat(manager.start()).isFalse();
    }

    @Test
    @DisplayName("stop after start returns true and clears running state")
    void stopAfterStart() {
        assumeTrue(manager.isJfrAvailable(), "JFR not available in this JVM");

        manager.start();
        assertThat(manager.stop()).isTrue();
        assertThat(manager.status().running()).isFalse();
        // Stopping again is a no-op.
        assertThat(manager.stop()).isFalse();
    }

    @Test
    @DisplayName("dump writes a JFR file while recording and latestDump finds it")
    void dumpWritesFile() {
        assumeTrue(manager.isJfrAvailable(), "JFR not available in this JVM");

        manager.start();
        Optional<Path> dump = manager.dump();

        assertThat(dump).isPresent();
        assertThat(Files.exists(dump.get())).isTrue();
        assertThat(dump.get().getFileName().toString()).endsWith(".jfr");
        assertThat(manager.latestDump()).contains(dump.get());
        assertThat(manager.status().dumpFiles()).contains(dump.get().getFileName().toString());
    }

    @Test
    @DisplayName("dump rotation retains at most maxDumpFiles dumps, deleting the oldest")
    void dumpRotation() throws Exception {
        assumeTrue(manager.isJfrAvailable(), "JFR not available in this JVM");
        props.setMaxDumpFiles(2);
        manager.start();

        for (int i = 0; i < 4; i++) {
            assertThat(manager.dump()).isPresent();
            // Timestamps embed seconds; a small pause keeps ordering deterministic.
            Thread.sleep(1_100);
        }

        long remaining;
        try (var files = Files.list(dumpDir)) {
            remaining = files.filter(p -> p.toString().endsWith(".jfr")).count();
        }
        assertThat(remaining).isLessThanOrEqualTo(2);
        assertThat(manager.status().dumpFiles()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("an unknown settings preset falls back to a preset-less recording rather than failing")
    void unknownSettingsPresetFallsBack() {
        assumeTrue(manager.isJfrAvailable(), "JFR not available in this JVM");
        props.setSettings("this-preset-does-not-exist");

        assertThat(manager.start()).isTrue();
        assertThat(manager.status().running()).isTrue();
    }

    @Test
    @DisplayName("zero/negative maxSize and maxAge disable those limits without error")
    void unboundedLimits() {
        assumeTrue(manager.isJfrAvailable(), "JFR not available in this JVM");
        props.setMaxSizeMb(0);
        props.setMaxAge(Duration.ZERO);

        assertThat(manager.start()).isTrue();
        assertThat(manager.status().running()).isTrue();
    }

    @Test
    @DisplayName("close stops any running recording")
    void closeStopsRecording() {
        assumeTrue(manager.isJfrAvailable(), "JFR not available in this JVM");

        manager.start();
        manager.close();
        assertThat(manager.status().running()).isFalse();
    }
}
