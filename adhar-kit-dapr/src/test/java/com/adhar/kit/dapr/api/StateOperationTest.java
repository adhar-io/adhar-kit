package com.adhar.kit.dapr.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StateOperation} factory methods and accessors.
 */
class StateOperationTest {

    @Test
    void upsertWithoutEtagBuildsUpsertOperation() {
        StateOperation op = StateOperation.upsert("key1", "value1");

        assertThat(op.getOperation()).isEqualTo(StateOperation.OperationType.UPSERT);
        assertThat(op.getKey()).isEqualTo("key1");
        assertThat(op.getValue()).isEqualTo("value1");
        assertThat(op.getEtag()).isNull();
    }

    @Test
    void upsertWithEtagBuildsUpsertOperationWithEtag() {
        StateOperation op = StateOperation.upsert("key2", 42, "etag-123");

        assertThat(op.getOperation()).isEqualTo(StateOperation.OperationType.UPSERT);
        assertThat(op.getKey()).isEqualTo("key2");
        assertThat(op.getValue()).isEqualTo(42);
        assertThat(op.getEtag()).isEqualTo("etag-123");
    }

    @Test
    void deleteWithoutEtagBuildsDeleteOperation() {
        StateOperation op = StateOperation.delete("key3");

        assertThat(op.getOperation()).isEqualTo(StateOperation.OperationType.DELETE);
        assertThat(op.getKey()).isEqualTo("key3");
        assertThat(op.getValue()).isNull();
        assertThat(op.getEtag()).isNull();
    }

    @Test
    void deleteWithEtagBuildsDeleteOperationWithEtag() {
        StateOperation op = StateOperation.delete("key4", "etag-9");

        assertThat(op.getOperation()).isEqualTo(StateOperation.OperationType.DELETE);
        assertThat(op.getKey()).isEqualTo("key4");
        assertThat(op.getEtag()).isEqualTo("etag-9");
    }

    @Test
    void noArgsConstructorAndSettersWork() {
        StateOperation op = new StateOperation();
        op.setOperation(StateOperation.OperationType.UPSERT);
        op.setKey("k");
        op.setValue("v");
        op.setEtag("e");

        assertThat(op.getKey()).isEqualTo("k");
        assertThat(op.getValue()).isEqualTo("v");
        assertThat(op.getEtag()).isEqualTo("e");
    }

    @Test
    void allArgsConstructorAndBuilderProduceEqualObjects() {
        StateOperation built = StateOperation.builder()
            .operation(StateOperation.OperationType.DELETE)
            .key("k")
            .value("v")
            .etag("e")
            .build();
        StateOperation constructed =
            new StateOperation(StateOperation.OperationType.DELETE, "k", "v", "e");

        assertThat(built).isEqualTo(constructed);
        assertThat(built.hashCode()).isEqualTo(constructed.hashCode());
        assertThat(built.toString()).contains("DELETE");
    }

    @Test
    void operationTypeValueOfRoundTrips() {
        assertThat(StateOperation.OperationType.valueOf("UPSERT"))
            .isEqualTo(StateOperation.OperationType.UPSERT);
        assertThat(StateOperation.OperationType.values()).hasSize(2);
    }
}
