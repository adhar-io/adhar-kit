package com.adhar.kit.batch.partitioner;

import com.adhar.kit.batch.support.RustFsStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ObjectPrefixPartitioner} against RustFS.
 */
@Testcontainers(disabledWithoutDocker = true)
class ObjectPrefixPartitionerTestContainerTest {

    private S3Client s3;
    private String bucket;

    @BeforeEach
    void setUp() {
        s3 = RustFsStorage.client();
        bucket = RustFsStorage.createBucket("prefixes");
        for (String key : new String[] {"day=2026-11-03/region=us/a.jsonl", "day=2026-11-03/region=eu/a.jsonl",
                "day=2026-11-03/region=eu/b.jsonl", "day=2026-11-03/_SUCCESS", "day=2026-11-04/region=eu/a.jsonl"}) {
            s3.putObject(request -> request.bucket(bucket).key(key), RequestBody.fromString("{}"));
        }
    }

    @AfterEach
    void tearDown() {
        s3.close();
    }

    private static Map<String, String> byName(Map<String, ExecutionContext> partitions, String key) {
        Map<String, String> names = new java.util.LinkedHashMap<>();
        partitions.forEach((name, context) -> names.put(name, context.getString(key)));
        return names;
    }

    @Test
    @DisplayName("one partition per child prefix, in key order, ignoring the grid size")
    void childPrefixes() {
        var partitions = ObjectPrefixPartitioner.of(s3, bucket, "day=2026-11-03/").partition(16);

        assertThat(byName(partitions, ObjectPrefixPartitioner.PREFIX)).containsExactly(
                Map.entry("region=eu", "day=2026-11-03/region=eu/"),
                Map.entry("region=us", "day=2026-11-03/region=us/"));
    }

    @Test
    @DisplayName("perObject: one partition per object, filtered by suffix")
    void perObject() {
        var partitions = ObjectPrefixPartitioner.of(s3, bucket, "day=2026-11-03/").perObject().suffix(".jsonl")
                .partition(1);

        assertThat(byName(partitions, ObjectPrefixPartitioner.KEY).values()).containsExactly(
                "day=2026-11-03/region=eu/a.jsonl", "day=2026-11-03/region=eu/b.jsonl", "day=2026-11-03/region=us/a.jsonl");
    }

    @Test
    @DisplayName("a prefix added before a restart is a new partition; the others keep their names")
    void namesSurviveANewPrefix() {
        var partitioner = ObjectPrefixPartitioner.of(s3, bucket, "day=2026-11-03/");
        Map<String, String> before = byName(partitioner.partition(1), ObjectPrefixPartitioner.PREFIX);

        s3.putObject(request -> request.bucket(bucket).key("day=2026-11-03/region=ap/a.jsonl"), RequestBody.fromString("{}"));
        Map<String, String> after = byName(partitioner.partition(1), ObjectPrefixPartitioner.PREFIX);

        assertThat(after).containsAllEntriesOf(before).containsEntry("region=ap", "day=2026-11-03/region=ap/").hasSize(3);
    }
}
