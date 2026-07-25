package com.adhar.kit.docs.versioning;

import com.adhar.kit.docs.annotation.ApiGroup;
import com.adhar.kit.docs.processor.ApiGroupProcessor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Surfaces {@link ApiGroup}-annotated controllers into SpringDoc {@link GroupedOpenApi}
 * definitions, so that each distinct {@code @ApiGroup} name produces its own grouped
 * OpenAPI document (its own {@code /v3/api-docs/{group}} endpoint and Swagger UI
 * selector entry) in addition to the tag-based grouping within the aggregated spec.
 *
 * <p>Classes sharing the same {@link ApiGroup#name()} are merged into a single group:
 * their packages are combined into {@code packagesToScan}, and an
 * {@link org.springdoc.core.customizers.OpenApiCustomizer} is attached to the group
 * that adds the corresponding {@link Tag} (built via {@link ApiGroupProcessor}) to the
 * grouped spec — preserving the existing tag-based behaviour.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Bean
 * public List<GroupedOpenApi> groupedOpenApis() {
 *     return GroupedApiCustomizer.buildGroupedOpenApis(List.of(
 *         OrderController.class,
 *         PaymentController.class
 *     ));
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class GroupedApiCustomizer {

    private GroupedApiCustomizer() {
        // utility class
    }

    /**
     * Builds one {@link GroupedOpenApi} per distinct {@link ApiGroup#name()} found among
     * the given classes. Classes without an {@code @ApiGroup} annotation are ignored.
     *
     * @param controllerClasses candidate controller/resource classes
     * @return a grouped OpenAPI definition per distinct group name, in first-seen order
     */
    public static List<GroupedOpenApi> buildGroupedOpenApis(Collection<Class<?>> controllerClasses) {
        Map<String, List<Class<?>>> byGroup = new LinkedHashMap<>();
        for (Class<?> clazz : controllerClasses) {
            ApiGroup annotation = clazz.getAnnotation(ApiGroup.class);
            if (annotation == null) {
                continue;
            }
            byGroup.computeIfAbsent(annotation.name(), name -> new ArrayList<>()).add(clazz);
        }

        List<GroupedOpenApi> result = new ArrayList<>();
        byGroup.forEach((groupName, classes) -> result.add(buildGroup(groupName, classes)));
        return result;
    }

    private static GroupedOpenApi buildGroup(String groupName, List<Class<?>> classes) {
        ApiGroup primary = classes.get(0).getAnnotation(ApiGroup.class);
        String displayName = primary.description().isEmpty() ? groupName : primary.description();

        String[] packages = classes.stream()
                .map(clazz -> clazz.getPackage().getName())
                .distinct()
                .toArray(String[]::new);

        List<Tag> tags = ApiGroupProcessor.processMultiple(classes);

        log.debug("Building GroupedOpenApi for group '{}' with {} package(s) and {} tag(s)",
                groupName, packages.length, tags.size());

        return GroupedOpenApi.builder()
                .group(groupName)
                .displayName(displayName)
                .packagesToScan(packages)
                .addOpenApiCustomizer(openApi -> applyGroupTags(openApi, tags))
                .build();
    }

    /**
     * Merges the given tags into the OpenAPI spec's tag list without duplicating
     * tags that are already present (matched by name).
     *
     * @param openApi the grouped OpenAPI spec
     * @param tags    the tags to merge in
     */
    static void applyGroupTags(OpenAPI openApi, List<Tag> tags) {
        if (tags.isEmpty()) {
            return;
        }
        List<Tag> merged = openApi.getTags() == null ? new ArrayList<>() : new ArrayList<>(openApi.getTags());
        for (Tag tag : tags) {
            boolean present = merged.stream().anyMatch(existing -> existing.getName().equals(tag.getName()));
            if (!present) {
                merged.add(tag);
            }
        }
        openApi.setTags(merged);
    }
}
