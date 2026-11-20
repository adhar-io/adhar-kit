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
| Remote partitioning (a manager and workers) | `RemotePartitioning`, `@ConditionalOnBatchRole`, `PartitionWorker`, `JdbcPartitionTransport`, `StaleRequestGuard`, `WorkerStopHandler`, `PartitionPlanSplitter`, `WorkerLivenessWatch` |
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
| 2 | Invalid request: parameters, unknown job, incrementer, in-memory repository, migration, business date, unsupported, a role, distribution or partition setting that does not bind (not retried) |
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
commit, over the ranges recorded by the first attempt. The partitioner runs once per identity: its
output is the **plan** (`adhar.partition.plan` in the manager step's context, saved before any
partition is), and every restart is split from it (`PartitionPlanSplitter`). A manager killed while
it recorded the partitions (Spring Batch saves each one empty first, then its range) therefore
cannot leave a partition that restarts with no range and reads nothing: a partition the plan names
but no attempt recorded, or whose last record is empty, starts from its last written record or
its planned range. `ObjectPrefixPartitioner` makes one
partition per S3 prefix (`#{stepExecutionContext['prefix']}`).

## Remote partitioning (a manager and workers)

A `BatchJob` with `mode: partitioned` runs one manager pod and `workers` worker pods: a Volcano
gang (`ADHAR_BATCH_ROLE=manager|worker`) or, without Volcano, one Indexed Job whose index 0 is the
manager (`ADHAR_BATCH_ROLE=indexed`, `JOB_COMPLETION_INDEX`). One image, one configuration:

```java
@Bean
@AdharBatchJob(parameters = @JobParam(name = "businessDate", type = ParamType.DATE, source = ParamSource.SCHEDULE))
Job importJob(JobRepository jobRepository, RemotePartitioning partitioning, DataSource dataSource) {
    return new JobBuilder("importJob", jobRepository).start(partitioning.manager("importManager", "importStep",
        SqlKeyRangePartitioner.of(dataSource, "public.inbound_records", "id").build())).build();
}

@Bean
@ConditionalOnBatchRole(BatchRole.WORKER)          // never built in the manager
Step importStep(AdharSteps steps, JdbcPagingItemReader<Record> rangeReader, ItemWriter<Record> writer) {
    return steps.<Record, Record>chunk("importStep").reader(rangeReader).writer(writer).build();
}
```

It needs `org.springframework.batch:spring-batch-integration` and
`org.springframework.integration:spring-integration-jdbc` (optional dependencies of the kit; a
worker without them exits 2, `unsupported`). The job exists in every role: a worker computes the
run's identity from its declared parameters exactly as the manager does.

- **The manager** (Spring Batch Integration's `RemotePartitioningManagerStepBuilder`, polling the
  JobRepository) splits the input into `adhar.batch.partition.grid-size` partitions and writes one
  request per partition to the **partition channel**: `batch.INT_CHANNEL_MESSAGE` (migration
  `V6_0_2`), a queue in the application's own database, one message group per run identity
  (`adhar-batch:<job>:<jobKey>`). It gives up after `adhar.batch.partition.timeout`.
- **Workers** are interchangeable: each claims a request (a single `DELETE … RETURNING`, so one
  worker per request), runs the partition and takes the next, until the identity's execution
  COMPLETED (exit 0). A FAILED or STOPPED execution does not end a worker: a restart is coming.
- **Restarts re-run only unfinished partitions**, each from its last commit, over the plan the
  first attempt recorded (see local partitioning above).
- **A worker killed mid-partition** (SIGKILL, OOM) does not hold the run until the timeout.
  Workers hold no lease; each records itself as its partition's owner (`PartitionOwner`: pod
  name, uid, step execution) before running it, and in a cluster the manager's
  `WorkerLivenessWatch` reads those pods every three poll intervals. A running partition whose
  owner pod is gone (404), replaced (another uid under its name) or ended is marked FAILED: the
  other partitions finish, the manager step fails retryable (exit 5) and the Job's retry runs
  that partition from its last commit. A terminating pod is left to stop its own partition. On
  Volcano the gang's `PodFailed → RestartJob` usually comes first.
- **Stale requests** never run: the manager clears the run's group before it sends, and a worker
  discards a request whose execution is not STARTED or not the latest, or whose partition already
  started.
- **Fencing (INV-4).** A worker that is slow rather than dead cannot write into a partition that is
  running again: `recover`, and a manager that stops waiting, mark the old partition FAILED, so the
  slow worker's next chunk commit fails on its version and its writes roll back with it.
- **SIGTERM on a worker** stops only its own partition at the next chunk boundary. The manager
  fails the run as retryable (exit 5) rather than STOPPED (exit 4, an operator's stop), so the Job
  retries it and the restart re-runs that partition. A partition that failed with a
  `NonRetryableBatchException` or a skip limit fails the run with exit 3.
- **Scheduled gangs.** On Volcano, `ADHAR_BATCH_K8S_JOB` is the pod's `volcano.sh/job-name`. A
  name `batch/v1` does not know is read as a Volcano Job (`batch.volcano.sh/v1alpha1`, the composed
  Role grants `get` on it): the business date comes from the Volcano CronJob's
  `volcano.sh/cronjob-scheduled-timestamp`, the attempt from the gang's `status.retryCount`.
- **Role `single`** (a local run, a test, a service) plays every part: `manager(…)` runs the same
  partitions in the one process.

`adhar.batch.distribution=chunked` (remote chunking) and the transports `kafka` and `amqp` are
reserved names: a run that names one exits 2 (`unsupported`).

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
  `batch.partition`, `batch.businessDate` and `k8s.job` in the MDC; an in-service run on Quartz
  also `batch.run` (its name, naming v1) and `batch.trigger` (the trigger's key).
- In-service schedules add `adhar.batch.scheduler.*` meters on both engines (see
  [In-service scheduling on Quartz](#in-service-scheduling-on-quartz)).

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
| `adhar.batch.partition.transport` | env `ADHAR_BATCH_PARTITION_TRANSPORT` (`jdbc`) | `jdbc`; `kafka` and `amqp` are reserved |
| `adhar.batch.partition.grid-size` | env `ADHAR_BATCH_PARTITION_GRIDSIZE` (4) | Partitions per run; the platform passes 4 × workers |
| `adhar.batch.partition.poll-interval` | `5s` | How often the manager and idle workers read the repository |
| `adhar.batch.partition.timeout` | env `ADHAR_BATCH_PARTITION_TIMEOUT` (none) | How long the manager waits for every partition; the platform passes the deadline less 5 minutes |
| `adhar.batch.distribution` | env `ADHAR_BATCH_DISTRIBUTION` (`partitioned`) | `partitioned` \| `chunked` (Phase 4, refused until then) |
| `adhar.batch.scheduler.enabled` / `.time-zone` / `.misfire-threshold` / `.lock` | `false` / `UTC` / `10m` / `jdbc` | Embedded mode |
| `adhar.batch.scheduler.lock-lease` / `.lock-renew-interval` / `.pool-size` / `.jobs.<job>` | `2m` / `30s` / `2` / — | Embedded mode |
| `adhar.batch.scheduler.engine` | `simple` | `simple` \| `quartz` (needs `spring-boot-starter-quartz`) |
| `adhar.batch.quartz.scheduler-name` | `${spring.application.name}` | The Quartz cluster's identity: one per service |
| `adhar.batch.quartz.cluster-checkin-interval` / `.misfire` / `.max-catch-up` | `15s` / `latest` / `24` | Failover speed; default misfire policy (`skip` \| `latest` \| `all`); most slots `all` runs |
| `adhar.batch.quartz.jobs.<job>.name` | the bean name as a DNS label | The schedule name, naming v1's `<batchjob>` |
| `adhar.batch.quartz.triggers.<job>[n].*` | — | `name`, `kind` (`cron` \| `interval` \| `daily`), `expression` \| `every`/`start` \| `from`/`to`/`every`/`days`, `time-zone`, `calendar`, `misfire`, `priority`, `parameters` |
| `adhar.batch.quartz.calendars.<name>.*` | — | `file` (a D5 `calendar.yaml`), or `time-zone`, `weekend-days`, `holidays`, `business-days` |
| `adhar.batch.quartz.calendars.refresh-interval` | `5m` | Re-read of calendar files |
| `adhar.batch.quartz.report.enabled` / `.name` / `.min-interval` | in Kubernetes / `<scheduler-name>-batch-schedules` / `10s` | The schedule report |

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

## In-service scheduling on Quartz

`adhar.batch.scheduler.engine=quartz` (and `spring-boot-starter-quartz` on the classpath) puts the
same `BatchScheduler` on a clustered Quartz JDBC store in the batch schema (`batch.QRTZ_*`, the
kit's second Flyway stream). Every replica of the service shares it: a slot runs once across them,
a replica that dies mid-run is re-fired on another and the run resumes from its last commit, and
each trigger has a misfire policy. Both engines name a slot's run alike (`SlotIdentity`, naming v1),
so a service switches engine without losing a JobInstance.

```yaml
adhar.batch:
  scheduler: { enabled: true, engine: quartz, time-zone: Asia/Singapore }
  quartz:
    calendars:
      sg-business: { file: /etc/adhar/batch/calendar/calendar.yaml }   # D5's calendar.yaml
    triggers:
      importJob:
        - { name: nightly, kind: cron, expression: "0 30 1 ? * *", calendar: sg-business }
        - { name: office, kind: daily, from: "09:00", to: "17:00", every: 5m, days: [MONDAY, FRIDAY] }
        - { name: poll, kind: interval, every: 5m, misfire: skip }
```

- **Triggers.** `cron` (Quartz syntax), `interval` (on the grid `start + n × every`) and `daily`
  (a window on days of the week; it runs in the JVM's zone). The `adhar.batch.scheduler.jobs`
  Spring cron schedules still work: each is converted, and the two parsers' next 400 fire times,
  in UTC and three daylight-saving zones, must agree or the start fails naming the slot. A time
  in the hour a clock change repeats (01:30 in New York or London) is such a disagreement: write
  it as a Quartz `cron` trigger.
- **Calendars.** D5's rule: a date is a business day when it is in `businessDays`, or is neither a
  `weekendDays` day nor in `holidays`. An excluded day never fires. A trigger and its calendar
  share one zone or the start fails; a calendar file is re-read every `refresh-interval`, and an
  invalid one, or one whose zone changed, keeps the previous version (counted) — at start it fails
  the start.
- **Runtime triggers.** `BatchTriggers` (a bean only on `quartz`) stores durable one-shot triggers
  keyed by the run's name, so the same identity twice is one trigger and one run:

  ```java
  Result result = triggers.fireNow("importJob", Map.of("file", "orders-0412.csv"));
  // result.status(): SCHEDULED (stored), PENDING (stored already, or running), COMPLETED (nothing stored)
  triggers.fireAt("reportJob", Instant.parse("2026-11-20T09:00:00Z"), Map.of());
  triggers.list();   triggers.cancel(result.runName());
  ```

  A `fireNow` identity is the caller's parameters only: a `@JobParam(source = SCHEDULE)` is passed
  explicitly, and a job whose identity would be empty is refused (use `fireAt`, keyed on the
  instant). Invalid parameters throw `ParameterValidationException` before anything is stored.
- **One service per scheduler name.** Each JobDetail records its application; a start that finds
  another application's jobs under its `scheduler-name` fails. A job the application stopped
  defining is removed with its triggers, so a rolling update's newest start wins.
- **Meters** (both engines): `adhar.batch.scheduler.next.fire` (gauge, epoch seconds; job,
  trigger), `adhar.batch.scheduler.fires` (job, trigger, outcome: `ran`, `skipped-complete`,
  `skipped-duplicate`, `misfire-skipped`, `recovered`, `failed-to-start`),
  `adhar.batch.scheduler.instances`, `adhar.batch.scheduler.report.failures` and
  `adhar.batch.scheduler.calendar.failures`. One INFO line per fire names the trigger, the slot,
  the run and the outcome; `BatchRunEvent.Skipped` (`already-complete`, `duplicate`, `misfire`) and
  `Recovered` carry the run's name (`getRunName()`).
- **The schedule report.** In Kubernetes the service writes `ConfigMap <scheduler-name>-batch-schedules`
  (the scheduler name lower-cased into a valid ConfigMap name)
  (key `schedules.json`, version 1, ≤ 256 KiB, `truncated` when cut), which the Console and
  `adhar batch schedules` read. Its ServiceAccount needs `create` on `configmaps` and
  `get`/`update`/`patch` on that name; without them every write is counted, never thrown.
