package com.adhar.kit.batch.support;

import com.adhar.kit.batch.AdharBatchApplication;
import com.adhar.kit.batch.runtime.TerminationSummaryWriter;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A remote-partitioned batch application as the partitioned template's {@code main} writes it, for
 * {@code RemotePartitioningIT}: one image for every role, started in its own JVM with the environment
 * the {@code BatchJob} composition sets on a gang's manager and worker pods (or an Indexed Job's),
 * against the test's PostgreSQL through {@code SPRING_DATASOURCE_*}. {@code TEST_CHUNK_MILLIS} slows
 * each chunk down, {@code TEST_TERMINATION_LOG} replaces {@code /dev/termination-log}.
 */
@Configuration(proxyBeanMethods = false)
@Import({BatchTestApplication.class, PartitionedJobs.class})
public class PartitionedRunnerApp {

    @Bean
    TerminationSummaryWriter terminationSummaryWriter() {
        return new TerminationSummaryWriter(Path.of(System.getenv("TEST_TERMINATION_LOG")), System.out);
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PartitionedRunnerApp.class);
        application.setBannerMode(Banner.Mode.OFF);
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("spring.sql.init.mode", "always");
        defaults.put("spring.sql.init.schema-locations", "classpath:adhar-batch-test/business-schema.sql");
        defaults.put("spring.batch.job.enabled", "false");
        defaults.put("test.chunk-millis", System.getenv().getOrDefault("TEST_CHUNK_MILLIS", "0"));
        application.setDefaultProperties(defaults);
        AdharBatchApplication.run(application, System::exit, args);
    }
}
