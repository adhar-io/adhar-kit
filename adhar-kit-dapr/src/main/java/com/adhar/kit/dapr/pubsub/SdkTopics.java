package com.adhar.kit.dapr.pubsub;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.Rule;
import io.dapr.Topic;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Reads the Dapr SDK's {@code @Topic} (with its {@code @Rule}) on controller methods the way the
 * SDK's own Spring integration does, so the kit keeps advertising them when it serves
 * {@code GET /dapr/subscribe} in that integration's place. The controller still receives the
 * events on its own {@code @PostMapping}. Only loaded when the SDK and Spring MVC are present.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
final class SdkTopics {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SdkTopics() {
    }

    static boolean isTopic(Method method) {
        return method.isAnnotationPresent(Topic.class);
    }

    static List<DaprSubscriptionEntry> entries(Class<?> controller, Method method, UnaryOperator<String> resolve) {
        Topic topic = method.getAnnotation(Topic.class);
        String name = resolve.apply(topic.name());
        String pubsub = resolve.apply(topic.pubsubName());
        if (name == null || name.isBlank() || pubsub == null || pubsub.isBlank()) {
            return List.of();
        }
        Map<String, String> metadata = metadata(topic.metadata());
        String deadLetter = resolve.apply(topic.deadLetterTopic());
        Rule rule = topic.rule();
        String match = resolve.apply(rule.match());
        List<DaprSubscriptionEntry> entries = new ArrayList<>();
        for (String route : routes(controller, method, name)) {
            if (match != null && !match.isBlank()) {
                entries.add(new DaprSubscriptionEntry(pubsub, name, null,
                        new DaprSubscriptionEntry.Routes(List.of(new DaprSubscriptionEntry.Rule(match, route)), null),
                        metadata, blankToNull(deadLetter), null));
            } else {
                entries.add(new DaprSubscriptionEntry(pubsub, name, route, null, metadata, blankToNull(deadLetter), null));
            }
        }
        return entries;
    }

    private static List<String> routes(Class<?> controller, Method method, String topicName) {
        String[] paths = {topicName};
        PostMapping post = AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class);
        if (post != null && post.path().length > 0) {
            paths = post.path();
        } else {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping != null && Arrays.asList(mapping.method()).contains(RequestMethod.POST)
                    && mapping.path().length > 0) {
                paths = mapping.path();
            }
        }
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        String[] prefixes = classMapping != null && classMapping.path().length > 0 ? classMapping.path() : new String[] {""};
        List<String> routes = new ArrayList<>();
        for (String prefix : prefixes) {
            for (String path : paths) {
                routes.add(join(prefix, path));
            }
        }
        return routes;
    }

    private static String join(String prefix, String path) {
        String p = path.startsWith("/") ? path : "/" + path;
        if (prefix == null || prefix.isEmpty()) {
            return p;
        }
        String base = prefix.startsWith("/") ? prefix : "/" + prefix;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) + p : base + p;
    }

    private static Map<String, String> metadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid @Topic metadata JSON: " + json, e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
