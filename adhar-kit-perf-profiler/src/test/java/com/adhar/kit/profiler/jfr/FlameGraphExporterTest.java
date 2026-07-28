package com.adhar.kit.profiler.jfr;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FlameGraphExporterTest {

    @TempDir
    Path tempDir;

    private final FlameGraphExporter exporter = new FlameGraphExporter();

    @Test
    @DisplayName("render emits one 'stack count' line per collapsed stack")
    void renderFormatsLines() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("a;b;c", 3L);
        counts.put("a;b;d", 1L);

        String rendered = FlameGraphExporter.render(counts);

        assertThat(rendered).isEqualTo("a;b;c 3\na;b;d 1\n");
    }

    @Test
    @DisplayName("render of an empty map yields empty text")
    void renderEmpty() {
        assertThat(FlameGraphExporter.render(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("exportCollapsed on a recording without execution samples yields empty text")
    void exportCollapsedNoExecutionSamples() throws IOException {
        assumeTrue(jfrAvailable(), "JFR not available in this JVM");

        Path jfr = tempDir.resolve("no-samples.jfr");
        // A recording with no settings enables no events, so there are no execution samples.
        try (Recording recording = new Recording()) {
            recording.setToDisk(true);
            recording.start();
            recording.stop();
            recording.dump(jfr);
        }

        String collapsed = exporter.exportCollapsed(jfr);

        assertThat(collapsed).isEmpty();
    }

    @Test
    @DisplayName("exportCollapsed parses execution samples into root-first collapsed stacks")
    void exportCollapsedWithExecutionSamples() throws IOException {
        assumeTrue(jfrAvailable(), "JFR not available in this JVM");

        Path jfr = tempDir.resolve("samples.jfr");
        try (Recording recording = new Recording()) {
            recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(5));
            recording.setToDisk(true);
            recording.start();
            burnCpu();
            recording.stop();
            recording.dump(jfr);
        }

        String collapsed = exporter.exportCollapsed(jfr);

        // Sampling is best-effort; only assert structure when samples were actually captured.
        assumeTrue(!collapsed.isEmpty(), "No execution samples were captured in this run");
        for (String line : collapsed.split("\n")) {
            assertThat(line).matches(".+ \\d+");
            String[] parts = line.split(" ");
            assertThat(Long.parseLong(parts[parts.length - 1])).isPositive();
            // Frames are semicolon-joined, root-first, as method references (Type.method).
            assertThat(line.substring(0, line.lastIndexOf(' '))).contains(".");
        }
    }

    @Test
    @DisplayName("exportCollapsed throws IOException for a file that is not a valid JFR recording")
    void exportCollapsedInvalidFile() throws IOException {
        Path bogus = tempDir.resolve("not-a-recording.jfr");
        Files.writeString(bogus, "this is not a JFR file");

        assertThatThrownBy(() -> exporter.exportCollapsed(bogus))
                .isInstanceOf(IOException.class);
    }

    private static boolean jfrAvailable() {
        try {
            return FlightRecorder.isAvailable();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static void burnCpu() {
        long deadline = System.nanoTime() + java.time.Duration.ofMillis(600).toNanos();
        long sink = 0;
        while (System.nanoTime() < deadline) {
            for (int i = 0; i < 100_000; i++) {
                sink += (long) Math.sqrt(i) * (i | 1);
            }
        }
        if (sink == Long.MIN_VALUE) {
            throw new IllegalStateException("unreachable");
        }
    }
}
