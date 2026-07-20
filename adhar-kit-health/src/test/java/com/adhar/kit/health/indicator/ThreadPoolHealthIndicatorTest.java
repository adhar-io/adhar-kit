package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ThreadPoolHealthIndicator}.
 */
class ThreadPoolHealthIndicatorTest {

    @Test
    void check_idlePool_returnsUp() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
        try {
            Health health = new ThreadPoolHealthIndicator(pool).check();

            assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
            assertThat(health.getComponent()).isEqualTo("threadPool");
            assertThat(health.getDetails())
                .containsEntry("queueSize", 0)
                .containsEntry("queueRemainingCapacity", 10)
                .containsEntry("corePoolSize", 1)
                .containsEntry("maximumPoolSize", 1)
                .containsKeys("poolSize", "activeThreads", "queueUsage");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void check_saturatedQueue_returnsDown() throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            // occupy the single worker …
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            // … and fill the queue completely
            pool.execute(() -> {
            });

            Health health = new ThreadPoolHealthIndicator("workers", pool, 0.9).check();

            assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
            assertThat(health.getComponent()).isEqualTo("workers");
            assertThat(health.getError()).contains("saturated");
            assertThat(health.getDetails()).containsEntry("queueRemainingCapacity", 0);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void check_shutdownExecutor_returnsOutOfService() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        pool.shutdown();

        Health health = new ThreadPoolHealthIndicator(pool).check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("message", "Executor has been shut down");
    }

    @Test
    void check_unsupportedExecutorType_returnsUnknown() {
        ExecutorService pool = Executors.newWorkStealingPool();
        try {
            Health health = new ThreadPoolHealthIndicator(pool).check();

            assertThat(health.getStatus()).isEqualTo(Health.Status.UNKNOWN);
            assertThat(health.getDetails().get("message").toString()).contains("Unsupported executor type");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void check_unboundedQueue_neverSaturates() {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        try {
            Health health = new ThreadPoolHealthIndicator(pool).check();

            assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void check_executorThrows_returnsDown() {
        ExecutorService broken = mock(ExecutorService.class);
        when(broken.isShutdown()).thenThrow(new IllegalStateException("broken"));

        Health health = new ThreadPoolHealthIndicator(broken).check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getError()).isEqualTo("broken");
    }

    @Test
    void configConstructor_appliesThreshold() {
        AdharHealthProperties.ThreadPoolConfig config = new AdharHealthProperties.ThreadPoolConfig();
        config.setQueueUsageThreshold(0.5);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
        try {
            Health health = new ThreadPoolHealthIndicator(pool, config).check();

            assertThat(health.getDetails()).containsEntry("queueUsageThreshold", 0.5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void getName_supportsCustomNames() {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            assertThat(new ThreadPoolHealthIndicator(pool).getName()).isEqualTo("threadPool");
            assertThat(new ThreadPoolHealthIndicator("io-pool", pool, 0.9).getName()).isEqualTo("io-pool");
        } finally {
            pool.shutdownNow();
        }
    }
}
