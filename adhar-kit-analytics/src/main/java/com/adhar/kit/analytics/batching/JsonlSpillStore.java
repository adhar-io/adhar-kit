package com.adhar.kit.analytics.batching;

import com.adhar.kit.analytics.client.CaptureEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * File-based {@link SpillStore} that appends failed batches as JSON Lines
 * (one JSON array of events per line) to a single file inside a configurable
 * directory. OFF by default - only used when
 * {@code adhar.analytics.post-hog.spill-enabled=true} and a directory is set.
 *
 * <p>Each event is stored as a plain map ({@code event}, {@code distinctId},
 * {@code properties}, ISO-8601 {@code timestamp}) so no special Jackson
 * modules are required. On startup {@link #loadAndClear()} reads every line,
 * reconstructs the batches and deletes the file so events are replayed once.</p>
 *
 * <p><b>Limits</b>: this is a simple single-file, append-only spill intended as
 * a crash/offline safety net, not a durable queue. Writes are serialized via an
 * intrinsic lock; the file is only pruned by {@link #loadAndClear()}, so it can
 * grow while the process is offline. Malformed lines are skipped on load.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class JsonlSpillStore implements SpillStore {

    private static final String FILE_NAME = "analytics-spill.jsonl";
    private static final TypeReference<List<Map<String, Object>>> LINE_TYPE = new TypeReference<>() {
    };

    private final Path file;
    private final ObjectMapper mapper;
    private final Object lock = new Object();

    public JsonlSpillStore(Path directory) {
        this(directory, new ObjectMapper());
    }

    public JsonlSpillStore(Path directory, ObjectMapper mapper) {
        this.mapper = mapper;
        this.file = directory.resolve(FILE_NAME);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create analytics spill directory: " + directory, e);
        }
    }

    /** Exposed for diagnostics/tests. */
    public Path getFile() {
        return file;
    }

    @Override
    public void write(List<CaptureEvent> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        List<Map<String, Object>> serialized = new ArrayList<>(batch.size());
        for (CaptureEvent event : batch) {
            serialized.add(toMap(event));
        }
        synchronized (lock) {
            try {
                String line = mapper.writeValueAsString(serialized) + System.lineSeparator();
                Files.writeString(file, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.error("Failed to spill analytics batch to {}", file, e);
                throw new UncheckedIOException("Failed to spill analytics batch", e);
            }
        }
    }

    @Override
    public List<List<CaptureEvent>> loadAndClear() {
        synchronized (lock) {
            if (!Files.exists(file)) {
                return List.of();
            }
            List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Failed to read analytics spill file {}", file, e);
                return List.of();
            }

            List<List<CaptureEvent>> batches = new ArrayList<>();
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                try {
                    List<Map<String, Object>> events = mapper.readValue(line, LINE_TYPE);
                    List<CaptureEvent> batch = new ArrayList<>(events.size());
                    for (Map<String, Object> map : events) {
                        batch.add(fromMap(map));
                    }
                    if (!batch.isEmpty()) {
                        batches.add(batch);
                    }
                } catch (Exception e) {
                    log.warn("Skipping malformed analytics spill line", e);
                }
            }

            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("Failed to delete analytics spill file {} after load", file, e);
            }
            return batches;
        }
    }

    private static Map<String, Object> toMap(CaptureEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("event", event.event());
        map.put("distinctId", event.distinctId());
        map.put("properties", event.properties());
        map.put("timestamp", event.timestamp() != null ? event.timestamp().toString() : null);
        return map;
    }

    @SuppressWarnings("unchecked")
    private static CaptureEvent fromMap(Map<String, Object> map) {
        String event = (String) map.get("event");
        String distinctId = (String) map.get("distinctId");
        Object propsObj = map.get("properties");
        Map<String, Object> properties = propsObj instanceof Map ? (Map<String, Object>) propsObj : Map.of();
        Object ts = map.get("timestamp");
        Instant timestamp = ts != null ? Instant.parse(ts.toString()) : Instant.now();
        return new CaptureEvent(event, distinctId, properties, timestamp);
    }
}
