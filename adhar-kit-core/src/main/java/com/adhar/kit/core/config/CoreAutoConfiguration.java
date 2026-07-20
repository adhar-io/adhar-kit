package com.adhar.kit.core.config;

import com.adhar.kit.core.CoreFacade;
import com.adhar.kit.core.aspect.AsyncAspect;
import com.adhar.kit.core.aspect.MemoizeAspect;
import com.adhar.kit.core.aspect.RetryAspect;
import com.adhar.kit.core.util.ContextPropagatingExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Auto-configuration for Adhar Kit core utilities.
 *
 * <p>Binds {@link AdharCoreProperties} (prefix {@code adhar.core}) and exposes:</p>
 * <ul>
 *   <li>an MDC context-propagating async {@link ExecutorService} built from the
 *       configured pool sizes ({@code adhar.core.async.*})</li>
 *   <li>a JavaTime-aware {@link ObjectMapper} (only if none is present)</li>
 *   <li>a {@link CoreFacade} bean wired to the above</li>
 *   <li>AOP aspects for {@code @Retry}, {@code @Memoize} and {@code @Async}
 *       (toggle with {@code adhar.core.aspects.enabled}, default {@code true})</li>
 * </ul>
 *
 * <p>Disable everything with {@code adhar.core.enabled=false}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(AdharCoreProperties.class)
@ConditionalOnProperty(prefix = "adhar.core", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CoreAutoConfiguration.class);

    /**
     * Name of the async executor bean created by this auto-configuration.
     */
    public static final String ASYNC_EXECUTOR_BEAN = "adharCoreAsyncExecutor";

    /**
     * Creates the async executor from {@code adhar.core.async.*} properties,
     * wrapped so the SLF4J MDC propagates from submitter to worker threads.
     *
     * @param properties the bound core properties
     * @return MDC-propagating executor service
     */
    @Bean(name = ASYNC_EXECUTOR_BEAN, destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = ASYNC_EXECUTOR_BEAN)
    public ExecutorService adharCoreAsyncExecutor(AdharCoreProperties properties) {
        AdharCoreProperties.AsyncConfig async = properties.getAsync();
        log.info("Creating Adhar core async executor (poolSize={}, maxPoolSize={}, queueCapacity={})",
            async.getPoolSize(), async.getMaxPoolSize(), async.getQueueCapacity());

        AtomicLong threadCounter = new AtomicLong();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(async.getThreadNamePrefix() + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            async.getPoolSize(),
            Math.max(async.getMaxPoolSize(), async.getPoolSize()),
            async.getKeepAliveSeconds(), TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(async.getQueueCapacity()),
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy());

        return new ContextPropagatingExecutor(pool);
    }

    /**
     * Provides a JavaTime-aware ObjectMapper if the application has none.
     *
     * @return ObjectMapper with the JavaTimeModule registered
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    /**
     * Exposes the {@link CoreFacade} wired to the configured executor and
     * ObjectMapper (instead of the hand-rolled singleton defaults).
     *
     * @param properties the bound core properties
     * @param asyncExecutor the configured async executor
     * @param objectMapper the application ObjectMapper
     * @return CoreFacade bean
     */
    @Bean
    @ConditionalOnMissingBean
    public CoreFacade coreFacade(AdharCoreProperties properties,
                                 @Qualifier(ASYNC_EXECUTOR_BEAN) ExecutorService asyncExecutor,
                                 ObjectMapper objectMapper) {
        return new CoreFacade(properties, asyncExecutor, objectMapper);
    }

    /**
     * Registers the aspects backing the {@code @Retry}, {@code @Memoize} and
     * {@code @Async} annotations. Disable with
     * {@code adhar.core.aspects.enabled=false}.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @ConditionalOnClass(ProceedingJoinPoint.class)
    @ConditionalOnProperty(prefix = "adhar.core.aspects", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static class AspectConfiguration {

        /**
         * Aspect for {@code @Retry}.
         *
         * @return retry aspect
         */
        @Bean
        @ConditionalOnMissingBean
        public RetryAspect adharRetryAspect() {
            return new RetryAspect();
        }

        /**
         * Aspect for {@code @Memoize}.
         *
         * @return memoize aspect
         */
        @Bean
        @ConditionalOnMissingBean
        public MemoizeAspect adharMemoizeAspect() {
            return new MemoizeAspect();
        }

        /**
         * Aspect for {@code @Async}, dispatching to the configured executor.
         *
         * @param asyncExecutor the configured async executor
         * @return async aspect
         */
        @Bean
        @ConditionalOnMissingBean
        public AsyncAspect adharAsyncAspect(@Qualifier(ASYNC_EXECUTOR_BEAN) ExecutorService asyncExecutor) {
            return new AsyncAspect(asyncExecutor);
        }
    }
}
