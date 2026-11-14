package com.adhar.kit.batch.reader;

import io.awspring.cloud.s3.InMemoryBufferingS3OutputStreamProvider;
import io.awspring.cloud.s3.PropertiesS3ObjectContentTypeResolver;
import io.awspring.cloud.s3.S3OutputStreamProvider;
import io.awspring.cloud.s3.S3Resource;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.file.MultiResourceItemReader;
import org.springframework.batch.infrastructure.item.file.ResourceAwareItemReaderItemStream;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

/**
 * Reads the objects {@link S3ItemReaderBuilder} selects, in key order, through one flat-file
 * delegate. Objects are listed when the step OPENS the reader, and the listing (keys and ETags) is
 * recorded in the execution context: a restart whose listing differs is refused, because the
 * position it would resume from (object index, line) belongs to input that no longer exists.
 *
 * @param <T> the item type
 * @since 0.1.0
 */
public class S3ObjectsItemReader<T> extends MultiResourceItemReader<T> {

    private static final String LISTING_KEY = "objects.sha256";

    private final S3Client client;
    private final String bucket;
    private final Supplier<List<S3ItemReaderBuilder.ObjectRef>> listing;
    private final S3OutputStreamProvider outputStreams;
    private boolean saveState = true;
    private String fingerprint;
    private List<String> keys = List.of();

    S3ObjectsItemReader(ResourceAwareItemReaderItemStream<? extends T> delegate, S3Client client, String bucket,
                        Supplier<List<S3ItemReaderBuilder.ObjectRef>> listing) {
        super(delegate);
        this.client = client;
        this.bucket = bucket;
        this.listing = listing;
        this.outputStreams = new InMemoryBufferingS3OutputStreamProvider(client, new PropertiesS3ObjectContentTypeResolver());
        setComparator(Comparator.comparing(resource -> ((S3Resource) resource).getLocation().getObject()));
    }

    @Override
    public void setSaveState(boolean saveState) {
        super.setSaveState(saveState);
        this.saveState = saveState;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        List<S3ItemReaderBuilder.ObjectRef> objects = listing.get();
        String current = fingerprint(objects);
        String recorded = executionContext.containsKey(getExecutionContextKey(LISTING_KEY))
                ? executionContext.getString(getExecutionContextKey(LISTING_KEY)) : null;
        if (saveState && recorded != null && !recorded.equals(current)) {
            throw new ItemStreamException("s3://" + bucket + ": the objects to read changed since the failed attempt"
                    + " (an object was added, removed or rewritten); a restart would resume inside different input."
                    + " Start a new run (new identity) for the new input.");
        }
        this.fingerprint = current;
        this.keys = objects.stream().map(S3ItemReaderBuilder.ObjectRef::key).toList();
        setResources(keys.stream()
                .map(key -> (Resource) new S3Resource(bucket, key, client, outputStreams))
                .toArray(Resource[]::new));
        super.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        super.update(executionContext);
        if (saveState && fingerprint != null) {
            executionContext.putString(getExecutionContextKey(LISTING_KEY), fingerprint);
        }
    }

    /**
     * @return the keys read, in order, as listed when the reader was opened
     */
    public List<String> keys() {
        return keys;
    }

    static String fingerprint(List<S3ItemReaderBuilder.ObjectRef> objects) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (S3ItemReaderBuilder.ObjectRef object : objects) {
                digest.update(object.key().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(String.valueOf(object.eTag()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
