package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.repository.BatchRepositoryLayout;
import org.springframework.batch.core.job.DefaultJobKeyGenerator;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.integration.partition.StepExecutionRequest;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore;
import org.springframework.integration.jdbc.store.channel.ChannelMessageStoreQueryProvider;
import org.springframework.integration.jdbc.store.channel.H2ChannelMessageStoreQueryProvider;
import org.springframework.integration.jdbc.store.channel.PostgresChannelMessageStoreQueryProvider;
import org.springframework.integration.store.MessageGroupQueue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.util.function.SingletonSupplier;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The {@code jdbc} partition transport (design 0025 §5.10): partition requests travel through a
 * {@link QueueChannel} backed by Spring Integration's {@link JdbcChannelMessageStore}, in the
 * JobRepository's own schema ({@code <schema>.INT_CHANNEL_MESSAGE}, migration {@code V6_0_2}). No
 * broker: the manager inserts one row per partition, and a worker claims one by deleting it
 * (PostgreSQL's poll is a single {@code DELETE ... RETURNING} with {@code FOR UPDATE SKIP LOCKED}),
 * so a request reaches exactly one worker. Requests are not durable and need not be: the
 * JobRepository decides which partitions still have to run, and a manager that restarts sends them
 * again.
 *
 * <p>The channel is split into one message group per run identity, {@link #group(String, JobParameters)}
 * {@code adhar-batch:<job>:<jobKey>}, where {@code jobKey} is Spring Batch's key of the identifying
 * parameters. The manager and every worker compute it alike from the same parameters, so a worker
 * only ever sees its own run's requests, and the manager can clear what a failed attempt left
 * behind ({@link #clear(String)}).</p>
 *
 * <p>The store is built on first use, so an application that never partitions remotely never
 * touches the table. PostgreSQL is the platform's database; H2 is accepted for tests.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class JdbcPartitionTransport {

    /** Prefix of every group: the kit's requests never mix with an application's own channels. */
    public static final String GROUP_PREFIX = "adhar-batch:";

    /** The store's region, which partitions the table between users of it. */
    public static final String REGION = "adhar-batch";

    /**
     * The only classes a request may deserialize to: the message, its headers and Spring Batch
     * Integration's request. Rows are written by the application's own pods, but a table is not a
     * trust boundary worth an arbitrary deserialization.
     */
    static final String[] ALLOWED_CLASSES = {
            "org.springframework.messaging.support.*",
            "org.springframework.messaging.MessageHeaders",
            "org.springframework.integration.support.*",
            "org.springframework.integration.message.*",
            "org.springframework.batch.integration.partition.StepExecutionRequest",
            "java.util.*",
            "java.lang.*"};

    private static final DefaultJobKeyGenerator KEYS = new DefaultJobKeyGenerator();

    private final JobRepository jobRepository;
    private final Supplier<JdbcChannelMessageStore> store;
    private final Map<String, QueueChannel> channels = new ConcurrentHashMap<>();
    private final MessageChannel requests = this::sendRequest;

    /**
     * @param dataSource    the JobRepository's DataSource
     * @param layout        where the JobRepository's schema is ({@code null}: unqualified names)
     * @param jobRepository resolves the run a request belongs to
     */
    public JdbcPartitionTransport(DataSource dataSource, BatchRepositoryLayout layout, JobRepository jobRepository) {
        this.jobRepository = jobRepository;
        this.store = SingletonSupplier.of(() -> createStore(dataSource, layout));
    }

    private static JdbcChannelMessageStore createStore(DataSource dataSource, BatchRepositoryLayout layout) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // One row per poll; a larger fetch size only matters to Oracle's FIFO, and the store warns without it.
        jdbc.setFetchSize(1);
        JdbcChannelMessageStore store = new JdbcChannelMessageStore();
        store.setJdbcTemplate(jdbc);
        store.setChannelMessageStoreQueryProvider(queryProvider(layout));
        store.setTablePrefix(layout != null && layout.managed()
                ? layout.schema() + "." + BatchRepositoryLayout.PARTITION_CHANNEL_PREFIX
                : BatchRepositoryLayout.PARTITION_CHANNEL_PREFIX);
        store.setRegion(REGION);
        store.addAllowedPatterns(ALLOWED_CLASSES);
        store.afterPropertiesSet();
        return store;
    }

    private static ChannelMessageStoreQueryProvider queryProvider(BatchRepositoryLayout layout) {
        String product = layout == null ? "" : layout.databaseProduct();
        if (layout != null && layout.managed()) {
            return new PostgresChannelMessageStoreQueryProvider();
        }
        if ("H2".equalsIgnoreCase(product)) {
            return new H2ChannelMessageStoreQueryProvider();
        }
        throw new IllegalStateException("The jdbc partition transport needs the JobRepository on PostgreSQL (the "
                + "platform's database), was '" + product + "'");
    }

    /**
     * @param jobName    the job
     * @param parameters the run's parameters; only the identifying ones count
     * @return the run identity's message group, {@code adhar-batch:<job>:<jobKey>}
     */
    public static String group(String jobName, JobParameters parameters) {
        return GROUP_PREFIX + jobName + ":" + KEYS.generateKey(parameters);
    }

    /**
     * @param execution an execution of the run
     * @return its message group
     */
    public static String group(JobExecution execution) {
        return group(execution.getJobInstance().getJobName(), execution.getJobParameters());
    }

    /**
     * @param group a message group
     * @return the queue of that group, on the store
     */
    public QueueChannel channel(String group) {
        return channels.computeIfAbsent(group, name -> new QueueChannel(new MessageGroupQueue(store.get(), name)));
    }

    /**
     * @return the channel the manager step sends its requests to: each request goes to the group
     *         of the execution its partition belongs to
     */
    public MessageChannel requests() {
        return requests;
    }

    /**
     * Removes every request of a group: what a failed or stopped attempt left unclaimed.
     *
     * @param group a message group
     */
    public void clear(String group) {
        store.get().removeMessageGroup(group);
    }

    /**
     * @param group a message group
     * @return how many requests wait in it
     */
    public int pending(String group) {
        return store.get().messageGroupSize(group);
    }

    private boolean sendRequest(Message<?> message, long timeout) {
        if (!(message.getPayload() instanceof StepExecutionRequest request)) {
            throw new MessagingException(message, "The partition channel carries StepExecutionRequests only, got "
                    + message.getPayload().getClass().getName());
        }
        StepExecution partition = StaleRequestGuard.stepExecution(jobRepository, request.getStepExecutionId());
        if (partition == null) {
            throw new MessagingException(message, "No step execution " + request.getStepExecutionId()
                    + " in the JobRepository for " + request);
        }
        return channel(group(partition.getJobExecution())).send(message, timeout);
    }
}
