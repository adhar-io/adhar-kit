package com.adhar.kit.batch;

import com.adhar.kit.batch.repository.BatchRepositoryException;
import com.adhar.kit.batch.runtime.AdharBatchRunner;
import com.adhar.kit.batch.runtime.BatchExitCodeGenerator;
import com.adhar.kit.batch.runtime.ExitCodes;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup;
import com.adhar.kit.batch.runtime.RunOutcome;
import com.adhar.kit.batch.runtime.TerminationSummaryWriter;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.RunnerJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AdharBatchApplication} in process, with the real Kubernetes-mode auto-configuration:
 * the exit code a run produces, a start-up failure's code, and no exit during a shutdown.
 * {@code RunnerExitCodeIT} proves the same with real processes.
 */
class AdharBatchApplicationTest {

    @TempDir
    Path dir;

    @Configuration(proxyBeanMethods = false)
    @Import({BatchTestApplication.class, RunnerJobs.class})
    static class App {

        static Path log;

        @Bean
        TerminationSummaryWriter terminationSummaryWriter() {
            return new TerminationSummaryWriter(log, new PrintStream(OutputStream.nullOutputStream()));
        }

        @Bean
        KubernetesOwnerLookup kubernetesOwnerLookup() {
            return KubernetesOwnerLookup.unavailable();
        }
    }

    private int run(Map<String, Object> properties) {
        App.log = dir.resolve("termination-log");
        SpringApplication application = new SpringApplication(App.class);
        application.setBannerMode(Banner.Mode.OFF);
        Map<String, Object> defaults = BatchTestApplication.h2();
        defaults.putAll(properties);
        application.setDefaultProperties(defaults);
        List<Integer> exits = new ArrayList<>();
        AdharBatchApplication.run(application, exits::add);
        assertThat(exits).hasSize(1);
        return exits.getFirst();
    }

    @Test
    @DisplayName("Kubernetes mode runs the named job while the context starts and exits with its code")
    void kubernetesRun() throws Exception {
        assertThat(run(Map.of("adhar.batch.job", RunnerJobs.REPORT_JOB,
                "ADHAR_BATCH_PARAMS", "{\"businessDate\":\"2026-11-03\"}"))).isZero();
        assertThat(Files.readString(dir.resolve("termination-log"))).contains("\"outcome\":\"completed\"");

        assertThat(run(Map.of("adhar.batch.job", RunnerJobs.REPORT_JOB,
                "ADHAR_BATCH_PARAMS", "{\"region\":\"xx\"}"))).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(Files.readString(dir.resolve("termination-log"))).contains("\"reason\":\"parameters\"");
    }

    @Test
    @DisplayName("a context that cannot start exits with the failure's own code, else 1")
    void startupFailure() {
        assertThat(run(Map.of("adhar.batch.job", RunnerJobs.REPORT_JOB, "adhar.batch.chunk.size", "0")))
                .isEqualTo(ExitCodes.STARTUP_FAILURE);
        assertThat(AdharBatchApplication.exitCodeOf(new IllegalStateException(new BatchRepositoryException("no db"))))
                .isEqualTo(ExitCodes.INVALID_REQUEST);
        ExitCodeGenerator zero = () -> 0;
        assertThat(AdharBatchApplication.exitCodeOf(new ZeroCode(zero))).isEqualTo(ExitCodes.STARTUP_FAILURE);
    }

    static class ZeroCode extends RuntimeException implements ExitCodeGenerator {
        private final transient ExitCodeGenerator delegate;

        ZeroCode(ExitCodeGenerator delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getExitCode() {
            return delegate.getExitCode();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ClosesItself {
        @Bean
        ApplicationRunner closer(ConfigurableApplicationContext context) {
            return args -> context.close();
        }
    }

    @Test
    @DisplayName("a context already closing (SIGTERM) is left to the JVM: no exit call, which would block")
    void noExitDuringShutdown() {
        SpringApplication application = new SpringApplication(ClosesItself.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setDefaultProperties(Map.of("spring.main.web-application-type", "none"));
        List<Integer> exits = new ArrayList<>();

        AdharBatchApplication.run(application, exits::add);

        assertThat(exits).isEmpty();
    }

    @Test
    @DisplayName("the exit code generator answers the run's code, 0 until there is one, before any other generator")
    void exitCodeGenerator() {
        BatchExitCodeGenerator generator = new BatchExitCodeGenerator();
        assertThat(generator.getExitCode()).isZero();
        assertThat(generator.outcome()).isEmpty();
        assertThat(generator.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);

        generator.record(RunOutcome.failed(ExitCodes.FAILED_RETRYABLE, "x"));

        assertThat(generator.getExitCode()).isEqualTo(5);
        assertThat(new AdharBatchRunner(() -> null, null, name -> null, null, null).getOrder())
                .isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }
}
