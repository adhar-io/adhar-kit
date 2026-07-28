package com.adhar.kit.profiler.contention;

import com.adhar.kit.profiler.config.PerfProfilerProperties;
import com.adhar.kit.profiler.model.ContentionSnapshot;
import com.adhar.kit.profiler.model.ContentionSnapshot.ContendedThread;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects thread-contention analytics from {@link ThreadMXBean}.
 *
 * <p>Blocked/waited counts are always tracked by the JVM; blocked/waited times additionally
 * require thread contention time monitoring, which this collector enables at construction when
 * configured and supported. Each snapshot reports the top-N most contended live threads plus
 * per-thread deltas since the previous snapshot, so both cumulative totals and recent activity
 * are visible.
 */
@Slf4j
public class ThreadContentionCollector {

    private final PerfProfilerProperties.Contention properties;
    private final ThreadMXBean threadMXBean;
    private final Map<Long, Baseline> baselines = new HashMap<>();

    public ThreadContentionCollector(PerfProfilerProperties.Contention properties) {
        this(properties, ManagementFactory.getThreadMXBean());
    }

    /**
     * Package-private seam so tests can supply a custom {@link ThreadMXBean}.
     */
    ThreadContentionCollector(PerfProfilerProperties.Contention properties, ThreadMXBean threadMXBean) {
        this.properties = properties;
        this.threadMXBean = threadMXBean;
        if (properties.isTimeMonitoringEnabled()) {
            if (threadMXBean.isThreadContentionMonitoringSupported()) {
                if (!threadMXBean.isThreadContentionMonitoringEnabled()) {
                    threadMXBean.setThreadContentionMonitoringEnabled(true);
                    log.info("Thread contention time monitoring enabled");
                }
            } else {
                log.warn("Thread contention time monitoring is not supported by this JVM");
            }
        }
    }

    /**
     * Samples all live threads and returns the top-N most contended ones.
     *
     * <p>Threads are ranked by blocked+waited time when contention time monitoring is enabled,
     * falling back to blocked+waited counts otherwise. Baselines of threads that have died are
     * dropped so memory stays bounded by the number of live threads.
     *
     * @param topN number of threads to report, or {@code null}/non-positive for the configured
     *             default
     */
    public synchronized ContentionSnapshot snapshot(Integer topN) {
        int limit = (topN != null && topN > 0) ? topN : properties.getTopThreads();
        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds());

        List<ContendedThread> threads = new ArrayList<>();
        Set<Long> liveThreadIds = new HashSet<>();
        for (ThreadInfo info : threadInfos) {
            if (info == null) {
                continue; // thread died between id enumeration and info lookup
            }
            liveThreadIds.add(info.getThreadId());
            Baseline previous = baselines.get(info.getThreadId());
            threads.add(new ContendedThread(
                    info.getThreadId(),
                    info.getThreadName(),
                    info.getThreadState().name(),
                    info.getBlockedCount(),
                    info.getBlockedTime(),
                    info.getWaitedCount(),
                    info.getWaitedTime(),
                    delta(info.getBlockedCount(), previous == null ? 0 : previous.blockedCount()),
                    delta(info.getBlockedTime(), previous == null ? 0 : previous.blockedTimeMs()),
                    delta(info.getWaitedCount(), previous == null ? 0 : previous.waitedCount()),
                    delta(info.getWaitedTime(), previous == null ? 0 : previous.waitedTimeMs())
            ));
            baselines.put(info.getThreadId(), new Baseline(
                    info.getBlockedCount(), info.getBlockedTime(), info.getWaitedCount(), info.getWaitedTime()));
        }
        baselines.keySet().retainAll(liveThreadIds);

        List<ContendedThread> topContended = threads.stream()
                .sorted(Comparator.comparingLong(ThreadContentionCollector::contentionScore).reversed())
                .limit(limit)
                .toList();

        return new ContentionSnapshot(
                threadMXBean.isThreadContentionMonitoringSupported(),
                threadMXBean.isThreadContentionMonitoringSupported()
                        && threadMXBean.isThreadContentionMonitoringEnabled(),
                topContended,
                Instant.now()
        );
    }

    /**
     * Delta since the previous sample; {@code -1} values (time monitoring disabled) stay -1.
     */
    private static long delta(long current, long previous) {
        return current < 0 ? -1 : current - Math.max(previous, 0);
    }

    /**
     * Ranking score: blocked+waited time when available, blocked+waited counts otherwise.
     */
    private static long contentionScore(ContendedThread thread) {
        if (thread.blockedTimeMs() >= 0 || thread.waitedTimeMs() >= 0) {
            return Math.max(thread.blockedTimeMs(), 0) + Math.max(thread.waitedTimeMs(), 0);
        }
        return thread.blockedCount() + thread.waitedCount();
    }

    private record Baseline(long blockedCount, long blockedTimeMs, long waitedCount, long waitedTimeMs) {}
}
