package com.adhar.kit.batch.fault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which steps a dlq-replay can send items through again, and which it must refuse.
 */
class DeadLetterReplayRegistryTest {

    private final DeadLetterReplayRegistry registry = new DeadLetterReplayRegistry();
    private final ItemProcessor<String, String> processor = item -> item;
    private final ItemWriter<String> writer = new ListItemWriter<>();

    @Test
    @DisplayName("a registered step is found by its name, and a partition's name finds its step")
    void found() {
        registry.register("importStep", processor, writer);

        assertThat(registry.find("importStep")).hasValueSatisfying(route -> {
            assertThat(route.processor()).isSameAs(processor);
            assertThat(route.writer()).isSameAs(writer);
            assertThat(route.problem()).isNull();
        });
        assertThat(registry.find("importStep:partition3")).map(DeadLetterReplayRegistry.Route::stepName)
                .contains("importStep");
        assertThat(registry.find("otherStep")).isEmpty();
        assertThat(registry.find("otherStep:partition0")).isEmpty();
    }

    @Test
    @DisplayName("a stream writer, an unknown writer and two steps of one name are refused with the reason")
    void refused() {
        registry.register("fileStep", processor, new FlatFileItemWriter<String>(String::valueOf));
        registry.register("customStep", processor, null);
        registry.register("twiceStep", processor, writer);
        registry.register("twiceStep", processor, writer);
        registry.register("sharedStep", processor, writer);
        registry.register("sharedStep", processor, new ListItemWriter<String>());

        assertThat(registry.find("fileStep").orElseThrow().problem()).contains("ItemStream");
        assertThat(registry.find("customStep").orElseThrow().problem()).contains("customize");
        assertThat(registry.find("twiceStep").orElseThrow().problem()).as("the same step built twice").isNull();
        assertThat(registry.find("sharedStep").orElseThrow().problem()).contains("more than one step");
        assertThat(registry.find("sharedStep").orElseThrow().writer()).isNull();
    }
}
