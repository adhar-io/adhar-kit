package com.adhar.kit.dapr.pubsub;

import com.adhar.kit.dapr.annotation.DaprSubscribe;
import com.adhar.kit.dapr.annotation.DaprTopic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Discovers Spring beans with methods annotated {@link DaprSubscribe} or {@link DaprTopic}
 * and registers them as pub/sub handlers, closing the gap where subscriptions were
 * publish-only (no subscription endpoint registration existed).
 *
 * <p>Runs as a {@link SmartInitializingSingleton} so that every singleton bean has already
 * been fully initialized by the time handler discovery happens (mirrors the pattern used by
 * {@code GrpcServiceRegistrar} in adhar-kit-grpc).</p>
 *
 * <p>Each handler is assigned a synthesized dispatch route under {@code /dapr/subscribe/...},
 * regardless of any custom {@link DaprSubscribe#route()} value (which is folded into the
 * route slug for readability). This keeps {@link DaprSubscriptionController} a single,
 * predictable dispatch entry point.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DaprSubscriptionRegistrar implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final Map<String, DaprSubscriptionHandler> handlersByRoute = new LinkedHashMap<>();

    public DaprSubscriptionRegistrar(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Object> beans = applicationContext.getBeansOfType(Object.class);
        beans.forEach(this::scanBean);
        log.info("DaprSubscriptionRegistrar discovered {} pub/sub handler(s)", handlersByRoute.size());
    }

    private void scanBean(String beanName, Object bean) {
        Class<?> targetClass = ClassUtils.getUserClass(bean.getClass());
        ReflectionUtils.doWithMethods(targetClass, method -> handleMethod(beanName, bean, method),
                method -> !method.isSynthetic() && !method.isBridge());
    }

    private void handleMethod(String beanName, Object bean, Method method) {
        DaprSubscribe subscribe = AnnotatedElementUtils.findMergedAnnotation(method, DaprSubscribe.class);
        if (subscribe != null) {
            String routeOrTopic = subscribe.route().isBlank() ? subscribe.topic() : subscribe.route();
            register(beanName, bean, method, subscribe.pubsubName(), subscribe.topic(),
                    routeOrTopic, subscribe.deadLetterTopic(), Map.of());
        }

        DaprTopic topic = AnnotatedElementUtils.findMergedAnnotation(method, DaprTopic.class);
        if (topic != null) {
            register(beanName, bean, method, topic.pubsubName(), topic.name(),
                    topic.name(), topic.deadLetterTopic(), parseMetadata(topic.metadata()));
        }
    }

    private void register(String beanName, Object bean, Method method, String pubsubName, String topicName,
                           String routeOrTopic, String deadLetterTopic, Map<String, String> metadata) {
        String route = buildRoute(pubsubName, routeOrTopic);
        DaprSubscriptionHandler handler = new DaprSubscriptionHandler(
                route, pubsubName, topicName, emptyToNull(deadLetterTopic), metadata, beanName, bean, method);

        DaprSubscriptionHandler existing = handlersByRoute.putIfAbsent(route, handler);
        if (existing != null) {
            log.warn("Duplicate Dapr subscription route '{}' from bean '{}#{}'; keeping handler already "
                            + "registered by '{}#{}'",
                    route, beanName, method.getName(), existing.beanName(), existing.method().getName());
        } else {
            log.info("Registered Dapr pub/sub handler: pubsub={} topic={} route={} bean={}#{}",
                    pubsubName, topicName, route, beanName, method.getName());
        }
    }

    /**
     * Returns the subscription metadata for {@code GET /dapr/subscribe}.
     *
     * @return the discovered subscriptions, in registration order
     */
    public List<DaprSubscriptionEntry> getSubscriptions() {
        return handlersByRoute.values().stream()
                .map(h -> new DaprSubscriptionEntry(h.pubsubName(), h.topic(), h.route(), h.metadata(), h.deadLetterTopic()))
                .toList();
    }

    /**
     * Looks up the handler registered for a dispatch route.
     *
     * @param route the route (as computed by {@link #buildRoute(String, String)})
     * @return the handler, if any
     */
    public Optional<DaprSubscriptionHandler> findHandler(String route) {
        return Optional.ofNullable(handlersByRoute.get(route));
    }

    /**
     * Number of currently registered handlers.
     *
     * @return handler count
     */
    public int size() {
        return handlersByRoute.size();
    }

    static Map<String, String> parseMetadata(String[] entries) {
        if (entries == null || entries.length == 0) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String entry : entries) {
            int idx = entry.indexOf('=');
            if (idx > 0) {
                map.put(entry.substring(0, idx).trim(), entry.substring(idx + 1).trim());
            }
        }
        return Map.copyOf(map);
    }

    static String buildRoute(String pubsubName, String routeOrTopic) {
        return "/dapr/subscribe/" + slugify(pubsubName) + "/" + slugify(routeOrTopic);
    }

    static String slugify(String value) {
        String cleaned = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        cleaned = cleaned.replaceAll("^-+|-+$", "");
        return cleaned.isEmpty() ? "route" : cleaned;
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
