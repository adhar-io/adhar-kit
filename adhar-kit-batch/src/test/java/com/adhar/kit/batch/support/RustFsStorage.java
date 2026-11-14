package com.adhar.kit.batch.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One RustFS container for the whole test JVM, at the image the platform pins
 * ({@code data/rustfs/manifests/install.yaml}), with a fresh bucket per test. Clients are
 * path-style, which is how every platform workload addresses RustFS: its Service name is not a
 * wildcard DNS zone, so virtual-hosted {@code <bucket>.rustfs…} names would not resolve.
 */
public final class RustFsStorage {

    public static final String IMAGE = "docker.io/rustfs/rustfs:1.0.1";
    public static final String ACCESS_KEY = "rustfsadmin";
    public static final String SECRET_KEY = "rustfs-test-secret";
    public static final String REGION = "us-east-1";

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static GenericContainer<?> container;

    private RustFsStorage() {
    }

    @SuppressWarnings("resource")
    private static synchronized GenericContainer<?> container() {
        if (container == null) {
            GenericContainer<?> started = new GenericContainer<>(IMAGE)
                    .withEnv("RUSTFS_VOLUMES", "/data")
                    .withEnv("RUSTFS_ADDRESS", ":9000")
                    .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
                    .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
            started.start();
            container = started;
        }
        return container;
    }

    /**
     * @return the S3 endpoint, e.g. {@code http://localhost:32768}
     */
    public static URI endpoint() {
        GenericContainer<?> rustfs = container();
        return URI.create("http://" + rustfs.getHost() + ":" + rustfs.getMappedPort(9000));
    }

    /**
     * @return a path-style client on the container
     */
    public static S3Client client() {
        return S3Client.builder()
                .endpointOverride(endpoint())
                .region(Region.of(REGION))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .forcePathStyle(true)
                .build();
    }

    /**
     * @param name a readable prefix
     * @return the name of a new, empty bucket
     */
    public static String createBucket(String name) {
        String bucket = (name + "-" + SEQUENCE.incrementAndGet()).toLowerCase();
        try (S3Client client = client()) {
            client.createBucket(request -> request.bucket(bucket));
        }
        return bucket;
    }
}
