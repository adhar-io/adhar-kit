package com.adhar.kit.batch.io;

import com.adhar.kit.batch.support.RustFsStorage;
import io.awspring.cloud.autoconfigure.core.AwsAutoConfiguration;
import io.awspring.cloud.autoconfigure.core.CredentialsProviderAutoConfiguration;
import io.awspring.cloud.autoconfigure.core.RegionProviderAutoConfiguration;
import io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KIT-1.5a: the spike that gates the S3 builders. Spring Cloud AWS 4.2.2 (Boot 4 line) on the kit's
 * AWS SDK 2.46.14, through the starter's own auto-configuration, against the RustFS image the
 * platform pins, path-style. Each test is one property the builders rely on.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3ResourceSpikeTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AwsAutoConfiguration.class, CredentialsProviderAutoConfiguration.class,
                    RegionProviderAutoConfiguration.class, S3AutoConfiguration.class))
            .withPropertyValues(
                    "spring.cloud.aws.s3.endpoint=" + RustFsStorage.endpoint(),
                    "spring.cloud.aws.s3.path-style-access-enabled=true",
                    "spring.cloud.aws.region.static=" + RustFsStorage.REGION,
                    "spring.cloud.aws.credentials.access-key=" + RustFsStorage.ACCESS_KEY,
                    "spring.cloud.aws.credentials.secret-key=" + RustFsStorage.SECRET_KEY);

    @Test
    void anS3UrlResolvesToAWritableResourceThroughTheResourceLoader() {
        String bucket = RustFsStorage.createBucket("spike-url");
        context.run(ctx -> {
            Resource missing = ctx.getResource("s3://" + bucket + "/inbound/records.jsonl");
            assertThat(missing).isInstanceOf(S3Resource.class);
            assertThat(missing.exists()).isFalse();

            try (OutputStream out = ((WritableResource) missing).getOutputStream()) {
                out.write("{\"id\":1}\n{\"id\":2}\n".getBytes(StandardCharsets.UTF_8));
            }
            Resource written = ctx.getResource("s3://" + bucket + "/inbound/records.jsonl");
            assertThat(written.exists()).isTrue();
            assertThat(written.contentLength()).isEqualTo(18);
            assertThat(written.lastModified()).isPositive();
            try (InputStream in = written.getInputStream()) {
                assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":1}\n{\"id\":2}\n");
            }
        });
    }

    /**
     * Finding: {@code S3Template.listObjects} returns ONE page (1,000 keys) and drops the rest
     * silently, so a prefix read built on it would lose input past the thousandth object. The
     * builders list with the SDK paginator instead, which this test also proves complete.
     */
    @Test
    void aPrefixListsEveryObjectOnlyThroughThePaginator() {
        String bucket = RustFsStorage.createBucket("spike-list");
        context.run(ctx -> {
            S3Template s3 = ctx.getBean(S3Template.class);
            IntStream.range(0, 1_005).parallel().forEach(i -> s3.store(bucket, "day=2026-11-03/part-%05d.jsonl".formatted(i), "x"));
            s3.store(bucket, "day=2026-11-04/part-00000.jsonl", "y");

            assertThat(s3.listObjects(bucket, "day=2026-11-03/")).hasSize(1_000);

            List<String> paged = ctx.getBean(S3Client.class)
                    .listObjectsV2Paginator(request -> request.bucket(bucket).prefix("day=2026-11-03/"))
                    .contents().stream().map(S3Object::key).toList();
            assertThat(paged).hasSize(1_005).isSorted().allSatisfy(key -> assertThat(key).startsWith("day=2026-11-03/"));
        });
    }

    @Test
    void aLargeObjectRoundTripsByteForByte() {
        String bucket = RustFsStorage.createBucket("spike-large");
        byte[] payload = new byte[12 * 1024 * 1024];
        new Random(42).nextBytes(payload);
        context.run(ctx -> {
            WritableResource target = (WritableResource) ctx.getResource("s3://" + bucket + "/big/blob.bin");
            try (OutputStream out = target.getOutputStream()) {
                for (int offset = 0; offset < payload.length; offset += 64 * 1024) {
                    out.write(payload, offset, 64 * 1024);
                }
            }
            try (InputStream in = ctx.getResource("s3://" + bucket + "/big/blob.bin").getInputStream()) {
                assertThat(sha256(in.readAllBytes())).isEqualTo(sha256(payload));
            }
        });
    }

    @Test
    void aNonAsciiKeyIsAddressedExactly() {
        String bucket = RustFsStorage.createBucket("spike-utf8");
        context.run(ctx -> {
            S3Template s3 = ctx.getBean(S3Template.class);
            s3.store(bucket, "région/zürich 2026.csv", "a,b");
            assertThat(s3.listObjects(bucket, "région/")).extracting(r -> r.getLocation().getObject())
                    .containsExactly("région/zürich 2026.csv");
            assertThat(s3.read(bucket, "région/zürich 2026.csv", String.class)).isEqualTo("a,b");
        });
    }

    @Test
    void anOverwriteReplacesTheObject() {
        String bucket = RustFsStorage.createBucket("spike-overwrite");
        context.run(ctx -> {
            S3Template s3 = ctx.getBean(S3Template.class);
            s3.store(bucket, "out/part-00000.jsonl", "first");
            s3.store(bucket, "out/part-00000.jsonl", "second");
            assertThat(s3.read(bucket, "out/part-00000.jsonl", String.class)).isEqualTo("second");
            assertThat(s3.listObjects(bucket, "out/")).hasSize(1);
        });
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
