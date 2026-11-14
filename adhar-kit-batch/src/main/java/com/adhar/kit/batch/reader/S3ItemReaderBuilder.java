package com.adhar.kit.batch.reader;

import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.ResourceAwareItemReaderItemStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Readers of objects in an S3 bucket — RustFS on the platform, through the Spring Cloud AWS
 * {@code S3Client} configured path-style ({@code spring.cloud.aws.s3.path-style-access-enabled=true}).
 * One object by key, or every object under a prefix in key order, through one flat-file delegate
 * (JSON Lines by default).
 *
 * <pre>{@code
 * S3ObjectsItemReader<InputRecord> reader = S3ItemReaderBuilder.<InputRecord>of(s3Client, "inbound")
 *         .prefix("day=" + businessDate + "/")
 *         .suffix(".jsonl")
 *         .jsonLines(InputRecord.class)
 *         .build();
 * }</pre>
 *
 * <p>A prefix is listed with the SDK paginator: {@code S3Template.listObjects} returns only the first
 * 1,000 keys (KIT-1.5a). An empty selection fails the step unless {@link #strict(boolean)} is
 * false, because a missing input is far more often a wrong path than an empty day. See
 * {@link S3ObjectsItemReader} for the restart guard.</p>
 *
 * @param <T> the item type
 * @since 0.1.0
 */
public final class S3ItemReaderBuilder<T> {

    /**
     * An object and its ETag (which changes when the object is rewritten).
     *
     * @param key  the key
     * @param eTag the ETag
     */
    public record ObjectRef(String key, String eTag) {
    }

    private final S3Client client;
    private final String bucket;
    private String name = "s3Reader";
    private String key;
    private String prefix;
    private String suffix = "";
    private boolean strict = true;
    private ResourceAwareItemReaderItemStream<? extends T> delegate;
    private LineMapper<T> lineMapper;
    private int linesToSkip;

    private S3ItemReaderBuilder(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    /**
     * @param client the S3 client (the Spring Cloud AWS bean)
     * @param bucket the bucket
     * @param <T>    the item type
     * @return the builder
     */
    public static <T> S3ItemReaderBuilder<T> of(S3Client client, String bucket) {
        return new S3ItemReaderBuilder<>(client, bucket);
    }

    /**
     * Lists every object under a prefix, in key order, page by page.
     *
     * @param client the S3 client
     * @param bucket the bucket
     * @param prefix the prefix ({@code ""} for the whole bucket)
     * @return the objects, sorted by key
     */
    public static List<ObjectRef> list(S3Client client, String bucket, String prefix) {
        return client.listObjectsV2Paginator(request -> request.bucket(bucket).prefix(prefix)).contents().stream()
                .map(object -> new ObjectRef(object.key(), object.eTag()))
                .sorted(Comparator.comparing(ObjectRef::key))
                .toList();
    }

    /**
     * Lists the "directories" directly under a prefix (common prefixes with delimiter {@code /}).
     *
     * @param client the S3 client
     * @param bucket the bucket
     * @param prefix the parent prefix, ending in {@code /} or empty
     * @return the child prefixes, each ending in {@code /}, sorted
     */
    public static List<String> childPrefixes(S3Client client, String bucket, String prefix) {
        return client.listObjectsV2Paginator(request -> request.bucket(bucket).prefix(prefix).delimiter("/"))
                .commonPrefixes().stream()
                .map(software.amazon.awssdk.services.s3.model.CommonPrefix::prefix)
                .sorted()
                .toList();
    }

    /**
     * @param name the reader's name, which prefixes its keys in the execution context
     * @return this builder
     */
    public S3ItemReaderBuilder<T> name(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param key one object to read
     * @return this builder
     */
    public S3ItemReaderBuilder<T> key(String key) {
        this.key = key;
        return this;
    }

    /**
     * @param prefix read every object under it
     * @return this builder
     */
    public S3ItemReaderBuilder<T> prefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    /**
     * @param suffix only keys ending with it (e.g. {@code .jsonl}); markers like {@code _SUCCESS} are skipped
     * @return this builder
     */
    public S3ItemReaderBuilder<T> suffix(String suffix) {
        this.suffix = suffix;
        return this;
    }

    /**
     * @param strict whether an empty selection fails the step (default true)
     * @return this builder
     */
    public S3ItemReaderBuilder<T> strict(boolean strict) {
        this.strict = strict;
        return this;
    }

    /**
     * @param type each line of each object is one JSON value of this type
     * @return this builder
     */
    public S3ItemReaderBuilder<T> jsonLines(Class<T> type) {
        return lines(JsonLinesItemReaderBuilder.lineMapper(type), 0);
    }

    /**
     * @param lineMapper  maps one line (e.g. a delimited mapper for CSV)
     * @param linesToSkip header lines at the top of EACH object
     * @return this builder
     */
    public S3ItemReaderBuilder<T> lines(LineMapper<T> lineMapper, int linesToSkip) {
        this.lineMapper = lineMapper;
        this.linesToSkip = linesToSkip;
        this.delegate = null;
        return this;
    }

    /**
     * @param delegate reads one object at a time
     * @return this builder
     */
    public S3ItemReaderBuilder<T> delegate(ResourceAwareItemReaderItemStream<? extends T> delegate) {
        this.delegate = delegate;
        this.lineMapper = null;
        return this;
    }

    /**
     * @return the reader
     */
    public S3ObjectsItemReader<T> build() {
        if ((key == null) == (prefix == null)) {
            throw new IllegalStateException(name + ": exactly one of key(...) and prefix(...) is required");
        }
        ResourceAwareItemReaderItemStream<? extends T> objects = delegate;
        if (objects == null && lineMapper != null) {
            FlatFileItemReader<T> lines = new FlatFileItemReader<>(lineMapper);
            lines.setName(name + ".object");
            lines.setEncoding(StandardCharsets.UTF_8.name());
            lines.setLinesToSkip(linesToSkip);
            objects = lines;
        }
        if (objects == null) {
            throw new IllegalStateException(name + ": jsonLines(...), lines(...) or delegate(...) is required");
        }
        Supplier<List<ObjectRef>> listing = key != null ? this::head : () -> list(client, bucket, prefix).stream()
                .filter(object -> object.key().endsWith(suffix) && !object.key().endsWith("/"))
                .toList();
        S3ObjectsItemReader<T> reader = new S3ObjectsItemReader<>(objects, client, bucket, listing);
        reader.setName(name);
        reader.setStrict(strict);
        return reader;
    }

    private List<ObjectRef> head() {
        try {
            return List.of(new ObjectRef(key, client.headObject(request -> request.bucket(bucket).key(key)).eTag()));
        } catch (NoSuchKeyException ex) {
            return List.of();
        } catch (software.amazon.awssdk.services.s3.model.S3Exception ex) {
            if (ex.statusCode() == 404) {
                return List.of();
            }
            throw ex;
        }
    }
}
