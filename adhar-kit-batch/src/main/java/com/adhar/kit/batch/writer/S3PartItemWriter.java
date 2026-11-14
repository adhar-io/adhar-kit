package com.adhar.kit.batch.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamSupport;
import org.springframework.batch.infrastructure.item.ItemStreamWriter;
import org.springframework.batch.infrastructure.item.file.transform.LineAggregator;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.charset.StandardCharsets;

/**
 * Writes each chunk as ONE object, {@code <prefix><part>}, numbered from the execution context.
 *
 * <p>The number of a chunk is only consumed when its transaction commits: a write retried in the
 * same transaction reuses its number, a chunk that is rolled back gives it back, and the next write
 * of those items overwrites the same key. A restart resumes at the number the last committed chunk
 * recorded. Every key is therefore written by exactly one committed chunk, and an object a failed
 * attempt left behind is replaced, not duplicated. Readers that must never see a half-finished run
 * wait for the job to complete (a staged prefix with a {@code _SUCCESS} marker is Phase 3).</p>
 *
 * @param <T> the item type
 * @since 0.1.0
 */
public class S3PartItemWriter<T> extends ItemStreamSupport implements ItemStreamWriter<T> {

    private static final String NEXT_PART_KEY = "next.part";

    private final S3Client client;
    private final String bucket;
    private final String prefix;
    private final String partFormat;
    private final String contentType;
    private final LineAggregator<T> lineAggregator;
    private int nextPart;

    S3PartItemWriter(S3Client client, String bucket, String prefix, String partFormat, String contentType,
                     LineAggregator<T> lineAggregator) {
        this.client = client;
        this.bucket = bucket;
        this.prefix = prefix;
        this.partFormat = partFormat;
        this.contentType = contentType;
        this.lineAggregator = lineAggregator;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        String key = getExecutionContextKey(NEXT_PART_KEY);
        nextPart = executionContext.containsKey(key) ? executionContext.getInt(key) : 0;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt(getExecutionContextKey(NEXT_PART_KEY), nextPart);
    }

    @Override
    public void write(Chunk<? extends T> chunk) {
        if (chunk.isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder();
        for (T item : chunk) {
            body.append(lineAggregator.aggregate(item)).append('\n');
        }
        boolean inTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        Integer retried = inTransaction ? (Integer) TransactionSynchronizationManager.getResource(this) : null;
        // Spring Batch 6 retries a write in the SAME transaction with the same chunk: that write
        // takes the part its first attempt uploaded. A scan writes one item per transaction.
        int part = retried != null ? retried : nextPart;
        String key = keyOf(part);
        client.putObject(request -> request.bucket(bucket).key(key).contentType(contentType),
                RequestBody.fromBytes(body.toString().getBytes(StandardCharsets.UTF_8)));
        nextPart = part + 1;
        if (inTransaction && retried == null) {
            TransactionSynchronizationManager.bindResource(this, part);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(S3PartItemWriter.this);
                    if (status != STATUS_COMMITTED) {
                        nextPart = Math.min(nextPart, part);
                    }
                }
            });
        }
    }

    /**
     * @param part a part number
     * @return its object key
     */
    public String keyOf(int part) {
        return prefix + partFormat.formatted(part);
    }
}
