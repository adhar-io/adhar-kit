package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.runtime.BatchRole;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.batch.integration.partition.RemotePartitioningManagerStepBuilder;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

/**
 * Remote partitioning (design 0025 §5.10, Phase 2): a manager pod splits the input and hands one
 * request per partition to interchangeable worker pods through the {@code jdbc} partition channel,
 * a table in the application's own database. Standard Spring Batch Integration underneath: the
 * manager step is {@link RemotePartitioningManagerStepBuilder}'s in JobRepository-polling mode (no
 * reply channel, a poll interval and a timeout), so a restart re-runs only the partitions that did
 * not complete, each from its own last commit.
 *
 * <pre>{@code
 * @Bean
 * @AdharBatchJob(parameters = @JobParam(name = "businessDate", type = ParamType.DATE, source = ParamSource.SCHEDULE))
 * Job importJob(JobRepository jobRepository, RemotePartitioning partitioning, DataSource dataSource) {
 *     return new JobBuilder("importJob", jobRepository).start(partitioning.manager("importManager", "importStep",
 *             SqlKeyRangePartitioner.of(dataSource, "public.inbound_records", "id").build())).build();
 * }
 *
 * @Bean
 * @ConditionalOnBatchRole(BatchRole.WORKER)
 * Step importStep(AdharSteps steps, JdbcPagingItemReader<Record> rangeReader, ItemWriter<Record> writer) {
 *     return steps.<Record, Record>chunk("importStep").reader(rangeReader).writer(writer).build();
 * }
 * }</pre>
 *
 * <p>The job is defined in every role, because a worker computes the run's identity from the job's
 * declared parameters exactly as the manager does. What {@link #manager} builds depends on the
 * pod's role ({@code ADHAR_BATCH_ROLE}, {@code indexed} resolved by {@code JOB_COMPLETION_INDEX}):</p>
 *
 * <ul>
 *   <li>{@code manager}: the remote manager step. Partitions {@code adhar.batch.partition.grid-size},
 *       polls the repository every {@code poll-interval}, gives up after {@code timeout}; the stale-request
 *       guard and the fence ({@link ManagerPartitionGuard}) and the outcome rules of
 *       {@link PartitionOutcomeAggregator} are attached.</li>
 *   <li>{@code worker}: a placeholder that refuses to execute. A worker never starts the job; it serves
 *       partitions ({@link PartitionWorker}) of the worker step, which {@link ConditionalOnBatchRole}
 *       keeps out of the manager.</li>
 *   <li>{@code single} (outside the platform: a test, a local run, a service): the same partitions run in
 *       this process ({@link LocalPartitioning}), on as many threads as there are processors, up to the
 *       grid size. The worker step must exist, which {@code @ConditionalOnBatchRole(WORKER)} allows.</li>
 * </ul>
 *
 * <p>The worker step must hold no state across partitions: its reader and writer are {@code @StepScope}
 * beans reading their range from {@code #{stepExecutionContext[...]}}, and its writes share the chunk's
 * transaction with the JobRepository (INV-4), which is what fences a zombie worker.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class RemotePartitioning {

    private final JobRepository jobRepository;
    private final ObjectProvider<JdbcPartitionTransport> transport;
    private final BatchProperties.Partition settings;
    private final BatchRole role;
    private final ListableBeanFactory beanFactory;

    /**
     * @param jobRepository the shared JobRepository
     * @param transport     the partition channel (needed by the manager role only)
     * @param settings      {@code adhar.batch.partition.*}
     * @param role          the pod's effective role ({@link OnBatchRoleCondition#effectiveRole})
     * @param beanFactory   finds the worker step in the single role
     */
    public RemotePartitioning(JobRepository jobRepository, ObjectProvider<JdbcPartitionTransport> transport,
                              BatchProperties.Partition settings, BatchRole role, ListableBeanFactory beanFactory) {
        this.jobRepository = jobRepository;
        this.transport = transport;
        this.settings = settings;
        this.role = role;
        this.beanFactory = beanFactory;
    }

    /**
     * @return the role this pod plays
     */
    public BatchRole role() {
        return role;
    }

    /**
     * @param name           the manager step's name
     * @param workerStepName the worker step's name (and bean name): partitions are named
     *                       {@code <workerStepName>:partition<n>}
     * @param partitioner    splits the input; called by the manager only, once per execution that is not a restart
     * @return the manager step for this pod's role
     */
    public Step manager(String name, String workerStepName, Partitioner partitioner) {
        int gridSize = settings.effectiveGridSize();
        return switch (role) {
            case MANAGER -> remoteManager(name, workerStepName, partitioner, gridSize);
            case WORKER -> workerPlaceholder(name);
            default -> LocalPartitioning.of(gridSize, Math.max(1, Math.min(gridSize,
                    Runtime.getRuntime().availableProcessors()))).step(name, jobRepository, partitioner,
                    PartitionWorker.findStep(beanFactory, workerStepName).orElseThrow(() -> new IllegalStateException(
                            "Role single runs the partitions of step [" + name + "] in this process, but there is "
                                    + "no step named [" + workerStepName + "]: keep the worker step for role single "
                                    + "(@ConditionalOnBatchRole(WORKER) includes it)")));
        };
    }

    private Step remoteManager(String name, String workerStepName, Partitioner partitioner, int gridSize) {
        JdbcPartitionTransport channel = transport.getIfAvailable();
        if (channel == null) {
            throw new IllegalStateException("Role manager needs the jdbc partition transport, which needs the "
                    + "JobRepository's DataSource: configure spring.datasource.*");
        }
        Duration timeout = settings.getTimeout();
        return new RemotePartitioningManagerStepBuilder(name, jobRepository)
                .partitioner(workerStepName, partitioner)
                .gridSize(gridSize)
                .outputChannel(channel.requests())
                .pollInterval(settings.getPollInterval().toMillis())
                .timeout(timeout == null ? -1 : timeout.toMillis())
                .aggregator(new PartitionOutcomeAggregator(jobRepository))
                .listener(new ManagerPartitionGuard(channel, jobRepository, workerStepName))
                .build();
    }

    private Step workerPlaceholder(String name) {
        return new StepBuilder(name, jobRepository).tasklet((contribution, context) -> {
            throw new IllegalStateException("Step [" + name + "] is the manager of a remote-partitioned job and this "
                    + "pod is a worker (ADHAR_BATCH_ROLE): a worker serves partitions and never runs the manager");
        }, new ResourcelessTransactionManager()).build();
    }
}
