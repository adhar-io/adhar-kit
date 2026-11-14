package com.adhar.kit.batch.partitioner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Partition names of {@link ObjectPrefixPartitioner}; its listing against RustFS is
 * {@link ObjectPrefixPartitionerTestContainerTest}.
 */
class ObjectPrefixPartitionerTest {

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
