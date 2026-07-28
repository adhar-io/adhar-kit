package com.adhar.kit.profiler.jfr;

import com.adhar.kit.profiler.config.PerfProfilerProperties;
import com.adhar.kit.profiler.model.JfrStatus;
import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Manages a continuous Java Flight Recorder recording for the profiler.
 *
 * <p>The recording is disk-backed and bounded by the configured max size / max age, so it can
 * run continuously with flat resource usage. On-demand dumps are written to the configured dump
 * directory, which is rotated so that at most {@code maxDumpFiles} dumps are retained (oldest
 * deleted first). All operations degrade gracefully when JFR is unavailable in the running JVM.
 */
@Slf4j
public class JfrRecordingManager implements AutoCloseable {

    private static final String RECORDING_NAME = "adhar-kit-continuous";
    private static final String DUMP_FILE_PREFIX = "adhar-profiler-";
    private static final String DUMP_FILE_SUFFIX = ".jfr";
    private static final DateTimeFormatter DUMP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final PerfProfilerProperties.Jfr properties;
    private final AtomicLong dumpSequence = new AtomicLong();
    private final Object lock = new Object();
    private Recording recording;

    public JfrRecordingManager(PerfProfilerProperties.Jfr properties) {
        this.properties = properties;
    }

    /**
     * Returns whether Flight Recorder is available in the running JVM.
     */
    public boolean isJfrAvailable() {
        try {
            return FlightRecorder.isAvailable();
        } catch (RuntimeException | LinkageError e) {
            log.debug("Flight Recorder availability check failed", e);
            return false;
        }
    }

    /**
     * Starts the continuous recording using the configured settings preset, max size, and max age.
     *
     * @return true if a new recording was started, false if JFR is unavailable or a recording
     *         is already running
     */
    public boolean start() {
        synchronized (lock) {
            if (!isJfrAvailable()) {
                log.warn("Cannot start JFR recording: Flight Recorder is not available in this JVM");
                return false;
            }
            if (recording != null) {
                log.debug("JFR recording [{}] is already running", RECORDING_NAME);
                return false;
            }
            Recording newRecording = createRecording();
            newRecording.setName(RECORDING_NAME);
            newRecording.setToDisk(true);
            newRecording.setDumpOnExit(false);
            if (properties.getMaxSizeMb() > 0) {
                newRecording.setMaxSize(properties.getMaxSizeMb() * 1024 * 1024);
            }
            Duration maxAge = properties.getMaxAge();
            if (maxAge != null && !maxAge.isZero() && !maxAge.isNegative()) {
                newRecording.setMaxAge(maxAge);
            }
            newRecording.start();
            recording = newRecording;
            log.info("Started continuous JFR recording [{}] (settings={}, maxSizeMb={}, maxAge={})",
                    RECORDING_NAME, properties.getSettings(), properties.getMaxSizeMb(), maxAge);
            return true;
        }
    }

    /**
     * Stops and closes the continuous recording.
     *
     * @return true if a running recording was stopped, false if none was running
     */
    public boolean stop() {
        synchronized (lock) {
            if (recording == null) {
                return false;
            }
            try {
                recording.stop();
            } catch (IllegalStateException e) {
                log.debug("JFR recording was not in a stoppable state", e);
            } finally {
                recording.close();
                recording = null;
            }
            log.info("Stopped continuous JFR recording [{}]", RECORDING_NAME);
            return true;
        }
    }

    /**
     * Dumps the running recording's buffered data to a new file in the configured dump
     * directory, then rotates the directory down to {@code maxDumpFiles} dumps.
     *
     * @return the dump file path, or empty if no recording is running or the dump failed
     */
    public Optional<Path> dump() {
        synchronized (lock) {
            if (recording == null) {
                return Optional.empty();
            }
            try {
                Path directory = Files.createDirectories(Path.of(properties.getDumpDirectory()));
                Path dumpFile = directory.resolve(DUMP_FILE_PREFIX
                        + DUMP_TIMESTAMP.format(Instant.now())
                        + "-" + dumpSequence.incrementAndGet()
                        + DUMP_FILE_SUFFIX);
                recording.dump(dumpFile);
                pruneOldDumps(directory);
                log.info("JFR dump written to {}", dumpFile);
                return Optional.of(dumpFile);
            } catch (IOException e) {
                log.warn("Failed to dump JFR recording", e);
                return Optional.empty();
            }
        }
    }

    /**
     * Returns the most recent dump file in the configured dump directory, if any.
     */
    public Optional<Path> latestDump() {
        return listDumps().stream().findFirst();
    }

    /**
     * Returns the current recording status, including retained dump files (newest first).
     */
    public JfrStatus status() {
        synchronized (lock) {
            return new JfrStatus(
                    isJfrAvailable(),
                    recording != null,
                    properties.getSettings(),
                    properties.getMaxSizeMb(),
                    properties.getMaxAge(),
                    properties.getDumpDirectory(),
                    listDumps().stream().map(path -> path.getFileName().toString()).toList()
            );
        }
    }

    /**
     * Stops the recording when the managing context shuts down.
     */
    @Override
    public void close() {
        stop();
    }

    private Recording createRecording() {
        try {
            return new Recording(Configuration.getConfiguration(properties.getSettings()));
        } catch (IOException | ParseException e) {
            log.warn("Unknown or unreadable JFR settings preset [{}], starting recording without a preset",
                    properties.getSettings(), e);
            return new Recording();
        }
    }

    /**
     * Lists retained dump files, newest first. Dump file names embed a UTC timestamp plus a
     * monotonically increasing sequence, so lexicographic order matches creation order.
     */
    private List<Path> listDumps() {
        Path directory = Path.of(properties.getDumpDirectory());
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(DUMP_FILE_SUFFIX))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to list JFR dump directory {}", directory, e);
            return List.of();
        }
    }

    private void pruneOldDumps(Path directory) {
        List<Path> dumps = listDumps();
        int maxDumpFiles = Math.max(properties.getMaxDumpFiles(), 1);
        for (int i = maxDumpFiles; i < dumps.size(); i++) {
            try {
                Files.deleteIfExists(dumps.get(i));
                log.debug("Deleted rotated JFR dump {}", dumps.get(i));
            } catch (IOException e) {
                log.warn("Failed to delete rotated JFR dump {}", dumps.get(i), e);
            }
        }
    }
}
