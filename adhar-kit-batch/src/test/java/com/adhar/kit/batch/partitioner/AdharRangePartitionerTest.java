package com.adhar.kit.batch.partitioner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AdharRangePartitioner}.
 */
class AdharRangePartitionerTest {

    // ----------------------------------------------------------------
    // partition with gridSize = 3
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("partition() with gridSize = 3")
    class PartitionGridSize3 {

        @Test
        @DisplayName("should divide range into 3 partitions")
        void dividesRangeInto3() {
            var partitioner = new AdharRangePartitioner(1, 9);

            Map<String, ExecutionContext> partitions = partitioner.partition(3);

            assertThat(partitions).hasSize(3);
        }

        @Test
        @DisplayName("each partition should have start and end keys")
        void hasStartAndEndKeys() {
            var partitioner = new AdharRangePartitioner(1, 9);

            Map<String, ExecutionContext> partitions = partitioner.partition(3);

            partitions.values().forEach(ctx -> {
                assertThat(ctx.containsKey("start")).isTrue();
                assertThat(ctx.containsKey("end")).isTrue();
            });
        }

        @Test
        @DisplayName("partitions should cover the entire range without gaps")
        void coversEntireRange() {
            var partitioner = new AdharRangePartitioner(1, 9);

            Map<String, ExecutionContext> partitions = partitioner.partition(3);

            // The first partition should start at 1
            long minStart = partitions.values().stream()
                    .mapToLong(ctx -> ctx.getLong("start"))
                    .min()
                    .orElse(-1);
            assertThat(minStart).isEqualTo(1);

            // The last partition should end at 9
            long maxEnd = partitions.values().stream()
                    .mapToLong(ctx -> ctx.getLong("end"))
                    .max()
                    .orElse(-1);
            assertThat(maxEnd).isEqualTo(9);
        }

        @Test
        @DisplayName("partitions should not overlap")
        void noOverlap() {
            var partitioner = new AdharRangePartitioner(1, 12);

            Map<String, ExecutionContext> partitions = partitioner.partition(3);

            long[] starts = partitions.values().stream()
                    .mapToLong(ctx -> ctx.getLong("start"))
                    .sorted()
                    .toArray();
            long[] ends = partitions.values().stream()
                    .mapToLong(ctx -> ctx.getLong("end"))
                    .sorted()
                    .toArray();

            // Each partition's start should be one more than the previous partition's end
            for (int i = 1; i < starts.length; i++) {
                assertThat(starts[i]).isEqualTo(ends[i - 1] + 1);
            }
        }
    }

    // ----------------------------------------------------------------
    // partition with gridSize = 1
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("partition() with gridSize = 1")
    class PartitionGridSize1 {

        @Test
        @DisplayName("should return single partition covering full range")
        void singlePartition() {
            var partitioner = new AdharRangePartitioner(1, 100);

            Map<String, ExecutionContext> partitions = partitioner.partition(1);

            assertThat(partitions).hasSize(1);

            ExecutionContext ctx = partitions.values().iterator().next();
            assertThat(ctx.getLong("start")).isEqualTo(1);
            assertThat(ctx.getLong("end")).isEqualTo(100);
        }
    }

    // ----------------------------------------------------------------
    // Validation
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Constructor validation")
    class Validation {

        @Test
        @DisplayName("should throw when start > end")
        void throwsWhenStartGreaterThanEnd() {
            assertThatThrownBy(() -> new AdharRangePartitioner(100, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rangeStart")
                    .hasMessageContaining("rangeEnd");
        }

        @Test
        @DisplayName("should accept start == end")
        void acceptsStartEqualsEnd() {
            var partitioner = new AdharRangePartitioner(5, 5);

            Map<String, ExecutionContext> partitions = partitioner.partition(1);

            assertThat(partitions).hasSize(1);
            ExecutionContext ctx = partitions.values().iterator().next();
            assertThat(ctx.getLong("start")).isEqualTo(5);
            assertThat(ctx.getLong("end")).isEqualTo(5);
        }
    }

    // ----------------------------------------------------------------
    // Partition key naming
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Partition key naming")
    class PartitionNaming {

        @Test
        @DisplayName("partition keys should follow partition0, partition1, ... pattern")
        void keyNaming() {
            var partitioner = new AdharRangePartitioner(1, 30);

            Map<String, ExecutionContext> partitions = partitioner.partition(3);

            assertThat(partitions.keySet()).containsExactlyInAnyOrder(
                    "partition0", "partition1", "partition2");
        }
    }
}
