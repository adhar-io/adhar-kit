package com.adhar.kit.batch.partitioner;

import com.adhar.kit.batch.reader.S3ItemReaderBuilder;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One partition per "directory" directly under a prefix of an S3 bucket ({@code day=…/region=eu/},
 * {@code …/region=us/}), or per object with {@link #perObject()}. Like Spring Batch's
 * {@code MultiResourcePartitioner}, the grid size is not used: the input decides the partitions.
 * Each context carries {@value #PREFIX} (or {@value #KEY}) for the worker's
 * {@code S3ItemReaderBuilder}.
 *
 * <p>A partition is NAMED after its prefix, not its position: a restart lists again, and a prefix
 * that appeared since the failed attempt then becomes a new partition instead of taking the name,
 * and the recorded progress, of another. Names are the prefix made safe for a step name, shortened
 * with a hash when long, because Spring Batch's {@code STEP_NAME} column holds 100 characters.</p>
 *
 * @since 0.1.0
 */
public final class ObjectPrefixPartitioner implements Partitioner {

    /** The prefix a partition reads. */
    public static final String PREFIX = "prefix";
    /** The object a partition reads, with {@link #perObject()}. */
    public static final String KEY = "key";

    private static final int MAX_NAME = 48;

    private final S3Client client;
    private final String bucket;
    private final String parent;
    private boolean perObject;
    private String suffix = "";

    private ObjectPrefixPartitioner(S3Client client, String bucket, String parent) {
        this.client = client;
        this.bucket = bucket;
        this.parent = parent;
    }

    /**
     * @param client the S3 client
     * @param bucket the bucket
     * @param parent the prefix whose children are the partitions, ending in {@code /} or empty
     * @return the partitioner
     */
    public static ObjectPrefixPartitioner of(S3Client client, String bucket, String parent) {
        return new ObjectPrefixPartitioner(client, bucket, parent);
    }

    /**
     * Makes each object under the parent prefix (at any depth) its own partition.
     *
     * @return this partitioner
     */
    public ObjectPrefixPartitioner perObject() {
        this.perObject = true;
        return this;
    }

    /**
     * @param suffix with {@link #perObject()}: only keys ending with it
     * @return this partitioner
     */
    public ObjectPrefixPartitioner suffix(String suffix) {
        this.suffix = suffix;
        return this;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        List<String> inputs = perObject
                ? S3ItemReaderBuilder.list(client, bucket, parent).stream().map(S3ItemReaderBuilder.ObjectRef::key)
                        .filter(key -> key.endsWith(suffix) && !key.endsWith("/")).toList()
                : S3ItemReaderBuilder.childPrefixes(client, bucket, parent);
        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
        for (String input : inputs) {
            ExecutionContext context = new ExecutionContext();
            context.putString(perObject ? KEY : PREFIX, input);
            partitions.put(nameOf(input.substring(parent.length())), context);
        }
        return partitions;
    }

    /**
     * @param relative the input's key or prefix below the parent
     * @return a step-name-safe, stable name for it
     */
    static String nameOf(String relative) {
        String safe = relative.replaceAll("[^A-Za-z0-9=._-]", "_").replaceAll("_+$", "");
        if (safe.equals(relative.replaceAll("/$", "")) && safe.length() <= MAX_NAME && !safe.isEmpty()) {
            return safe;
        }
        String hash = sha256(relative).substring(0, 8);
        String head = safe.length() > MAX_NAME - 9 ? safe.substring(0, MAX_NAME - 9) : safe;
        return head + "~" + hash;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
