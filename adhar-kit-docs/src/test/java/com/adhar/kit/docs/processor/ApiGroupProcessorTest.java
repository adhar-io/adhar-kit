package com.adhar.kit.docs.processor;

import com.adhar.kit.docs.annotation.ApiGroup;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiGroupProcessorTest {

    @ApiGroup(name = "Orders", description = "Order operations", priority = 5)
    static class OrdersController {
    }

    @ApiGroup(name = "Items")
    static class ItemsController {
    }

    @ApiGroup(name = "Users", description = "User operations", priority = 10)
    static class UsersController {
    }

    static class NotAnnotated {
    }

    @Test
    void processAnnotationWithDescriptionAndPriority() {
        ApiGroup annotation = OrdersController.class.getAnnotation(ApiGroup.class);
        Tag tag = ApiGroupProcessor.processAnnotation(annotation, OrdersController.class);

        assertThat(tag.getName()).isEqualTo("Orders");
        assertThat(tag.getDescription()).isEqualTo("Order operations");
        assertThat(tag.getExtensions()).containsEntry("x-priority", 5);
    }

    @Test
    void processAnnotationWithDefaultsOmitsDescriptionAndPriority() {
        ApiGroup annotation = ItemsController.class.getAnnotation(ApiGroup.class);
        Tag tag = ApiGroupProcessor.processAnnotation(annotation, ItemsController.class);

        assertThat(tag.getName()).isEqualTo("Items");
        assertThat(tag.getDescription()).isNull();
        assertThat(tag.getExtensions()).isNull();
    }

    @Test
    void processMultipleSortsByPriorityDescendingAndSkipsUnannotated() {
        List<Class<?>> classes = List.of(
                OrdersController.class,   // priority 5
                UsersController.class,    // priority 10
                NotAnnotated.class);      // skipped

        List<Tag> tags = ApiGroupProcessor.processMultiple(classes);

        assertThat(tags).hasSize(2);
        // Higher priority first.
        assertThat(tags.get(0).getName()).isEqualTo("Users");
        assertThat(tags.get(1).getName()).isEqualTo("Orders");
    }

    @Test
    void processMultipleEmptyForNoAnnotatedClasses() {
        List<Tag> tags = ApiGroupProcessor.processMultiple(List.of(NotAnnotated.class));
        assertThat(tags).isEmpty();
    }
}
