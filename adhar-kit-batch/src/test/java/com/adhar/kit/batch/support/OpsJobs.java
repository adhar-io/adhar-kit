package com.adhar.kit.batch.support;

import com.adhar.kit.batch.annotation.AdharBatchJob;
import com.adhar.kit.batch.annotation.JobParam;
import com.adhar.kit.batch.fault.FaultTolerance;
import com.adhar.kit.batch.step.AdharSteps;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.LongStream;

/**
 * The ops commands' test job, {@value #LEDGER_JOB}: entries 1..{@link #ITEMS} in chunks of 3, a
 * processor that rejects the ids in {@link #REJECT_PROCESS} and a STEP-SCOPED writer that rejects
 * the ids in {@link #REJECT_WRITE} and records the rest with the run's {@code run} parameter in
 * {@code OPS_LEDGER}. Rejected entries are skipped and dead-lettered, so a dlq-replay has
 * something to send through the same processor and writer, under the original step scope.
 */
@Configuration(proxyBeanMethods = false)
public class OpsJobs {

    public static final String LEDGER_JOB = "ledgerJob";
    public static final String LEDGER_STEP = "ledgerStep";

    /** How many entries a run reads. */
    public static final int ITEMS = 9;

    /** Ids the processor rejects (skipped in process). */
    public static final Set<Long> REJECT_PROCESS = ConcurrentHashMap.newKeySet();

    /** Ids the writer rejects (skipped in write). */
    public static final Set<Long> REJECT_WRITE = ConcurrentHashMap.newKeySet();

    /** Ids the processor was called with, in order. */
    public static final List<Long> PROCESSED = new CopyOnWriteArrayList<>();

    /** One entry; a record, so Jackson 3 writes and reads it as the dead letter keeps it. */
    public record Entry(long id, String note) {
    }

    /** Forgets what an earlier test rejected and processed. */
    public static void reset() {
        REJECT_PROCESS.clear();
        REJECT_WRITE.clear();
        PROCESSED.clear();
    }

    @Bean
    @AdharBatchJob(parameters = @JobParam(name = "run", required = true))
    Job ledgerJob(JobRepository jobRepository, AdharSteps steps, ListItemReader<Entry> ledgerReader,
                  ItemWriter<Entry> ledgerWriter) {
        ItemProcessor<Entry, Entry> processor = entry -> {
            PROCESSED.add(entry.id());
            if (REJECT_PROCESS.contains(entry.id())) {
                throw new IllegalArgumentException("entry " + entry.id() + " does not balance");
            }
            return entry;
        };
        var step = steps.<Entry, Entry>chunk(LEDGER_STEP, 3)
                .reader(ledgerReader)
                .processor(processor)
                .writer(ledgerWriter)
                .faultTolerance(FaultTolerance.none().skip(IllegalArgumentException.class, 100).deadLetter())
                .build();
        return new JobBuilder(LEDGER_JOB, jobRepository).start(step).build();
    }

    @Bean
    @StepScope
    ListItemReader<Entry> ledgerReader() {
        return new ListItemReader<>(LongStream.rangeClosed(1, ITEMS).mapToObj(id -> new Entry(id, "entry-" + id))
                .toList());
    }

    @Bean
    @StepScope
    ItemWriter<Entry> ledgerWriter(JdbcTemplate jdbc, @Value("#{jobParameters['run']}") String run) {
        return chunk -> {
            for (Entry entry : chunk) {
                if (REJECT_WRITE.contains(entry.id())) {
                    throw new IllegalArgumentException("entry " + entry.id() + " is refused by the ledger");
                }
                jdbc.update("INSERT INTO OPS_LEDGER (ID, RUN) VALUES (?, ?)", entry.id(), run);
            }
        };
    }
}
