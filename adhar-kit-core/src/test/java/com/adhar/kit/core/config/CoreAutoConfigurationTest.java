package com.adhar.kit.core.config;

import com.adhar.kit.core.CoreFacade;
import com.adhar.kit.core.annotation.Async;
import com.adhar.kit.core.annotation.Memoize;
import com.adhar.kit.core.annotation.Retry;
import com.adhar.kit.core.aspect.AsyncAspect;
import com.adhar.kit.core.aspect.MemoizeAspect;
import com.adhar.kit.core.aspect.RetryAspect;
import com.adhar.kit.core.util.ContextPropagatingExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CoreAutoConfiguration Tests")
class CoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CoreAutoConfiguration.class));

    @Nested
    @DisplayName("Bean Registration")
    class BeanRegistration {

        @Test
        @DisplayName("registers facade, executor, ObjectMapper and aspects by default")
        void registersCoreBeans() {
            runner.run(context -> {
                assertThat(context).hasSingleBean(CoreFacade.class);
                assertThat(context).hasSingleBean(ObjectMapper.class);
                assertThat(context).hasSingleBean(AdharCoreProperties.class);
                assertThat(context).hasBean(CoreAutoConfiguration.ASYNC_EXECUTOR_BEAN);
                assertThat(context.getBean(CoreAutoConfiguration.ASYNC_EXECUTOR_BEAN))
                    .isInstanceOf(ContextPropagatingExecutor.class);
                assertThat(context).hasSingleBean(RetryAspect.class);
                assertThat(context).hasSingleBean(MemoizeAspect.class);
                assertThat(context).hasSingleBean(AsyncAspect.class);
            });
        }

        @Test
        @DisplayName("adhar.core.enabled=false disables everything")
        void disabledViaProperty() {
            runner.withPropertyValues("adhar.core.enabled=false").run(context -> {
                assertThat(context).doesNotHaveBean(CoreFacade.class);
                assertThat(context).doesNotHaveBean(RetryAspect.class);
                assertThat(context).doesNotHaveBean(CoreAutoConfiguration.ASYNC_EXECUTOR_BEAN);
            });
        }

        @Test
        @DisplayName("adhar.core.aspects.enabled=false disables only the aspects")
        void aspectsDisabledViaProperty() {
            runner.withPropertyValues("adhar.core.aspects.enabled=false").run(context -> {
                assertThat(context).hasSingleBean(CoreFacade.class);
                assertThat(context).doesNotHaveBean(RetryAspect.class);
                assertThat(context).doesNotHaveBean(MemoizeAspect.class);
                assertThat(context).doesNotHaveBean(AsyncAspect.class);
            });
        }

        @Test
        @DisplayName("a user-supplied ObjectMapper is not overridden")
        void customObjectMapperRespected() {
            runner.withUserConfiguration(CustomMapperConfig.class).run(context -> {
                assertThat(context.getBean(ObjectMapper.class))
                    .isSameAs(CustomMapperConfig.MAPPER);
                assertThat(context.getBean(CoreFacade.class).getObjectMapper())
                    .isSameAs(CustomMapperConfig.MAPPER);
            });
        }

        @Test
        @DisplayName("executor pool is built from adhar.core.async.* properties")
        void executorUsesConfiguredPoolSizes() {
            runner.withPropertyValues(
                "adhar.core.async.pool-size=3",
                "adhar.core.async.max-pool-size=7",
                "adhar.core.async.queue-capacity=11",
                "adhar.core.async.thread-name-prefix=custom-async-"
            ).run(context -> {
                ContextPropagatingExecutor executor = (ContextPropagatingExecutor)
                    context.getBean(CoreAutoConfiguration.ASYNC_EXECUTOR_BEAN);
                ThreadPoolExecutor pool = (ThreadPoolExecutor) executor.getDelegate();

                assertThat(pool.getCorePoolSize()).isEqualTo(3);
                assertThat(pool.getMaximumPoolSize()).isEqualTo(7);
                assertThat(pool.getQueue().remainingCapacity()).isEqualTo(11);

                String threadName = executor.submit(() -> Thread.currentThread().getName())
                    .get(5, TimeUnit.SECONDS);
                assertThat(threadName).startsWith("custom-async-");
            });
        }

        @Test
        @DisplayName("the facade uses the configured snowflake node id")
        void facadeUsesConfiguredSnowflakeNodeId() {
            runner.withPropertyValues("adhar.core.snowflake.node-id=42").run(context -> {
                long id = context.getBean(CoreFacade.class).generateSnowflakeId();
                assertThat(com.adhar.kit.core.util.SnowflakeIdGenerator.extractNodeId(id)).isEqualTo(42);
            });
        }
    }

    @Nested
    @DisplayName("Aspect Weaving (end-to-end)")
    class AspectWeaving {

        @Test
        @DisplayName("@Retry on a Spring bean retries failed invocations")
        void retryAnnotationIsWoven() {
            runner.withUserConfiguration(SampleServicesConfig.class).run(context -> {
                FlakyService service = context.getBean(FlakyService.class);
                assertThat(service.call()).isEqualTo("recovered");
                assertThat(service.getInvocations()).isEqualTo(3);
            });
        }

        @Test
        @DisplayName("@Memoize on a Spring bean caches the result")
        void memoizeAnnotationIsWoven() {
            runner.withUserConfiguration(SampleServicesConfig.class).run(context -> {
                MemoService service = context.getBean(MemoService.class);
                assertThat(service.compute("k")).isEqualTo("value-1");
                assertThat(service.compute("k")).isEqualTo("value-1");
                assertThat(service.getInvocations()).isEqualTo(1);
            });
        }

        @Test
        @DisplayName("@Async on a Spring bean runs on the configured executor")
        void asyncAnnotationIsWoven() {
            runner.withUserConfiguration(SampleServicesConfig.class).run(context -> {
                AsyncService service = context.getBean(AsyncService.class);
                String threadName = service.currentThread().get(5, TimeUnit.SECONDS);
                assertThat(threadName).startsWith("adhar-async-");
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomMapperConfig {
        static final ObjectMapper MAPPER = new ObjectMapper();

        @Bean
        ObjectMapper objectMapper() {
            return MAPPER;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SampleServicesConfig {

        @Bean
        FlakyService flakyService() {
            return new FlakyService();
        }

        @Bean
        MemoService memoService() {
            return new MemoService();
        }

        @Bean
        AsyncService asyncService() {
            return new AsyncService();
        }
    }

    public static class FlakyService {
        private final AtomicInteger invocations = new AtomicInteger();

        @Retry(maxAttempts = 3, backoff = @Retry.Backoff(delay = 1, multiplier = 1.0))
        public String call() {
            if (invocations.incrementAndGet() < 3) {
                throw new IllegalStateException("transient failure");
            }
            return "recovered";
        }

        // Accessed through the proxy, which delegates to the target instance
        public int getInvocations() {
            return invocations.get();
        }
    }

    public static class MemoService {
        private final AtomicInteger invocations = new AtomicInteger();

        @Memoize
        public String compute(String key) {
            return "value-" + invocations.incrementAndGet();
        }

        // Accessed through the proxy, which delegates to the target instance
        public int getInvocations() {
            return invocations.get();
        }
    }

    public static class AsyncService {

        @Async
        public CompletableFuture<String> currentThread() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}
