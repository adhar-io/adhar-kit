package com.adhar.kit.batch.writer;

import org.springframework.batch.infrastructure.item.file.transform.LineAggregator;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Writers of chunk-sized objects into an S3 bucket — RustFS on the platform, through the Spring
 * Cloud AWS {@code S3Client} configured path-style. Each chunk becomes one object
 * {@code <prefix>part-00000.jsonl}, {@code part-00001.jsonl}, … with idempotent keys (see
 * {@link S3PartItemWriter}); JSON Lines by default.
 *
 * <pre>{@code
 * S3PartItemWriter<Record> writer = S3ItemWriterBuilder.<Record>of(s3Client, "outbound")
 *         .prefix("records/day=" + businessDate + "/")
 *         .build();
 * }</pre>
 *
 * <p>The prefix should carry the run's identity (the business date): a new instance then never
 * overwrites another instance's output, and a restart of the same instance overwrites only its own.</p>
 *
 * @param <T> the item type
 * @since 0.1.0
 */
public final class S3ItemWriterBuilder<T> {

    private final S3Client client;
    private final String bucket;
    private String name = "s3Writer";
    private String prefix;
    private String partFormat = "part-%05d.jsonl";
    private String contentType = "application/jsonl";
    private LineAggregator<T> lineAggregator;

    private S3ItemWriterBuilder(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    /**
     * @param client the S3 client (the Spring Cloud AWS bean)
     * @param bucket the bucket
     * @param <T>    the item type
     * @return the builder
     */
    public static <T> S3ItemWriterBuilder<T> of(S3Client client, String bucket) {
        return new S3ItemWriterBuilder<>(client, bucket);
    }

    /**
     * @param name the writer's name, which prefixes its keys in the execution context
     * @return this builder
     */
    public S3ItemWriterBuilder<T> name(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param prefix the "directory" of the parts, normally ending in {@code /}
     * @return this builder
     */
    public S3ItemWriterBuilder<T> prefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    /**
     * @param partFormat the part name, a format with one integer (default {@code part-%05d.jsonl})
     * @return this builder
     */
    public S3ItemWriterBuilder<T> partFormat(String partFormat) {
        this.partFormat = partFormat;
        return this;
    }

    /**
     * @param lineAggregator formats one item as one line (e.g. a delimited aggregator for CSV)
     * @param contentType    the objects' content type
     * @return this builder
     */
    public S3ItemWriterBuilder<T> lines(LineAggregator<T> lineAggregator, String contentType) {
        this.lineAggregator = lineAggregator;
        this.contentType = contentType;
        return this;
    }

    /**
     * @return the writer
     */
    public S3PartItemWriter<T> build() {
        if (prefix == null) {
            throw new IllegalStateException(name + ": prefix(...) is required");
        }
        if (partFormat.formatted(0).equals(partFormat.formatted(1))) {
            throw new IllegalStateException(name + ": partFormat '" + partFormat + "' must number the parts");
        }
        S3PartItemWriter<T> writer = new S3PartItemWriter<>(client, bucket, prefix, partFormat, contentType,
                lineAggregator != null ? lineAggregator : JsonLinesItemWriterBuilder.lineAggregator());
        writer.setName(name);
        return writer;
    }
}
