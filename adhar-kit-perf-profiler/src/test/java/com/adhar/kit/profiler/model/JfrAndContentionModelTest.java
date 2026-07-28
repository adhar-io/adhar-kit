package com.adhar.kit.profiler.model;

import com.adhar.kit.profiler.model.ContentionSnapshot.ContendedThread;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JfrAndContentionModelTest {

    @Test
    @DisplayName("JfrStatus exposes its components and honours record equality")
    void jfrStatusRecord() {
        JfrStatus a = new JfrStatus(true, false, "default", 100, Duration.ofHours(1),
                "/tmp/jfr", List.of("adhar-profiler-1.jfr"));
        JfrStatus b = new JfrStatus(true, false, "default", 100, Duration.ofHours(1),
                "/tmp/jfr", List.of("adhar-profiler-1.jfr"));

        assertThat(a.available()).isTrue();
        assertThat(a.running()).isFalse();
        assertThat(a.settings()).isEqualTo("default");
        assertThat(a.maxSizeMb()).isEqualTo(100);
        assertThat(a.maxAge()).isEqualTo(Duration.ofHours(1));
        assertThat(a.dumpDirectory()).isEqualTo("/tmp/jfr");
        assertThat(a.dumpFiles()).containsExactly("adhar-profiler-1.jfr");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.toString()).contains("default");
    }

    @Test
    @DisplayName("ContentionSnapshot and ContendedThread expose their components")
    void contentionSnapshotRecord() {
        Instant now = Instant.now();
        ContendedThread thread = new ContendedThread(
                7, "worker-1", "BLOCKED", 10, 100, 20, 200, 3, 30, 4, 40);
        ContentionSnapshot snapshot = new ContentionSnapshot(true, true, List.of(thread), now);

        assertThat(snapshot.monitoringSupported()).isTrue();
        assertThat(snapshot.timeMonitoringEnabled()).isTrue();
        assertThat(snapshot.capturedAt()).isEqualTo(now);
        assertThat(snapshot.topContendedThreads()).containsExactly(thread);

        assertThat(thread.threadId()).isEqualTo(7);
        assertThat(thread.threadName()).isEqualTo("worker-1");
        assertThat(thread.threadState()).isEqualTo("BLOCKED");
        assertThat(thread.blockedCount()).isEqualTo(10);
        assertThat(thread.blockedTimeMs()).isEqualTo(100);
        assertThat(thread.waitedCount()).isEqualTo(20);
        assertThat(thread.waitedTimeMs()).isEqualTo(200);
        assertThat(thread.blockedCountDelta()).isEqualTo(3);
        assertThat(thread.blockedTimeMsDelta()).isEqualTo(30);
        assertThat(thread.waitedCountDelta()).isEqualTo(4);
        assertThat(thread.waitedTimeMsDelta()).isEqualTo(40);

        assertThat(snapshot).isEqualTo(new ContentionSnapshot(true, true, List.of(thread), now));
        assertThat(thread.toString()).contains("worker-1");
    }
}
