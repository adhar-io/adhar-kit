# Adhar Kit Batch

> Enterprise batch processing with Spring Batch 6 - job scheduling, readers/writers, metrics, and partitioning.

## Features

- **BatchFacade** - unified access via `adhar.getBatch()`
- **Job Scheduling** - cron-based job scheduling with cancel/list support
- **Batch Metrics** - job execution stats via Micrometer (duration, success/failure counts)
- **CSV/JPA Readers** - pre-configured builders for common data sources
- **CSV Writers** - delimited file output with header support
- **Range Partitioner** - parallel batch processing with automatic range splitting
- **Retryable Steps** - fluent builder for retry/skip policies
- **Job Execution Listener** - structured logging of job lifecycle events

## Installation

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-batch</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
@Service
public class ReportService {
    private final AdharFacade adhar;

    public ReportService(AdharFacade adhar) { this.adhar = adhar; }

    public void scheduleNightlyReport() {
        adhar.getBatch().scheduleJob("nightly-report", "0 0 2 * * ?");
    }

    public BatchJobStats getStats() {
        return adhar.getBatch().getJobStats("nightly-report");
    }
}
```

## Configuration

```yaml
adhar:
  batch:
    enabled: true
    table-prefix: BATCH_
    max-concurrent-jobs: 5
    retry-on-failure: true
    max-retries: 3
    default-chunk-size: 100
    default-page-size: 50
```

## API Reference

| Method | Description |
|--------|-------------|
| `scheduleJob(name, cron)` | Schedule a job with cron expression |
| `cancelJob(name)` | Cancel a scheduled job |
| `listScheduledJobs()` | List all scheduled job names |
| `getJobStats(name)` | Get execution stats for a job |
| `recordJobExecution(name, ms, ok)` | Record a job execution manually |
| `getDefaultChunkSize()` | Get configured chunk size |
