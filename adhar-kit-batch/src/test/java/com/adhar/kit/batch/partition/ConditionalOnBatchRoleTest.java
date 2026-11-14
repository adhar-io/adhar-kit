package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.runtime.BatchRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The role switch (design 0025 §5.10): one {@code @Configuration}, beans by role. {@code indexed}
 * resolves through {@code JOB_COMPLETION_INDEX} as the runner resolves it, and {@code single} plays
 * every part.
 */
class ConditionalOnBatchRoleTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner().withUserConfiguration(Roles.class);

    @Test
    @DisplayName("manager: the manager-only bean, never the worker step")
    void manager() {
        contexts.withPropertyValues("adhar.batch.role=manager").run(context -> {
            assertThat(context).hasBean("managerOnly").doesNotHaveBean("workerStep");
        });
    }

    @Test
    @DisplayName("worker: the worker step, never the manager-only bean")
    void worker() {
        contexts.withPropertyValues("adhar.batch.role=worker").run(context -> {
            assertThat(context).hasBean("workerStep").doesNotHaveBean("managerOnly");
        });
    }

    @Test
    @DisplayName("indexed: index 0 is the manager, any other index a worker")
    void indexed() {
        contexts.withPropertyValues("adhar.batch.role=indexed", "JOB_COMPLETION_INDEX=0").run(context -> {
            assertThat(context).hasBean("managerOnly").doesNotHaveBean("workerStep");
            assertThat(OnBatchRoleCondition.effectiveRole(context.getEnvironment())).isEqualTo(BatchRole.MANAGER);
        });
        contexts.withPropertyValues("adhar.batch.role=INDEXED", "JOB_COMPLETION_INDEX=3").run(context -> {
            assertThat(context).hasBean("workerStep").doesNotHaveBean("managerOnly");
            assertThat(OnBatchRoleCondition.effectiveRole(context.getEnvironment())).isEqualTo(BatchRole.WORKER);
        });
    }

    @Test
    @DisplayName("single (the default) plays every part; a single-only bean exists only there")
    void single() {
        contexts.run(context -> assertThat(context).hasBean("managerOnly").hasBean("workerStep").hasBean("singleOnly"));
        contexts.withPropertyValues("adhar.batch.role=worker").run(context ->
                assertThat(context).doesNotHaveBean("singleOnly"));
    }

    @Test
    @DisplayName("a completion index that is not a number fails the start")
    void badIndex() {
        contexts.withPropertyValues("adhar.batch.role=indexed", "JOB_COMPLETION_INDEX=x").run(context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining("JOB_COMPLETION_INDEX"));
    }

    @Configuration(proxyBeanMethods = false)
    static class Roles {

        @Bean
        @ConditionalOnBatchRole(BatchRole.MANAGER)
        String managerOnly() {
            return "manager";
        }

        @Bean
        @ConditionalOnBatchRole(BatchRole.WORKER)
        String workerStep() {
            return "worker";
        }

        @Bean
        @ConditionalOnBatchRole(BatchRole.SINGLE)
        String singleOnly() {
            return "single";
        }
    }
}
