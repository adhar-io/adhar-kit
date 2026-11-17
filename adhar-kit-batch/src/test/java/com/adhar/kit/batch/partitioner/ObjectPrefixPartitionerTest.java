package com.adhar.kit.batch.partitioner;

import com.adhar.kit.batch.support.InMemoryS3Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ObjectPrefixPartitioner}'s partition names, and its partitions over
 * {@link InMemoryS3Client}; its listing against RustFS is
 * {@link ObjectPrefixPartitionerTestContainerTest}.
 */
class ObjectPrefixPartitionerTest {

    private static final String BUCKET = "inbound";
    private static final String DAY = "day=2026-11-03/";

    private InMemoryS3Client day() {
        InMemoryS3Client s3 = new InMemoryS3Client(BUCKET).pageSize(2);
        s3.put(DAY + "region=us/part-00000.jsonl", "{}");
        s3.put(DAY + "region=eu/part-00000.jsonl", "{}");
        s3.put(DAY + "region=eu/part-00001.jsonl", "{}");
        s3.put(DAY + "region=eu/_SUCCESS", "");
        s3.put("day=2026-11-04/region=ap/part-00000.jsonl", "{}");
        return s3;
    }

    @Test
    @DisplayName("one partition per child prefix, in key order, whatever the grid size")
    void childPrefixes() {
        var partitions = ObjectPrefixPartitioner.of(day(), BUCKET, DAY).partition(16);

        assertThat(partitions.keySet()).containsExactly("region=eu", "region=us");
        assertThat(partitions.get("region=eu").getString(ObjectPrefixPartitioner.PREFIX)).isEqualTo(DAY + "region=eu/");
        assertThat(partitions.get("region=us").containsKey(ObjectPrefixPartitioner.KEY)).isFalse();
        assertThat(ObjectPrefixPartitioner.of(day(), BUCKET, "day=2026-12-01/").partition(1)).isEmpty();
    }

    @Test
    @DisplayName("perObject: one partition per object at any depth, filtered by suffix, named after its relative key")
    void perObject() {
        var partitions = ObjectPrefixPartitioner.of(day(), BUCKET, DAY).perObject().suffix(".jsonl").partition(1);

        assertThat(partitions).hasSize(3);
        assertThat(partitions.values()).extracting(context -> context.getString(ObjectPrefixPartitioner.KEY))
                .containsExactly(DAY + "region=eu/part-00000.jsonl", DAY + "region=eu/part-00001.jsonl",
                        DAY + "region=us/part-00000.jsonl");
        assertThat(partitions.keySet()).allMatch(name -> name.matches("region=(eu|us)_part-0000[01]\\.jsonl~[0-9a-f]{8}"));
        assertThat(ObjectPrefixPartitioner.of(day(), BUCKET, DAY).perObject().partition(1))
                .as("without a suffix the marker is an object like any other").hasSize(4);
    }

    @Test
    @DisplayName("a prefix added before a restart is a new partition; the others keep their names")
    void namesSurviveANewPrefix() {
        InMemoryS3Client s3 = day();
        var partitioner = ObjectPrefixPartitioner.of(s3, BUCKET, DAY);
        var before = partitioner.partition(4);

        s3.put(DAY + "region=ap/part-00000.jsonl", "{}");
        var after = partitioner.partition(4);

        assertThat(after.keySet()).containsExactly("region=ap", "region=eu", "region=us");
        assertThat(after.get("region=eu").getString(ObjectPrefixPartitioner.PREFIX))
                .isEqualTo(before.get("region=eu").getString(ObjectPrefixPartitioner.PREFIX));
    }

    @Test
    @DisplayName("a plain prefix names its partition as it is")
    void plainNames() {
        assertThat(ObjectPrefixPartitioner.nameOf("region=eu/")).isEqualTo("region=eu");
        assertThat(ObjectPrefixPartitioner.nameOf("part-00001.jsonl")).isEqualTo("part-00001.jsonl");
    }

    @Test
    @DisplayName("an altered or long name carries a hash, so two inputs never share a name")
    void alteredNamesAreDistinct() {
        String spaced = ObjectPrefixPartitioner.nameOf("a b/");
        String underscored = ObjectPrefixPartitioner.nameOf("a_b/");
        String nested = ObjectPrefixPartitioner.nameOf("a/b.jsonl");
        String flat = ObjectPrefixPartitioner.nameOf("a_b.jsonl");

        assertThat(spaced).startsWith("a_b~").hasSize(12);
        assertThat(underscored).isEqualTo("a_b");
        assertThat(nested).startsWith("a_b.jsonl~");
        assertThat(flat).isEqualTo("a_b.jsonl");
        String longName = ObjectPrefixPartitioner.nameOf("x".repeat(300) + "/");
        assertThat(longName).hasSize(48).matches("x{39}~[0-9a-f]{8}");
        assertThat(ObjectPrefixPartitioner.nameOf("é/")).matches("_?~[0-9a-f]{8}");
    }
}
