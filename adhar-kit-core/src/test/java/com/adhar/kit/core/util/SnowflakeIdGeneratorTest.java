package com.adhar.kit.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SnowflakeIdGenerator Tests")
class SnowflakeIdGeneratorTest {

    @AfterEach
    void cleanup() {
        System.clearProperty(SnowflakeIdGenerator.NODE_ID_PROPERTY);
    }

    @Nested
    @DisplayName("ID Generation")
    class IdGeneration {

        @Test
        @DisplayName("generates positive, unique, strictly increasing ids")
        void generatesUniqueMonotonicIds() {
            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);

            long previous = -1;
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < 20_000; i++) {
                long id = generator.nextId();
                assertThat(id).isPositive();
                assertThat(id).isGreaterThan(previous);
                assertThat(seen.add(id)).isTrue();
                previous = id;
            }
        }

        @Test
        @DisplayName("encodes node id, timestamp and sequence in the id")
        void encodesComponents() {
            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(123);
            long before = System.currentTimeMillis();

            long id1 = generator.nextId();
            long id2 = generator.nextId();

            long after = System.currentTimeMillis();

            assertThat(SnowflakeIdGenerator.extractNodeId(id1)).isEqualTo(123);
            assertThat(SnowflakeIdGenerator.extractNodeId(id2)).isEqualTo(123);
            assertThat(SnowflakeIdGenerator.extractTimestamp(id1)).isBetween(before, after);
            assertThat(SnowflakeIdGenerator.extractSequence(id1)).isBetween(0L, 4095L);
        }

        @Test
        @DisplayName("rolls the sequence over to the next millisecond after 4096 ids")
        void sequenceRollover() {
            long base = System.currentTimeMillis();
            AtomicLong ticks = new AtomicLong();
            // Clock advances 1ms every 5000 reads, so >4096 ids land in one ms
            SnowflakeIdGenerator generator =
                new SnowflakeIdGenerator(7, () -> base + ticks.getAndIncrement() / 5000);

            Set<Long> ids = new HashSet<>();
            long previous = -1;
            for (int i = 0; i < 4200; i++) {
                long id = generator.nextId();
                assertThat(id).isGreaterThan(previous);
                assertThat(ids.add(id)).isTrue();
                previous = id;
            }

            assertThat(ids).hasSize(4200);
        }

        @Test
        @DisplayName("generates unique ids under concurrency")
        void uniqueUnderConcurrency() throws Exception {
            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(9);
            int threads = 4;
            int perThread = 5_000;
            Set<Long> ids = ConcurrentHashMap.newKeySet();
            CountDownLatch done = new CountDownLatch(threads);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                for (int t = 0; t < threads; t++) {
                    pool.execute(() -> {
                        for (int i = 0; i < perThread; i++) {
                            ids.add(generator.nextId());
                        }
                        done.countDown();
                    });
                }
                assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            } finally {
                pool.shutdownNow();
            }

            assertThat(ids).hasSize(threads * perThread);
        }
    }

    @Nested
    @DisplayName("Clock Drift Guard")
    class ClockDriftGuard {

        @Test
        @DisplayName("waits for the clock to catch up when it moves backwards")
        void waitsOnClockRegression() {
            long base = System.currentTimeMillis();
            List<Long> script = new ArrayList<>();
            script.add(base + 100);          // first id at base+100
            script.add(base);                // regression: 100ms backwards
            AtomicLong reads = new AtomicLong();
            // After the scripted values, keep advancing so the wait terminates
            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(5, () -> {
                long i = reads.getAndIncrement();
                if (i < script.size()) {
                    return script.get((int) i);
                }
                return base + (i - script.size()); // climbs back past base+100
            });

            long id1 = generator.nextId();
            long id2 = generator.nextId();  // sees regression, must wait

            assertThat(id2).isGreaterThan(id1);
            assertThat(SnowflakeIdGenerator.extractTimestamp(id2))
                .isGreaterThanOrEqualTo(SnowflakeIdGenerator.extractTimestamp(id1));
            // The guard had to poll the clock repeatedly to get past the regression
            assertThat(reads.get()).isGreaterThan(100);
        }
    }

    @Nested
    @DisplayName("Node ID Resolution")
    class NodeIdResolution {

        @Test
        @DisplayName("rejects node ids above the 10-bit maximum")
        void rejectsOutOfRangeNodeId() {
            assertThatThrownBy(() -> new SnowflakeIdGenerator(SnowflakeIdGenerator.MAX_NODE_ID + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1023");
        }

        @Test
        @DisplayName("accepts the maximum node id")
        void acceptsMaxNodeId() {
            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(SnowflakeIdGenerator.MAX_NODE_ID);
            assertThat(SnowflakeIdGenerator.extractNodeId(generator.nextId()))
                .isEqualTo(SnowflakeIdGenerator.MAX_NODE_ID);
        }

        @Test
        @DisplayName("negative node id resolves from the system property")
        void resolvesFromSystemProperty() {
            System.setProperty(SnowflakeIdGenerator.NODE_ID_PROPERTY, "77");

            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(-1);

            assertThat(generator.getNodeId()).isEqualTo(77);
        }

        @Test
        @DisplayName("invalid system property falls back to a valid node id")
        void invalidPropertyFallsBack() {
            System.setProperty(SnowflakeIdGenerator.NODE_ID_PROPERTY, "not-a-number");

            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(-1);

            assertThat(generator.getNodeId()).isBetween(0L, SnowflakeIdGenerator.MAX_NODE_ID);
        }

        @Test
        @DisplayName("out-of-range system property falls back to a valid node id")
        void outOfRangePropertyFallsBack() {
            System.setProperty(SnowflakeIdGenerator.NODE_ID_PROPERTY, "99999");

            SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

            assertThat(generator.getNodeId()).isBetween(0L, SnowflakeIdGenerator.MAX_NODE_ID);
        }
    }
}
