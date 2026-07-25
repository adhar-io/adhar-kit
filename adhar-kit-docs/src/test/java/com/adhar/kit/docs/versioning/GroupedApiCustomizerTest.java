package com.adhar.kit.docs.versioning;

import com.adhar.kit.docs.annotation.ApiGroup;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroupedApiCustomizerTest {

    @ApiGroup(name = "Orders", description = "Order management", priority = 1)
    static class OrderController {
    }

    @ApiGroup(name = "Orders", description = "Order management", priority = 1)
    static class OrderAdminController {
    }

    @ApiGroup(name = "Users")
    static class UserController {
    }

    static class NotAnnotated {
    }

    @Test
    void buildGroupedOpenApisCreatesOneGroupPerDistinctName() {
        List<GroupedOpenApi> groups = GroupedApiCustomizer.buildGroupedOpenApis(
                List.of(OrderController.class, OrderAdminController.class, UserController.class, NotAnnotated.class));

        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(GroupedOpenApi::getGroup).containsExactlyInAnyOrder("Orders", "Users");
    }

    @Test
    void ordersGroupMergesPackagesFromBothControllers() {
        List<GroupedOpenApi> groups = GroupedApiCustomizer.buildGroupedOpenApis(
                List.of(OrderController.class, OrderAdminController.class));

        GroupedOpenApi ordersGroup = groups.get(0);
        assertThat(ordersGroup.getGroup()).isEqualTo("Orders");
        assertThat(ordersGroup.getDisplayName()).isEqualTo("Order management");
        // Both nested classes share the same enclosing test package.
        assertThat(ordersGroup.getPackagesToScan()).containsExactly(OrderController.class.getPackage().getName());
    }

    @Test
    void groupWithoutDescriptionUsesGroupNameAsDisplayName() {
        List<GroupedOpenApi> groups = GroupedApiCustomizer.buildGroupedOpenApis(List.of(UserController.class));

        GroupedOpenApi usersGroup = groups.get(0);
        assertThat(usersGroup.getGroup()).isEqualTo("Users");
        assertThat(usersGroup.getDisplayName()).isEqualTo("Users");
    }

    @Test
    void groupCustomizerAppliesApiGroupTagToOpenApi() {
        List<GroupedOpenApi> groups = GroupedApiCustomizer.buildGroupedOpenApis(List.of(OrderController.class));
        GroupedOpenApi ordersGroup = groups.get(0);

        OpenAPI openApi = new OpenAPI();
        for (OpenApiCustomizer customizer : ordersGroup.getOpenApiCustomizers()) {
            customizer.customise(openApi);
        }

        assertThat(openApi.getTags()).extracting(Tag::getName).containsExactly("Orders");
        assertThat(openApi.getTags().get(0).getDescription()).isEqualTo("Order management");
    }

    @Test
    void groupCustomizerDoesNotDuplicateExistingTag() {
        List<GroupedOpenApi> groups = GroupedApiCustomizer.buildGroupedOpenApis(List.of(UserController.class));
        GroupedOpenApi usersGroup = groups.get(0);

        OpenAPI openApi = new OpenAPI();
        List<Tag> existingTags = new ArrayList<>();
        existingTags.add(new Tag().name("Users").description("pre-existing tag-based grouping"));
        openApi.setTags(existingTags);

        for (OpenApiCustomizer customizer : usersGroup.getOpenApiCustomizers()) {
            customizer.customise(openApi);
        }

        assertThat(openApi.getTags()).hasSize(1);
        assertThat(openApi.getTags().get(0).getDescription()).isEqualTo("pre-existing tag-based grouping");
    }

    @Test
    void buildGroupedOpenApisReturnsEmptyListWhenNoAnnotatedClasses() {
        List<GroupedOpenApi> groups = GroupedApiCustomizer.buildGroupedOpenApis(List.of(NotAnnotated.class));
        assertThat(groups).isEmpty();
    }

    @Test
    void applyGroupTagsIsNoOpForEmptyTagList() {
        OpenAPI openApi = new OpenAPI();
        GroupedApiCustomizer.applyGroupTags(openApi, List.of());
        assertThat(openApi.getTags()).isNull();
    }
}
