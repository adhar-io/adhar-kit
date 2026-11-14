package com.adhar.kit.batch.support;

import com.adhar.kit.batch.AdharBatchApplication;
import com.adhar.kit.batch.runtime.TerminationSummaryWriter;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;

/**
 * A batch application as a template's {@code main} writes it, for {@code RunnerExitCodeIT}: it is
 * started in its own JVM with the environment the {@code BatchJob} composition sets, on an
 * in-memory H2 database. {@code TEST_FAILURE} and {@code TEST_CHUNK_MILLIS} steer the test jobs,
 * {@code TEST_TERMINATION_LOG} replaces {@code /dev/termination-log}.
 */
@Configuration(proxyBeanMethods = false)
@Import({BatchTestApplication.class, RunnerJobs.class})
public class RunnerExitCodeApp {

    @Bean
    TerminationSummaryWriter terminationSummaryWriter() {
        return new TerminationSummaryWriter(Path.of(System.getenv("TEST_TERMINATION_LOG")), System.out);
    }

    public static void main(String[] args) {
        RunnerJobs.FAILURE.set(System.getenv().getOrDefault("TEST_FAILURE", ""));
        RunnerJobs.CHUNK_MILLIS.set(Long.parseLong(System.getenv().getOrDefault("TEST_CHUNK_MILLIS", "100")));
        SpringApplication application = new SpringApplication(RunnerExitCodeApp.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setDefaultProperties(BatchTestApplication.h2());
        AdharBatchApplication.run(application, System::exit, args);
    }
}
