package com.adhar.kit.batch.support;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One S3 bucket in memory, for the tests that must run where there is no Docker: the four calls the
 * kit's readers, writers and partitioner make ({@code ListObjectsV2} with prefix, delimiter and
 * continuation, {@code HeadObject}, {@code GetObject}, {@code PutObject}), with the semantics they
 * depend on: keys listed in order, at most {@link #pageSize(int)} entries (keys and common prefixes
 * together) per page, an ETag that changes with the content, and 404 as S3 reports it. The same
 * scenarios run against RustFS in the {@code *TestContainerTest} classes; this client stands in for
 * the wire, not for the store's behaviour beyond those calls.
 */
public final class InMemoryS3Client implements S3Client {

    private final String bucket;
    private final Map<String, byte[]> objects = new ConcurrentSkipListMap<>();
    private final Map<String, String> contentTypes = new ConcurrentSkipListMap<>();
    private final AtomicInteger listCalls = new AtomicInteger();
    private final AtomicInteger puts = new AtomicInteger();
    private volatile int pageSize = 1000;
    private volatile S3Exception headFailure;

    /**
     * @param bucket the one bucket that exists
     */
    public InMemoryS3Client(String bucket) {
        this.bucket = bucket;
    }

    /**
     * @param pageSize the most entries one listing page returns (S3's own limit is 1,000)
     * @return this client
     */
    public InMemoryS3Client pageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    /**
     * @param failure what every {@code HeadObject} answers from now on, {@code null} for the object
     */
    public void failHeadWith(S3Exception failure) {
        this.headFailure = failure;
    }

    /**
     * Stores an object, as a test's arrangement.
     */
    public void put(String key, String body) {
        objects.put(key, body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param key a key
     * @return the object's text, {@code null} when there is none
     */
    public String text(String key) {
        byte[] body = objects.get(key);
        return body != null ? new String(body, StandardCharsets.UTF_8) : null;
    }

    /**
     * @param key a key
     * @return the content type it was put with
     */
    public String contentType(String key) {
        return contentTypes.get(key);
    }

    /**
     * @return every key, in order
     */
    public List<String> keys() {
        return List.copyOf(objects.keySet());
    }

    /**
     * @return how many listing pages were asked for
     */
    public int listCalls() {
        return listCalls.get();
    }

    /**
     * @return how many objects were put through the API
     */
    public int puts() {
        return puts.get();
    }

    private void requireBucket(String requested) {
        if (!bucket.equals(requested)) {
            throw NoSuchBucketException.builder().statusCode(404).message("no bucket " + requested).build();
        }
    }

    @Override
    public ListObjectsV2Response listObjectsV2(ListObjectsV2Request request) {
        requireBucket(request.bucket());
        listCalls.incrementAndGet();
        String prefix = request.prefix() != null ? request.prefix() : "";
        String delimiter = request.delimiter();
        // Keys and common prefixes are one ordered sequence of entries, paged together.
        TreeSet<String> entries = new TreeSet<>();
        for (String key : objects.keySet()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            int cut = delimiter != null && !delimiter.isEmpty() ? key.indexOf(delimiter, prefix.length()) : -1;
            entries.add(cut >= 0 ? key.substring(0, cut + delimiter.length()) : key);
        }
        String after = request.continuationToken();
        int limit = request.maxKeys() != null ? Math.min(request.maxKeys(), pageSize) : pageSize;
        List<S3Object> contents = new ArrayList<>();
        List<CommonPrefix> prefixes = new ArrayList<>();
        String last = null;
        boolean truncated = false;
        for (String entry : after != null ? entries.tailSet(after, false) : entries) {
            if (contents.size() + prefixes.size() == limit) {
                truncated = true;
                break;
            }
            if (objects.containsKey(entry)) {
                contents.add(S3Object.builder().key(entry).eTag(eTag(objects.get(entry)))
                        .size((long) objects.get(entry).length).build());
            } else {
                prefixes.add(CommonPrefix.builder().prefix(entry).build());
            }
            last = entry;
        }
        return ListObjectsV2Response.builder().name(bucket).prefix(prefix).delimiter(delimiter)
                .contents(contents).commonPrefixes(prefixes).keyCount(contents.size() + prefixes.size())
                .isTruncated(truncated).nextContinuationToken(truncated ? last : null).build();
    }

    @Override
    public HeadObjectResponse headObject(HeadObjectRequest request) {
        requireBucket(request.bucket());
        if (headFailure != null) {
            throw headFailure;
        }
        byte[] body = find(request.key());
        return HeadObjectResponse.builder().eTag(eTag(body)).contentLength((long) body.length)
                .contentType(contentTypes.get(request.key())).build();
    }

    @Override
    public <ReturnT> ReturnT getObject(GetObjectRequest request,
                                       ResponseTransformer<GetObjectResponse, ReturnT> transformer) {
        requireBucket(request.bucket());
        byte[] body = find(request.key());
        GetObjectResponse response = GetObjectResponse.builder().eTag(eTag(body)).contentLength((long) body.length)
                .contentType(contentTypes.get(request.key())).build();
        try {
            return transformer.transform(response, AbortableInputStream.create(new ByteArrayInputStream(body)));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest request) {
        return getObject(request, ResponseTransformer.toBytes());
    }

    @Override
    public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
        requireBucket(request.bucket());
        byte[] content;
        try (InputStream stream = body.contentStreamProvider().newStream()) {
            content = stream.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        objects.put(request.key(), content);
        if (request.contentType() != null) {
            contentTypes.put(request.key(), request.contentType());
        }
        puts.incrementAndGet();
        return PutObjectResponse.builder().eTag(eTag(content)).build();
    }

    private byte[] find(String key) {
        byte[] body = objects.get(key);
        if (body == null) {
            throw NoSuchKeyException.builder().statusCode(404).message("no object " + key).build();
        }
        return body;
    }

    private static String eTag(byte[] body) {
        try {
            return '"' + HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(body)) + '"';
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public String serviceName() {
        return SERVICE_NAME;
    }

    @Override
    public void close() {
        // nothing to release
    }
}
