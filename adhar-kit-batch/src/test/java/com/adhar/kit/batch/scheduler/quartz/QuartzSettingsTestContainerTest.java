package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.config.BatchAutoConfiguration;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.QuartzContexts;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.quartz.Scheduler;
import org.springframework.batch.core.job.Job;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * KIT-Q.2 (design 0025 §5.20.4, AQ.7): every Quartz setting the kit owns is one a guarantee rests
 * on, so an application value that contradicts it fails the start, naming the property. One start
 * per owned setting, against a real context on PostgreSQL.
 */
@Testcontainers(disabledWithoutDocker = true)
class QuartzSettingsTestContainerTest {

    private static final String Q = "spring.quartz.properties.";

    private static String url;

    @BeforeAll
    static void database() {
        url = PostgresDatabases.createDatabase("qsettings");
    }

    static Stream<Arguments> contradictions() {
        return Stream.of(
                Arguments.of("spring.quartz.job-store-type", "memory"),
                Arguments.of("spring.quartz.jdbc.initialize-schema", "always"),
                Arguments.of("spring.quartz.jdbc.initialize-schema", "embedded"),
                Arguments.of("spring.quartz.overwrite-existing-jobs", "false"),
                Arguments.of("spring.quartz.wait-for-jobs-to-complete-on-shutdown", "true"),
                Arguments.of("spring.quartz.scheduler-name", "another"),
                Arguments.of(Q + "org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate"),
                Arguments.of(Q + "org.quartz.jobStore.tablePrefix", "QRTZ_"),
                Arguments.of(Q + "org.quartz.jobStore.isClustered", "false"),
                Arguments.of(Q + "org.quartz.jobStore.acquireTriggersWithinLock", "false"),
                Arguments.of(Q + "org.quartz.jobStore.useProperties", "false"),
                Arguments.of(Q + "org.quartz.jobStore.misfireThreshold", "60000"),
                Arguments.of(Q + "org.quartz.jobStore.clusterCheckinInterval", "20000"),
                Arguments.of(Q + "org.quartz.scheduler.instanceName", "another"),
                Arguments.of(Q + "org.quartz.scheduler.instanceId", "replica-1"),
                Arguments.of(Q + "org.quartz.threadPool.threadCount", "10"),
                Arguments.of(Q + "org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore"),
                Arguments.of("adhar.batch.scheduler.lock", "none"),
                Arguments.of("adhar.batch.repository.table-prefix", "QRTZ_"));
    }

    @ParameterizedTest(name = "{0}={1} fails the start, naming the property")
    @MethodSource("contradictions")
    void aContradictingValueFailsTheStart(String property, String value) {
        Map<String, Object> properties = QuartzContexts.properties(url, "settings");
        properties.put(property, value);

        assertThatThrownBy(() -> start(properties))
                .satisfies(failure -> assertThat(NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                        .contains(property.replace(Q, "").replace("adhar.batch.", "adhar.batch.")));
    }

    @Test
    @DisplayName("values equal to the kit's are accepted, case-insensitively where Quartz and PostgreSQL are")
    void agreeingValuesAreAccepted() {
        Map<String, Object> properties = QuartzContexts.properties(url, "agreeing");
        properties.put("spring.quartz.job-store-type", "JDBC");
        properties.put(Q + "org.quartz.jobStore.tablePrefix", "BATCH.qrtz_");
        properties.put(Q + "org.quartz.jobStore.isClustered", "TRUE");
        properties.put(Q + "org.quartz.scheduler.instanceId", "auto");

        try (ConfigurableApplicationContext context = start(properties)) {
            assertThat(context.getBean(BatchScheduler.class).engine().name()).isEqualTo("quartz");
        }
    }

    @Test
    @DisplayName("without a scheduler name or spring.application.name the start fails naming the property")
    void theSchedulerNameIsRequired() {
        Map<String, Object> properties = QuartzContexts.properties(url, "unnamed");
        properties.remove("adhar.batch.quartz.scheduler-name");
        properties.remove("spring.application.name");

        assertThatThrownBy(() -> start(properties))
                .satisfies(failure -> assertThat(NestedExceptionUtils.getMostSpecificCause(failure).getMessage())
                        .contains("adhar.batch.quartz.scheduler-name"));
    }

    @Test
    @DisplayName("engine=quartz without Quartz on the classpath fails the start naming the dependency")
    void withoutQuartzTheStartFailsNamingTheDependency() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(BatchAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(Scheduler.class))
                .withBean("job", Job.class, () -> mock(Job.class))
                .withPropertyValues("adhar.batch.scheduler.enabled=true", "adhar.batch.scheduler.engine=quartz")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure()).getMessage())
                            .contains("spring-boot-starter-quartz");
                });
    }

    private static ConfigurableApplicationContext start(Map<String, Object> properties) {
        return QuartzContexts.start(properties, TestJobs.class);
    }
}
