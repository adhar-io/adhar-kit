package com.adhar.kit.docs.processor;

import com.adhar.kit.docs.annotation.ApiGroup;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Processor for @ApiGroup annotation.
 *
 * <p>Processes API group annotations and creates OpenAPI tags.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @RestController
 * @ApiGroup(name = "Orders", description = "Order management operations", priority = 1)
 * public class OrderController {
 *     // Endpoints automatically grouped
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ApiGroupProcessor {

    /**
     * Processes @ApiGroup annotation.
     *
     * @param annotation the annotation instance
     * @param targetClass the annotated class
     * @return OpenAPI Tag
     */
    public static Tag processAnnotation(ApiGroup annotation, Class<?> targetClass) {
        log.debug("Processing @ApiGroup on class: {}", targetClass.getName());

        Tag tag = new Tag();
        tag.setName(annotation.name());

        if (!annotation.description().isEmpty()) {
            tag.setDescription(annotation.description());
        }

        // Add extension for priority (used for ordering)
        if (annotation.priority() != 0) {
            tag.addExtension("x-priority", annotation.priority());
        }

        return tag;
    }

    /**
     * Processes multiple @ApiGroup annotations.
     *
     * @param classes classes to process
     * @return list of Tags
     */
    public static List<Tag> processMultiple(List<Class<?>> classes) {
        List<Tag> tags = new ArrayList<>();

        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(ApiGroup.class)) {
                ApiGroup annotation = clazz.getAnnotation(ApiGroup.class);
                tags.add(processAnnotation(annotation, clazz));
            }
        }

        // Sort by priority
        tags.sort((t1, t2) -> {
            Integer p1 = (Integer) t1.getExtensions().getOrDefault("x-priority", 0);
            Integer p2 = (Integer) t2.getExtensions().getOrDefault("x-priority", 0);
            return p2.compareTo(p1); // Higher priority first
        });

        return tags;
    }
}

