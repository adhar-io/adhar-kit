package com.adhar.kit.test.batch;

import com.adhar.kit.batch.annotation.AdharBatchJob;
import com.adhar.kit.batch.annotation.JobParam;
import com.adhar.kit.batch.annotation.ParamSource;
import com.adhar.kit.batch.annotation.ParamType;
import com.adhar.kit.batch.reader.KeysetPagingItemReaderBuilder;
import com.adhar.kit.batch.step.AdharSteps;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * The application of the batch self-tests: {@value #JOB} copies {@code source_items} into
 * {@code target_items} in chunks of {@value #CHUNK}; its writer inserts the chunk and THEN checks
 * the {@link FailureInjector}, so a failed chunk's rows exist when it fails and only the shared
 * transaction rolls them back.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class CopyJobApplication {

    static final String JOB = "copyJob";
    static final int CHUNK = 10;

    @Bean
    FailureInjector failureInjector() {
        return new FailureInjector();
    }

    @Bean
    Step copyStep(AdharSteps steps, DataSource dataSource, JdbcTemplate jdbc, FailureInjector failures) {
        var reader = KeysetPagingItemReaderBuilder.of(dataSource, (rs, i) -> rs.getLong("id"))
                .from("source_items").key("id").pageSize(CHUNK).name("source").build();
        ItemWriter<Long> writer = chunk -> {
            chunk.forEach(id -> jdbc.update("INSERT INTO target_items (id) VALUES (?)", id));
            chunk.forEach(failures::check);
        };
        return steps.<Long, Long>chunk("copyStep", CHUNK).reader(reader).writer(writer).build();
    }

    @Bean
    @AdharBatchJob(description = "Copies the source items", parameters = {
            @JobParam(name = "businessDate", type = ParamType.DATE, source = ParamSource.SCHEDULE),
            @JobParam(name = "dryRun", type = ParamType.BOOLEAN, identifying = false, defaultValue = "false")})
    Job copyJob(JobRepository jobRepository, Step copyStep) {
        return new JobBuilder(JOB, jobRepository).start(copyStep).build();
    }

    static void seed(JdbcTemplate jdbc, int count) {
        jdbc.update("DELETE FROM source_items");
        jdbc.update("DELETE FROM target_items");
        for (long id = 1; id <= count; id++) {
            jdbc.update("INSERT INTO source_items (id) VALUES (?)", id);
        }
    }
}
