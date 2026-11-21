package com.adhar.kit.batch.params;

import com.adhar.kit.batch.annotation.AdharBatchJob;
import com.adhar.kit.batch.annotation.JobParam;
import com.adhar.kit.batch.annotation.ParamSource;
import com.adhar.kit.batch.annotation.ParamType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JobParameterSpecRegistry} and {@link JobParameterSpec}: declarations collected from bean
 * methods, classes and spec beans, and broken declarations failing the startup.
 */
class JobParameterSpecRegistryTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JobParameterSpecRegistry.class);

    static Job job(String name) {
        return new Job() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public void execute(JobExecution execution) {
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class Jobs {

        @Bean
        @AdharBatchJob(description = "Imports", parameters = {
                @JobParam(name = "businessDate", type = ParamType.DATE, source = ParamSource.SCHEDULE),
                @JobParam(name = "source", defaultValue = "inbound/records.csv", pattern = "^[a-z/.]+$",
                        sensitive = true),
                @JobParam(name = "dryRun", type = ParamType.BOOLEAN, identifying = false, defaultValue = "false")})
        Job importJob() {
            return job("importJob");
        }

        @Bean
        @AdharBatchJob(allowIncrementer = true)
        Job housekeepingBean() {
            return job("housekeeping");
        }

        @Bean
        Job undeclared() {
            return job("undeclared");
        }

        @Bean
        JobParameterSpec computedSpec() {
            return new JobParameterSpec("computed", "", List.of(JobParameterSpec.Param.of("n", ParamType.LONG)), false);
        }
    }

    @Test
    @DisplayName("declarations on bean methods and spec beans are found by job name and by bean name")
    void collected() {
        runner.withUserConfiguration(Jobs.class).run(context -> {
            JobParameterSpecRegistry registry = context.getBean(JobParameterSpecRegistry.class);

            JobParameterSpec importSpec = registry.spec("importJob");
            assertThat(importSpec.description()).isEqualTo("Imports");
            assertThat(importSpec.parameters()).extracting(JobParameterSpec.Param::name)
                    .containsExactly("businessDate", "source", "dryRun");
            assertThat(importSpec.param("source")).get().extracting(JobParameterSpec.Param::defaultValue)
                    .isEqualTo("inbound/records.csv");
            assertThat(importSpec.sensitiveNames()).containsExactly("source");
            assertThat(registry.spec("housekeeping").allowIncrementer()).isTrue();
            assertThat(registry.find("housekeepingBean")).as("by bean name").contains(registry.spec("housekeeping"));
            assertThat(registry.spec("computed").parameters()).hasSize(1);
            assertThat(registry.find("undeclared")).isEmpty();
            assertThat(registry.spec("undeclared").parameters()).isEmpty();
            assertThat(registry.all()).containsKeys("importJob", "housekeeping", "housekeepingBean", "computed");
        });
    }

    /** A consumer registered ahead of the registry, as the imported Quartz engine is. */
    static class EarlyReader implements SmartInitializingSingleton {

        private final JobParameterSpecRegistry registry;
        private JobParameterSpec seen;

        EarlyReader(JobParameterSpecRegistry registry) {
            this.registry = registry;
        }

        @Override
        public void afterSingletonsInstantiated() {
            seen = registry.spec("importJob");
        }
    }

    @Test
    @DisplayName("a SmartInitializingSingleton whose callback runs before the registry's still sees every declaration")
    void readBeforeTheRegistrysOwnCallback() {
        new ApplicationContextRunner()
                .withBean(EarlyReader.class)
                .withBean(JobParameterSpecRegistry.class)
                .withUserConfiguration(Jobs.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EarlyReader.class).seen.parameters()).extracting(JobParameterSpec.Param::name)
                            .containsExactly("businessDate", "source", "dryRun");
                    assertThat(context.getBean(JobParameterSpecRegistry.class).all())
                            .as("collected once: the registry's own callback adds nothing twice")
                            .containsKeys("importJob", "housekeeping", "computed");
                });
    }

    @AdharBatchJob(description = "on the class")
    static class AnnotatedJob implements Job {
        @Override
        public String getName() {
            return "classJob";
        }

        @Override
        public void execute(JobExecution execution) {
        }
    }

    @Test
    @DisplayName("a declaration on the job's class counts too")
    void onTheClass() {
        runner.withBean("classJob", AnnotatedJob.class).run(context ->
                assertThat(context.getBean(JobParameterSpecRegistry.class).spec("classJob").description())
                        .isEqualTo("on the class"));
    }

    @Configuration(proxyBeanMethods = false)
    static class MismatchedName {
        @Bean
        @AdharBatchJob(name = "other")
        Job importJob() {
            return job("importJob");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoDeclarations {
        @Bean
        @AdharBatchJob
        Job importJob() {
            return job("importJob");
        }

        @Bean
        JobParameterSpec importSpec() {
            return JobParameterSpec.none("importJob");
        }
    }

    @Test
    @DisplayName("a name that is not the job's, or two declarations of one job, fail the startup")
    void inconsistentDeclarations() {
        runner.withUserConfiguration(MismatchedName.class).run(context -> assertThat(context).hasFailed()
                .getFailure().hasMessageContaining("names the job [other], but the job is named [importJob]"));
        runner.withUserConfiguration(TwoDeclarations.class).run(context -> assertThat(context).hasFailed()
                .getFailure().hasMessageContaining("Job [importJob] has two parameter declarations"));
    }

    @Test
    @DisplayName("a declaration is checked when it is made: names, defaults, allowed values, patterns, sources")
    void declarationChecks() {
        assertThatThrownBy(() -> new JobParameterSpec("j", null, List.of(JobParameterSpec.Param.of("a", ParamType.LONG),
                JobParameterSpec.Param.of("a", ParamType.LONG)), false)).hasMessageContaining("declares parameter a twice");
        assertThatThrownBy(() -> JobParameterSpec.Param.of("1st", ParamType.STRING)).hasMessageContaining("must start");
        assertThatThrownBy(() -> JobParameterSpec.Param.of("adhar.trigger", ParamType.STRING))
                .hasMessageContaining("reserves");
        assertThatThrownBy(() -> new JobParameterSpec.Param("n", ParamType.LONG, true, false, "x", null, List.of(),
                ParamSource.INPUT, false, null)).hasMessageContaining("The default of parameter n is invalid");
        assertThatThrownBy(() -> new JobParameterSpec.Param("n", ParamType.LONG, true, false, null, null, List.of("x"),
                ParamSource.INPUT, false, null)).hasMessageContaining("allows 'x', which is not a LONG");
        assertThatThrownBy(() -> new JobParameterSpec.Param("n", ParamType.STRING, true, false, null, "(", List.of(),
                ParamSource.INPUT, false, null)).hasMessageContaining("invalid pattern");
        assertThatThrownBy(() -> new JobParameterSpec.Param("n", ParamType.STRING, true, false, null, null, List.of(),
                ParamSource.SCHEDULE, false, null)).hasMessageContaining("must be a DATE or DATETIME");
        JobParameterSpec.Param defaults = new JobParameterSpec.Param("n", null, true, false, null, null, null, null,
                false, null);
        assertThat(defaults.type()).isEqualTo(ParamType.STRING);
        assertThat(defaults.source()).isEqualTo(ParamSource.INPUT);
        assertThat(defaults.toString()).isEqualTo("n:STRING");
        assertThat(JobParameterSpecRegistry.of(List.of(JobParameterSpec.none("x"))).find("x")).isPresent();
    }
}
