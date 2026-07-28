package com.adhar.kit.profiler.contention;

import com.adhar.kit.profiler.config.PerfProfilerProperties;
import com.adhar.kit.profiler.model.ContentionSnapshot;
import com.adhar.kit.profiler.model.ContentionSnapshot.ContendedThread;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThreadContentionCollectorTest {

    private static PerfProfilerProperties.Contention props(boolean timeMonitoring, int topThreads) {
        PerfProfilerProperties.Contention p = new PerfProfilerProperties.Contention();
        p.setTimeMonitoringEnabled(timeMonitoring);
        p.setTopThreads(topThreads);
        return p;
    }

    private static ThreadInfo mockInfo(long id, String name, Thread.State state,
                                       long blockedCount, long blockedTime,
                                       long waitedCount, long waitedTime) {
        ThreadInfo info = mock(ThreadInfo.class);
        lenient().when(info.getThreadId()).thenReturn(id);
        lenient().when(info.getThreadName()).thenReturn(name);
        lenient().when(info.getThreadState()).thenReturn(state);
        lenient().when(info.getBlockedCount()).thenReturn(blockedCount);
        lenient().when(info.getBlockedTime()).thenReturn(blockedTime);
        lenient().when(info.getWaitedCount()).thenReturn(waitedCount);
        lenient().when(info.getWaitedTime()).thenReturn(waitedTime);
        return info;
    }

    // ------------------------------------------------------------------
    // Real ThreadMXBean (public constructor)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("snapshot against the live JVM returns ranked live threads bounded by topN")
    void liveSnapshotBounded() {
        ThreadContentionCollector collector = new ThreadContentionCollector(props(false, 10));

        ContentionSnapshot snapshot = collector.snapshot(3);

        assertThat(snapshot.capturedAt()).isNotNull();
        assertThat(snapshot.topContendedThreads()).isNotEmpty();
        assertThat(snapshot.topContendedThreads()).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("null/non-positive topN falls back to the configured default")
    void liveSnapshotDefaultTopN() {
        ThreadContentionCollector collector = new ThreadContentionCollector(props(false, 2));

        assertThat(collector.snapshot(null).topContendedThreads()).hasSizeLessThanOrEqualTo(2);
        assertThat(collector.snapshot(0).topContendedThreads()).hasSizeLessThanOrEqualTo(2);
        assertThat(collector.snapshot(-5).topContendedThreads()).hasSizeLessThanOrEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Mocked ThreadMXBean (package-private seam)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("snapshot skips null ThreadInfo and ranks by blocked+waited time when monitoring is on")
    void mockedSnapshotRanksByTime() {
        ThreadMXBean bean = mock(ThreadMXBean.class);
        when(bean.isThreadContentionMonitoringSupported()).thenReturn(true);
        when(bean.isThreadContentionMonitoringEnabled()).thenReturn(true);
        when(bean.getAllThreadIds()).thenReturn(new long[]{1, 2, 3});

        ThreadInfo low = mockInfo(1, "low", Thread.State.RUNNABLE, 1, 5, 1, 5);
        ThreadInfo high = mockInfo(2, "high", Thread.State.BLOCKED, 2, 100, 3, 200);
        when(bean.getThreadInfo(new long[]{1, 2, 3}))
                .thenReturn(new ThreadInfo[]{low, high, null});

        ThreadContentionCollector collector = new ThreadContentionCollector(props(true, 10), bean);
        ContentionSnapshot snapshot = collector.snapshot(null);

        assertThat(snapshot.monitoringSupported()).isTrue();
        assertThat(snapshot.timeMonitoringEnabled()).isTrue();
        // null skipped -> only two threads, ranked highest contention first.
        List<ContendedThread> threads = snapshot.topContendedThreads();
        assertThat(threads).hasSize(2);
        assertThat(threads.getFirst().threadName()).isEqualTo("high");
        assertThat(threads.getFirst().threadState()).isEqualTo("BLOCKED");
        // On the first snapshot the delta equals the cumulative value (baseline was 0).
        assertThat(threads.getFirst().blockedTimeMsDelta()).isEqualTo(100);
        assertThat(threads.getFirst().waitedCountDelta()).isEqualTo(3);
    }

    @Test
    @DisplayName("a second snapshot reports deltas relative to the previous sample and prunes dead threads")
    void mockedSnapshotDeltas() {
        ThreadMXBean bean = mock(ThreadMXBean.class);
        when(bean.isThreadContentionMonitoringSupported()).thenReturn(true);
        when(bean.isThreadContentionMonitoringEnabled()).thenReturn(true);
        ThreadContentionCollector collector = new ThreadContentionCollector(props(true, 10), bean);

        // First sample: threads 1 and 2 present.
        ThreadInfo[] first = {
                mockInfo(1, "t1", Thread.State.RUNNABLE, 5, 50, 5, 50),
                mockInfo(2, "t2", Thread.State.WAITING, 1, 10, 1, 10)
        };
        when(bean.getAllThreadIds()).thenReturn(new long[]{1, 2});
        when(bean.getThreadInfo(new long[]{1, 2})).thenReturn(first);
        collector.snapshot(null);

        // Second sample: thread 2 has died, thread 1's counters advanced.
        ThreadInfo[] secondInfos = {
                mockInfo(1, "t1", Thread.State.RUNNABLE, 8, 80, 9, 90)
        };
        when(bean.getAllThreadIds()).thenReturn(new long[]{1});
        when(bean.getThreadInfo(new long[]{1})).thenReturn(secondInfos);
        ContentionSnapshot second = collector.snapshot(null);

        assertThat(second.topContendedThreads()).hasSize(1);
        ContendedThread t1 = second.topContendedThreads().getFirst();
        assertThat(t1.blockedCount()).isEqualTo(8);
        assertThat(t1.blockedCountDelta()).isEqualTo(3);   // 8 - 5
        assertThat(t1.blockedTimeMsDelta()).isEqualTo(30); // 80 - 50
        assertThat(t1.waitedCountDelta()).isEqualTo(4);    // 9 - 5
        assertThat(t1.waitedTimeMsDelta()).isEqualTo(40);  // 90 - 50
    }

    @Test
    @DisplayName("when time monitoring is off, times are -1 and ranking falls back to blocked+waited counts")
    void mockedSnapshotRanksByCountsWhenTimesUnavailable() {
        ThreadMXBean bean = mock(ThreadMXBean.class);
        when(bean.isThreadContentionMonitoringSupported()).thenReturn(false);
        lenient().when(bean.isThreadContentionMonitoringEnabled()).thenReturn(false);
        // -1 times signal that contention time monitoring is unavailable.
        ThreadInfo[] infos = {
                mockInfo(1, "few", Thread.State.RUNNABLE, 1, -1, 1, -1),
                mockInfo(2, "many", Thread.State.BLOCKED, 40, -1, 60, -1)
        };
        when(bean.getAllThreadIds()).thenReturn(new long[]{1, 2});
        when(bean.getThreadInfo(new long[]{1, 2})).thenReturn(infos);

        ThreadContentionCollector collector = new ThreadContentionCollector(props(false, 10), bean);
        ContentionSnapshot snapshot = collector.snapshot(null);

        assertThat(snapshot.monitoringSupported()).isFalse();
        assertThat(snapshot.timeMonitoringEnabled()).isFalse();
        List<ContendedThread> threads = snapshot.topContendedThreads();
        assertThat(threads.getFirst().threadName()).isEqualTo("many");
        // -1 time deltas remain -1 (never turned into a positive delta).
        assertThat(threads.getFirst().blockedTimeMs()).isEqualTo(-1);
        assertThat(threads.getFirst().blockedTimeMsDelta()).isEqualTo(-1);
    }

    @Test
    @DisplayName("topN limits the number of reported threads")
    void mockedSnapshotTopN() {
        ThreadMXBean bean = mock(ThreadMXBean.class);
        lenient().when(bean.isThreadContentionMonitoringSupported()).thenReturn(true);
        lenient().when(bean.isThreadContentionMonitoringEnabled()).thenReturn(true);
        ThreadInfo[] infos = {
                mockInfo(1, "a", Thread.State.RUNNABLE, 3, 30, 3, 30),
                mockInfo(2, "b", Thread.State.RUNNABLE, 2, 20, 2, 20),
                mockInfo(3, "c", Thread.State.RUNNABLE, 1, 10, 1, 10)
        };
        when(bean.getAllThreadIds()).thenReturn(new long[]{1, 2, 3});
        when(bean.getThreadInfo(new long[]{1, 2, 3})).thenReturn(infos);

        ThreadContentionCollector collector = new ThreadContentionCollector(props(true, 10), bean);

        assertThat(collector.snapshot(1).topContendedThreads()).hasSize(1);
    }

    // ------------------------------------------------------------------
    // Constructor time-monitoring enablement branches
    // ------------------------------------------------------------------

    @Test
    @DisplayName("constructor enables contention time monitoring when configured, supported, and not yet enabled")
    void constructorEnablesMonitoring() {
        ThreadMXBean bean = mock(ThreadMXBean.class);
        when(bean.isThreadContentionMonitoringSupported()).thenReturn(true);
        when(bean.isThreadContentionMonitoringEnabled()).thenReturn(false);

        new ThreadContentionCollector(props(true, 10), bean);

        verify(bean).setThreadContentionMonitoringEnabled(true);
    }

    @Test
    @DisplayName("constructor does not re-enable monitoring that is already enabled")
    void constructorSkipsWhenAlreadyEnabled() {
        ThreadMXBean bean = mock(ThreadMXBean.class);
        when(bean.isThreadContentionMonitoringSupported()).thenReturn(true);
        when(bean.isThreadContentionMonitoringEnabled()).thenReturn(true);

        new ThreadContentionCollector(props(true, 10), bean);

        verify(bean, never()).setThreadContentionMonitoringEnabled(true);
    }

    @Test
    @DisplayName("constructor logs and skips enabling when monitoring is unsupported")
    void constructorHandlesUnsupportedMonitoring() {
        ThreadMXBean bean = mock(ThreadMXBean.class);
        when(bean.isThreadContentionMonitoringSupported()).thenReturn(false);

        new ThreadContentionCollector(props(true, 10), bean);

        verify(bean, never()).setThreadContentionMonitoringEnabled(true);
    }

    @Test
    @DisplayName("constructor leaves monitoring untouched when time monitoring is disabled in config")
    void constructorSkipsWhenDisabled() {
        ThreadMXBean bean = mock(ThreadMXBean.class);

        new ThreadContentionCollector(props(false, 10), bean);

        verify(bean, never()).isThreadContentionMonitoringSupported();
        verify(bean, never()).setThreadContentionMonitoringEnabled(true);
    }
}
