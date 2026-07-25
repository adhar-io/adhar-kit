# Adhar Kit Performance Profiler

> Method-level performance profiling with hotspot detection, memory analysis, and Actuator endpoint.

## Features

- **ProfilerFacade** - unified access via `adhar.getProfiler()`
- **@Profiled Annotation** - AOP-based automatic method timing with slow execution detection
- **Profiling Registry** - thread-safe aggregation of method stats (avg/min/max/p95/p99), backed
  by a bounded [HdrHistogram](https://github.com/HdrHistogram/HdrHistogram) per method so memory
  stays flat no matter how many calls are recorded
- **Rolling Time Windows** - stats roll over on a configurable interval into a small, bounded
  history of past windows, so long-running services don't retain per-call data forever
- **Sampling & Overhead Guard** - `sampleRate` and `maxTrackedMethods` bound the instrumentation
  cost and cardinality of the registry on hot paths
- **Slow-Call Alerting** - `SlowCallEvent` (per call) and `SlowCallThresholdBreachedEvent`
  (aggregate p99) are published as Spring `ApplicationEvent`s
- **Memory Profiler** - heap/non-heap usage, GC stats, thread counts via ManagementFactory
- **Actuator Endpoint** - `/actuator/profiling` for reports, hotspots, memory, percentiles, and windows
- **Manual Profiling** - `adhar.profiled("name", () -> work())` for ad-hoc timing
- **Micrometer Integration** - all profiling data recorded as Micrometer Timer metrics

## Installation

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-perf-profiler</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
@Service
public class DataProcessor {
    private final AdharFacade adhar;

    public DataProcessor(AdharFacade adhar) { this.adhar = adhar; }

    // Automatic profiling via annotation
    @Profiled(slowThresholdMs = 200, histogram = true)
    public Result processData(Request request) {
        return doWork(request);
    }

    // Manual profiling via facade shortcut
    public Report generateReport() {
        return adhar.profiled("report-generation", () -> buildReport());
    }

    // Check hotspots
    public void diagnostics() {
        var hotspots = adhar.getProfiler().getHotspots(5);
        hotspots.forEach(m -> log.info("{}: avg={}ms, calls={}", m.name(), m.averageTimeMs(), m.callCount()));

        var mem = adhar.getProfiler().getMemorySnapshot();
        log.info("Heap: {}MB, Threads: {}", adhar.getProfiler().getHeapUsageMb(), mem.threadCount());
    }
}
```

## Configuration

```yaml
adhar:
  profiler:
    enabled: true
    default-slow-threshold-ms: 500
    log-slow-by-default: true
    # Rolling time-window aggregation
    window-duration: 5m          # window length before stats roll over into history
    history-windows: 5           # number of completed windows retained (bounded)
    # Sampling & overhead guard
    sample-rate: 1.0             # fraction (0.0-1.0) of calls actually timed/recorded
    max-tracked-methods: 1000    # cap on distinct method names tracked in the registry
    # Aggregate alerting
    p99-alert-threshold-ms: 0    # >0 publishes SlowCallThresholdBreachedEvent when p99 crosses it
```

## Slow-Call Events

In addition to the existing `log.warn`, `ProfilingAspect` publishes Spring `ApplicationEvent`s
that application code can listen for:

- `SlowCallEvent` - published every time a single call exceeds its `@Profiled(slowThresholdMs=...)` threshold.
- `SlowCallThresholdBreachedEvent` - published (debounced - once per breach, re-armed once it clears)
  when a method's aggregate p99 latency crosses `adhar.profiler.p99-alert-threshold-ms`.

```java
@Component
class SlowCallAlerter {
    @EventListener
    void onSlowCall(SlowCallEvent event) {
        log.warn("slow: {} took {}ms", event.getMethodKey(), event.getDurationMs());
    }
}
```

## Actuator Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/actuator/profiling` | GET | Full profiling report |
| `/actuator/profiling/hotspots?top=10` | GET | Top N slowest methods |
| `/actuator/profiling/memory` | GET | Memory and GC snapshot |
| `/actuator/profiling/percentiles` | GET | Per-method avg/min/max/p95/p99 for the current window |
| `/actuator/profiling/windows` | GET | Current window plus bounded rolling-window history |
| `/actuator/profiling` | DELETE | Reset profiling data |

## API Reference

| Method | Description |
|--------|-------------|
| `profile(name, supplier)` | Manually profile a code block |
| `getReport()` | Get aggregated profiling report |
| `getHotspots(topN)` | Get N slowest methods |
| `getMemorySnapshot()` | Get memory/GC/thread snapshot |
| `getHeapUsageMb()` | Current heap usage in MB |
| `getThreadCount()` | Active thread count |
| `reset()` | Clear all profiling data |
