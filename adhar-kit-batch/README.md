# Adhar Kit Batch

> Spring Batch 6 on the Adhar platform: one pod per run, a JDBC JobRepository with a managed
> schema, restarts that never duplicate, and exit codes the platform's Job policy understands.

Design: `adhar/docs/design/0025-batch-platform.md` (§5). The platform runs a batch job as a
`BatchJob` (a CronJob or a manual run, each one pod); this module is the code inside that pod.

## What it provides

| Area | Classes |
|---|---|
| Declaring a job | `@AdharBatchJob`, `@JobParam`, `ParamType`, `ParamSource`; `JobParameterSpec`(`Registry`) |
| The Kubernetes runner | `AdharBatchApplication`, `AdharBatchRunner`, `RunContext`, exit codes, termination summary |
| Restart safety | JDBC JobRepository in schema `batch` (Flyway), `ExecutionLease`, `RecoveryPolicy`, `GracefulStopLifecycle` |
| Steps | `AdharSteps` → `AdharChunkStepBuilder`; `FaultTolerance` (retry and skip presets, `deadLetter()`) |
| I/O | `KeysetPagingItemReaderBuilder`, `PostgresUpsertItemWriterBuilder`, `JsonLines{Reader,Writer}Builder`, `S3ItemReaderBuilder`, `S3ItemWriterBuilder`, plus the CSV/JSON/JDBC/JPA builders |
| Partitioning (one pod) | `LocalPartitioning`, `SqlKeyRangePartitioner`, `ObjectPrefixPartitioner` |
| Telemetry | `BatchProgressMetrics`, `BatchMdc`, `ItemObservationTracingFilter` |
| Events | `BatchRunEvent` (`Started`, `Completed`, `Failed`, `Stopped`, `Skipped`, `Recovered`) |
| Operations | `BatchOperator` (`start`, `restart`, `stop`, `abandon`, `recover`, `findExecutions`) |
| Embedded mode | `BatchScheduler` + `SchedulerLock` (a service that also runs a small job; not the platform path) |

Everything is active only when the application defines at least one `Job` bean: a web service
on `adhar-kit-starter` gets none of it (the starter carries this module as optional).

## Installation

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-batch</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

Optional, when used: `io.awspring.cloud:spring-cloud-aws-starter-s3` (S3/RustFS I/O),
`io.micrometer:micrometer-tracing` (the trace filter), `io.micrometer:micrometer-registry-otlp` and
`org.springframework.boot:spring-boot-opentelemetry` (OTLP metrics push, see adhar-kit-metrics).
Tests: `com.adhar.kit:adhar-kit-test-commons`, `org.springframework.batch:spring-batch-test`,
`org.testcontainers:testcontainers-postgresql`.

## A job

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        AdharBatchApplication.run(Application.class, args);   // exits with the run's code
    }
}

@Configuration
class ImportJobConfiguration {

    @Bean
    @AdharBatchJob(description = "Imports inbound records", parameters = {
        @JobParam(name = "businessDate", type = ParamType.DATE, source = ParamSource.SCHEDULE),
        @JobParam(name = "source", defaultValue = "inbound/records.csv", pattern = "^[a-zA-Z0-9/._-]+$"),
        @JobParam(name = "dryRun", type = ParamType.BOOLEAN, identifying = false, defaultValue = "false")})
    Job importJob(JobRepository jobRepository, Step importStep) {
        return new JobBuilder("importJob", jobRepository).start(importStep).build();   // no incrementer
    }

    @Bean
    Step importStep(AdharSteps steps, ItemReader<InputRecord> reader, RecordProcessor processor,
                    ItemWriter<Record> writer) {
        return steps.<InputRecord, Record>chunk("importStep")       // size: adhar.batch.chunk.size
            .reader(reader).processor(processor).writer(writer)
            .faultTolerance(FaultTolerance.transientInfrastructure()
                .skip(ValidationException.class, 100)
                .deadLetter())                                       // batch.ADHAR_BATCH_DEAD_LETTER
            .build();
    }

    @Bean
    @StepScope
    JdbcPagingItemReader<InputRecord> reader(DataSource dataSource,
                                             @Value("#{jobParameters['businessDate']}") LocalDate day) {
        return KeysetPagingItemReaderBuilder.of(dataSource, InputRecord.class)
            .from("public.inbound_records").where("business_date = :day").parameter("day", day)
            .key("record_key").build();          // the key must be unique and NOT NULL: it is checked
    }

    @Bean
    JdbcBatchItemWriter<Record> writer(DataSource dataSource) {
        return PostgresUpsertItemWriterBuilder.<Record>into(dataSource, "public.records")
            .columns("record_key", "amount", "business_date").conflictOn("record_key").build();
    }
}
```

- **Identity.** The identifying parameters are the declared ones with `identifying = true`. A
  scheduled run's `businessDate` is the CronJob's scheduled time in the job's time zone, never
  `now()`. Business jobs have no incrementer (`allowIncrementer = true` is for housekeeping only).
- **No secrets in parameters.** They are stored in clear text; names such as `password`,
  `token` or `apiKey` are refused, and `sensitive = true` values are masked everywhere.
- **Exactly once.** Business writes and the JobRepository's progress share one transaction, so a
  restart resumes after the last commit. Keyset readers and upsert writers keep the boundary
  stable; the S3 writer writes one object per committed chunk with reusable keys.

## The Kubernetes runner

`adhar.batch.mode=auto` becomes `kubernetes` when `ADHAR_BATCH_JOB` is set, which the platform
always does. The runner then forces `spring.batch.job.enabled=false`,
`spring.main.web-application-type=none`, `spring.main.cloud-platform=none` and the shutdown phase
timeout, starts the named job once and exits.

| Input | From |
|---|---|
| `ADHAR_BATCH_JOB`, `_NAME`, `_ROLE`, `_TRIGGER`, `_PARAMS` (a JSON object), `_TIME_ZONE` | the composition or the client |
| `ADHAR_BATCH_K8S_JOB`, `ADHAR_BATCH_ACTOR`, `POD_NAME`, `POD_UID`, `POD_NAMESPACE`, `NODE_NAME` | the downward API |
| Scheduled time, attempt, image digest | one `GET` of the owner Job and of the pod (ServiceAccount token) |

| Exit | Meaning |
|---|---|
| 0 | Completed, or skipped: already complete, a duplicate run still alive |
| 1 | The JVM or context failed before the run (retried) |
| 2 | Invalid request: parameters, unknown job, incrementer, in-memory repository, migration, business date (not retried) |
| 3 | Failed, not retryable: `NonRetryableBatchException`, skip limit exceeded (not retried) |
| 4 | Stopped on request |
| 5 | Failed, retryable |
| 6 | Abandoned: a new identity is needed |
| 7 | Repository state unknown |
| 143 / 137 | SIGTERM (STOPPED is recorded first) / OOM |

On exit the runner writes a summary (`{"v":1,"job":…,"status":…,"exitCode":…,"outcome":…,
"read":…,"written":…,"skipped":…,"deadLettered":…,"steps":[…]}`, at most 3,584 bytes) to
`/dev/termination-log` and stdout, which the Console and the CLI show. A run that finds its
instance still STARTED checks the owner pod and the execution lease: it recovers a dead owner's
execution, waits for a terminating one, and skips (exit 0) while the owner is alive.

## Partitioning inside one pod

```java
@Bean
Step importManager(JobRepository jobRepository, DataSource dataSource, Step importStep) {
    return LocalPartitioning.of(8, 4).step("importManager", jobRepository,
        SqlKeyRangePartitioner.of(dataSource, "public.inbound_records", "id").build(), importStep);
}
```

The worker's reader is `@StepScope` and reads `#{stepExecutionContext['minKey']}` /
`['maxKey']`. A restart re-runs only the partitions that did not complete, each from its last
commit, over the ranges recorded by the first attempt. `ObjectPrefixPartitioner` makes one
partition per S3 prefix (`#{stepExecutionContext['prefix']}`).

## S3 and RustFS

Configure Spring Cloud AWS path-style (`spring.cloud.aws.s3.endpoint`,
`spring.cloud.aws.s3.path-style-access-enabled=true`) and pass its `S3Client`:

```java
S3ItemReaderBuilder.<InputRecord>of(s3Client, "inbound").prefix("day=" + day + "/").suffix(".jsonl")
    .jsonLines(InputRecord.class).build();
S3ItemWriterBuilder.<Record>of(s3Client, "outbound").prefix("records/day=" + day + "/").build();
```

A prefix is listed completely (the SDK paginator; `S3Template.listObjects` stops at 1,000 keys),
and a restart over input that changed since the failed attempt is refused.

## Telemetry

- Spring Batch's own meters (`spring.batch.*`), plus `adhar.batch.run`, `adhar.batch.last.commit`
  and `adhar.batch.items.dead.lettered`. A run pod pushes them over OTLP when the
  `OTEL_EXPORTER_OTLP_*` variables are set (adhar-kit-metrics), and once more on exit.
- Traces have job, step and chunk spans only; per-item observations stay metrics
  (`adhar.batch.telemetry.trace-items=true` traces them).
- Every log line of a run carries `batch.job`, `batch.instance`, `batch.execution`, `batch.step`,
  `batch.partition`, `batch.businessDate` and `k8s.job` in the MDC.

## Testing

```java
@AdharBatchTest                                  // real auto-configuration on PostgreSQL 18.4
class RestartIT {
    @Autowired JobOperatorTestUtils jobs;
    @Autowired FailureInjector failures;         // a bean of the test configuration
    @Autowired JdbcTemplate jdbc;

    @Test
    void aRestartWritesEachRecordOnce() throws Exception {
        RestartScenario.of(jobs).withParameters(params).failingAt(failures, 150L).run()
            .assertStepHistory("importStep", BatchStatus.FAILED, BatchStatus.COMPLETED)
            .assertResumedFromLastCommit("importStep", 1_000)
            .assertExactlyOnce(jdbc, "records", "record_key", 1_000);
    }
}
```

`BatchContractAssertions.assertContract(context, Path.of("deploy/base/batchjob.yaml"))` fails the
build when the `BatchJob` manifest's parameters differ from `@AdharBatchJob`, its memory is below
1Gi, or its grace period does not exceed `adhar.batch.shutdown.timeout`.

## Properties

| Property | Default | Meaning |
|---|---|---|
| `adhar.batch.enabled` | `true` | Module switch; at least one `Job` bean is also required |
| `adhar.batch.mode` | `auto` | `auto` \| `kubernetes` \| `embedded` |
| `adhar.batch.job` | env `ADHAR_BATCH_JOB` | The job a Kubernetes run executes |
| `adhar.batch.role` | env `ADHAR_BATCH_ROLE` (`single`) | `single` \| `manager` \| `worker` \| `indexed` |
| `adhar.batch.repository.schema` / `.table-prefix` | `batch` / `BATCH_` | The JobRepository's schema-qualified prefix |
| `adhar.batch.repository.migrate` | `true` | Run the kit's Flyway migrations of that schema |
| `adhar.batch.repository.allow-in-memory` | `false` | Tests only; a Kubernetes run refuses an in-memory repository |
| `adhar.batch.recovery.heartbeat-interval` / `.stale-after` / `.max-wait` | `30s` / `2m` / `10m` | Execution lease |
| `adhar.batch.shutdown.timeout` | `90s` | Graceful stop; must be below the pod's grace period |
| `adhar.batch.exit.already-complete` | `success` | `success` (exit 0) \| `fail` (exit 3) |
| `adhar.batch.chunk.size` / `adhar.batch.page.size` | `100` / `500` | Builder defaults |
| `adhar.batch.retry.max-attempts` / `.initial-backoff` / `.max-backoff` | `3` / `1s` / `30s` | Retry presets |
| `adhar.batch.dead-letter.enabled` | `true` | JDBC dead-letter table |
| `adhar.batch.telemetry.trace-items` | `false` | Per-item spans |
| `adhar.batch.scheduler.enabled` / `.time-zone` / `.misfire-threshold` / `.lock` | `false` / `UTC` / `10m` / `jdbc` | Embedded mode |
| `adhar.batch.scheduler.lock-lease` / `.lock-renew-interval` / `.pool-size` / `.jobs.<job>` | `2m` / `30s` / `2` / — | Embedded mode |

Renamed directly, with no aliases: `table-prefix` → `repository.table-prefix`,
`default-chunk-size` → `chunk.size`, `default-page-size` → `page.size`, `max-retries` →
`retry.max-attempts`. Removed with the asynchronous launcher: its concurrency limit, and
`retry-on-failure`. `RetryableStepBuilder` was replaced by `AdharSteps` and `FaultTolerance`, and
`metrics.BatchMetrics` is `telemetry.BatchProgressMetrics`.

## Embedded mode

A service that also runs a small job can schedule it in-process:
`adhar.batch.scheduler.enabled=true` and `adhar.batch.scheduler.jobs.nightlyReport: "0 0 2 * * *"`.
The launch is synchronous, a JDBC lock (renewed while the run lasts) keeps it to one replica, and
the nominal fire time is the run's identity, so a double fire is the same JobInstance.
`BatchFacade.getInstance()` (or `adhar.getBatch()`) exposes `scheduleJob`, `cancelJob`,
`listScheduledJobs`, `getJobStats` and the `BatchOperator`. It is not the platform path: a long or
heavy job belongs in a `BatchJob`.
