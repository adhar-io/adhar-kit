package com.adhar.kit.batch.partitioner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Partitioner} implementation that creates partitions based on start/end
 * ranges for parallel batch processing.
 *
 * <p>This partitioner divides a numeric range into equal-sized partitions, enabling
 * parallel processing of large datasets. Each partition receives a start and end
 * value in its {@link ExecutionContext}.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * var partitioner = new AdharRangePartitioner(1, 10000);
 * Map<String, ExecutionContext> partitions = partitioner.partition(4);
 * // Creates 4 partitions: [1-2500], [2501-5000], [5001-7500], [7501-10000]
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AdharRangePartitioner implements Partitioner {

    private static final String PARTITION_KEY_PREFIX = "partition";
    private static final String START_KEY = "start";
    private static final String END_KEY = "end";

    private final long rangeStart;
    private final long rangeEnd;

    /**
     * Creates a new range partitioner with the specified bounds.
     *
     * @param rangeStart the inclusive start of the range
     * @param rangeEnd   the inclusive end of the range
     * @throws IllegalArgumentException if rangeStart is greater than rangeEnd
     */
    public AdharRangePartitioner(long rangeStart, long rangeEnd) {
        if (rangeStart > rangeEnd) {
            throw new IllegalArgumentException(
                    "rangeStart (%d) must not be greater than rangeEnd (%d)".formatted(rangeStart, rangeEnd));
        }
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
    }

    /**
     * Creates partitions by dividing the range into {@code gridSize} equal segments.
     *
     * <p>Each partition's {@link ExecutionContext} contains:</p>
     * <ul>
     *   <li>{@code start} - the inclusive start value for the partition</li>
     *   <li>{@code end} - the inclusive end value for the partition</li>
     * </ul>
     *
     * @param gridSize the number of partitions to create
     * @return a map of partition names to execution contexts
     */
    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        var partitions = new HashMap<String, ExecutionContext>();
        long totalRange = rangeEnd - rangeStart + 1;
        long partitionSize = Math.max(1, totalRange / gridSize);

        long currentStart = rangeStart;

        for (int i = 0; i < gridSize && currentStart <= rangeEnd; i++) {
            var context = new ExecutionContext();
            long currentEnd = (i == gridSize - 1) ? rangeEnd : Math.min(currentStart + partitionSize - 1, rangeEnd);

            context.putLong(START_KEY, currentStart);
            context.putLong(END_KEY, currentEnd);

            var partitionName = PARTITION_KEY_PREFIX + i;
            partitions.put(partitionName, context);

            log.debug("Created {}: start={}, end={}, size={}", partitionName, currentStart, currentEnd, currentEnd - currentStart + 1);

            currentStart = currentEnd + 1;
        }

        log.info("Created {} partitions for range [{}, {}]", partitions.size(), rangeStart, rangeEnd);
        return partitions;
    }
}
