# Adhar Kit Performance Profiler

> Method-level performance profiling with hotspot detection, memory analysis, and Actuator endpoint.

## Features

- **ProfilerFacade** - unified access via `adhar.getProfiler()`
- **@Profiled Annotation** - AOP-based automatic method timing with slow execution detection
- **Profiling Registry** - thread-safe aggregation of method stats (avg/min/max/p95/p99)
- **Memory Profiler** - heap/non-heap usage, GC stats, thread counts via ManagementFactory
- **Actuator Endpoint** - `/actuator/profiling` for reports, hotspots, and memory snapshots
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
```

## Actuator Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/actuator/profiling` | GET | Full profiling report |
| `/actuator/profiling/hotspots?top=10` | GET | Top N slowest methods |
| `/actuator/profiling/memory` | GET | Memory and GC snapshot |
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
