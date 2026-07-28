package com.adhar.kit.analytics.batching;

import com.adhar.kit.analytics.client.CaptureEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonlSpillStore Tests")
class JsonlSpillStoreTest {

    @Test
    @DisplayName("round-trips a batch preserving event, distinctId, properties and timestamp")
    void roundTrip(@TempDir Path dir) {
        JsonlSpillStore store = new JsonlSpillStore(dir);
        Instant ts = Instant.parse("2024-01-01T10:15:30Z");
        CaptureEvent e1 = new CaptureEvent("Purchase", "u1", Map.of("amount", 10, "currency", "USD"), ts);
        CaptureEvent e2 = new CaptureEvent("$pageview", "u2", Map.of(), ts);

        store.write(List.of(e1, e2));

        List<List<CaptureEvent>> loaded = store.loadAndClear();
        assertEquals(1, loaded.size());
        List<CaptureEvent> batch = loaded.get(0);
        assertEquals(2, batch.size());
        assertEquals("Purchase", batch.get(0).event());
        assertEquals("u1", batch.get(0).distinctId());
        assertEquals(10, batch.get(0).properties().get("amount"));
        assertEquals("USD", batch.get(0).properties().get("currency"));
        assertEquals(ts, batch.get(0).timestamp());
        assertEquals("$pageview", batch.get(1).event());
    }

    @Test
    @DisplayName("multiple batches are stored as separate JSONL lines and all reloaded")
    void multipleBatches(@TempDir Path dir) {
        JsonlSpillStore store = new JsonlSpillStore(dir);
        store.write(List.of(CaptureEvent.of("a", "u", Map.of())));
        store.write(List.of(CaptureEvent.of("b", "u", Map.of()), CaptureEvent.of("c", "u", Map.of())));

        List<List<CaptureEvent>> loaded = store.loadAndClear();
        assertEquals(2, loaded.size());
        assertEquals(1, loaded.get(0).size());
        assertEquals(2, loaded.get(1).size());
    }

    @Test
    @DisplayName("loadAndClear deletes the spill file so batches are replayed only once")
    void loadClears(@TempDir Path dir) {
        JsonlSpillStore store = new JsonlSpillStore(dir);
        store.write(List.of(CaptureEvent.of("a", "u", Map.of())));
        assertTrue(Files.exists(store.getFile()));

        assertFalse(store.loadAndClear().isEmpty());
        assertFalse(Files.exists(store.getFile()));
        assertTrue(store.loadAndClear().isEmpty());
    }

    @Test
    @DisplayName("loading when no file exists yields an empty list")
    void emptyWhenNoFile(@TempDir Path dir) {
        JsonlSpillStore store = new JsonlSpillStore(dir);
        assertTrue(store.loadAndClear().isEmpty());
    }

    @Test
    @DisplayName("empty/null batch writes are ignored")
    void ignoresEmptyWrites(@TempDir Path dir) {
        JsonlSpillStore store = new JsonlSpillStore(dir);
        store.write(List.of());
        store.write(null);
        assertFalse(Files.exists(store.getFile()));
    }

    @Test
    @DisplayName("malformed lines are skipped on load")
    void skipsMalformedLines(@TempDir Path dir) throws Exception {
        JsonlSpillStore store = new JsonlSpillStore(dir);
        store.write(List.of(CaptureEvent.of("good", "u", Map.of())));
        // Append a bad line
        Files.writeString(store.getFile(), "this is not json\n",
                java.nio.file.StandardOpenOption.APPEND);

        List<List<CaptureEvent>> loaded = store.loadAndClear();
        assertEquals(1, loaded.size());
        assertEquals("good", loaded.get(0).get(0).event());
    }

    @Test
    @DisplayName("creates the spill directory if it does not exist")
    void createsDirectory(@TempDir Path dir) {
        Path nested = dir.resolve("a/b/c");
        JsonlSpillStore store = new JsonlSpillStore(nested);
        store.write(List.of(CaptureEvent.of("a", "u", Map.of())));
        assertTrue(Files.exists(nested));
    }
}
