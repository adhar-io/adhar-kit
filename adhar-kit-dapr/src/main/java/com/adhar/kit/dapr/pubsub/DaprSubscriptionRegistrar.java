package com.adhar.kit.dapr.pubsub;

import com.adhar.kit.dapr.DaprComponents;
import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.DaprMode;
import com.adhar.kit.dapr.DaprSubscription;
import com.adhar.kit.dapr.annotation.DaprSubscribe;
import com.adhar.kit.dapr.annotation.DaprTopic;
import com.adhar.kit.dapr.http.DaprHttpClient;
import io.dapr.utils.TypeRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Collects every subscriber of the application and makes Dapr deliver to it.
 *
 * <p>At startup it finds the methods annotated {@link DaprSubscribe} or {@link DaprTopic} (and
 * the Dapr SDK's own {@code @Topic} controller methods, which it advertises but leaves to their
 * controller), and accepts handlers registered in code through
 * {@link DaprFacade#subscribe}. How they are delivered depends on where the app runs:</p>
 * <ul>
 *   <li>a Spring MVC app with a sidecar: advertised on {@code GET /dapr/subscribe} and served on
 *       their routes by {@code DaprAppCallbackHandlerMapping} &mdash; the programmatic
 *       subscription model every adhar-kit language uses;</li>
 *   <li>no web server: a streaming subscription per handler;</li>
 *   <li>memory mode: bound to the in-process pub/sub, so publishes of this process reach them.</li>
 * </ul>
 *
 * <p>Routes default to {@code /dapr/events/<pubsub>/<topic>}. Several handlers of one topic need
 * routing rules ({@code match}); the handler without a rule is the topic's default route.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DaprSubscriptionRegistrar implements SmartInitializingSingleton, ProgrammaticSubscriptions,
        DisposableBean {

    /** Prefix of the default delivery routes. */
    public static final String DEFAULT_ROUTE_PREFIX = "/dapr/events/";

    private static final boolean SDK_TOPIC_PRESENT =
            ClassUtils.isPresent("io.dapr.Topic", DaprSubscriptionRegistrar.class.getClassLoader())
                    && ClassUtils.isPresent("org.springframework.web.bind.annotation.PostMapping",
                    DaprSubscriptionRegistrar.class.getClassLoader());

    private final ApplicationContext applicationContext;
    private final DaprFacade dapr;
    private final DaprEventDispatcher dispatcher;
    private final Map<String, DaprSubscriptionHandler> handlersByRoute = new LinkedHashMap<>();
    private final List<DaprSubscriptionEntry> advertisedOnly = new CopyOnWriteArrayList<>();
    private final Map<String, DaprSubscription> localBindings = new LinkedHashMap<>();
    private final AtomicBoolean served = new AtomicBoolean();
    private volatile boolean httpDelivery;
    private volatile boolean started;

    /**
     * A registrar without a facade: discovers and serves handlers but never binds them locally.
     *
     * @param applicationContext the context to scan
     */
    public DaprSubscriptionRegistrar(ApplicationContext applicationContext) {
        this(applicationContext, null, new DaprEventDispatcher());
    }

    public DaprSubscriptionRegistrar(ApplicationContext applicationContext, DaprFacade dapr,
                                     DaprEventDispatcher dispatcher) {
        this.applicationContext = applicationContext;
        this.dapr = dapr;
        this.dispatcher = dispatcher != null ? dispatcher : new DaprEventDispatcher();
    }

    /**
     * Called by the HTTP callback endpoint when it exists: Dapr then delivers over HTTP and the
     * handlers are not bound to streaming subscriptions.
     */
    public void enableHttpDelivery() {
        this.httpDelivery = true;
    }

    /** {@code true} when deliveries arrive over the app's HTTP endpoint. */
    public boolean isHttpDelivery() {
        return httpDelivery;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (applicationContext != null) {
            for (String beanName : applicationContext.getBeanNamesForType(Object.class, false, false)) {
                scanBean(beanName);
            }
        }
        if (dapr != null) {
            dapr.setProgrammaticSubscriptions(this);
        }
        started = true;
        for (DaprSubscriptionHandler handler : getHandlers()) {
            bindLocallyIfNeeded(handler);
        }
        log.info("Dapr subscriptions: {} handler(s), {} SDK @Topic method(s), delivered {}", size(),
                advertisedOnly.size(), deliveryDescription());
    }

    private void scanBean(String beanName) {
        Class<?> beanType;
        try {
            beanType = applicationContext.getType(beanName, false);
        } catch (RuntimeException e) {
            return;
        }
        if (beanType == null || beanType == getClass()) {
            return;
        }
        Class<?> userClass = ClassUtils.getUserClass(beanType);
        List<Method> annotated = new ArrayList<>();
        List<Method> sdkTopics = new ArrayList<>();
        ReflectionUtils.doWithMethods(userClass, method -> {
            if (AnnotatedElementUtils.hasAnnotation(method, DaprSubscribe.class)
                    || AnnotatedElementUtils.hasAnnotation(method, DaprTopic.class)) {
                annotated.add(method);
            } else if (SDK_TOPIC_PRESENT && SdkTopics.isTopic(method)) {
                sdkTopics.add(method);
            }
        }, method -> !method.isSynthetic() && !method.isBridge());
        for (Method method : sdkTopics) {
            advertisedOnly.addAll(SdkTopics.entries(userClass, method, this::resolve));
        }
        if (annotated.isEmpty()) {
            return;
        }
        Object bean = applicationContext.getBean(beanName);
        for (Method method : annotated) {
            registerMethod(beanName, bean, method);
        }
    }

    private void registerMethod(String beanName, Object bean, Method method) {
        DaprSubscribe subscribe = AnnotatedElementUtils.findMergedAnnotation(method, DaprSubscribe.class);
        if (subscribe != null) {
            String pubsub = pubsubOrDefault(resolve(subscribe.pubsubName()));
            String topic = resolve(subscribe.topic());
            SubscribeOptions.BulkSubscribe bulk = subscribe.bulk()
                    ? new SubscribeOptions.BulkSubscribe(subscribe.maxMessagesCount(), subscribe.maxAwaitDurationMs())
                    : null;
            add(new DaprSubscriptionHandler(routeFor(pubsub, topic, resolve(subscribe.route())), pubsub, topic,
                    deadLetterOrDefault(resolve(subscribe.deadLetterTopic())), parseMetadata(subscribe.metadata()),
                    emptyToNull(resolve(subscribe.match())), subscribe.priority(), bulk, beanName, bean, method,
                    dispatcher.methodInvoker(beanName, bean, method)));
        }
        DaprTopic topicAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, DaprTopic.class);
        if (topicAnnotation != null) {
            String pubsub = pubsubOrDefault(resolve(topicAnnotation.pubsubName()));
            String topic = resolve(topicAnnotation.name());
            add(new DaprSubscriptionHandler(routeFor(pubsub, topic, null), pubsub, topic,
                    deadLetterOrDefault(resolve(topicAnnotation.deadLetterTopic())),
                    parseMetadata(topicAnnotation.metadata()), null, 0, null, beanName, bean, method,
                    dispatcher.methodInvoker(beanName, bean, method)));
        }
    }

    @Override
    public <T> DaprSubscription register(String topic, TypeRef<T> type, DaprTopicHandler<T> handler,
                                         SubscribeOptions options) {
        SubscribeOptions opts = options == null ? SubscribeOptions.none() : options;
        String pubsub = pubsubOrDefault(opts.pubsub());
        String description = "subscriber of " + pubsub + "/" + topic;
        DaprSubscriptionHandler registered = new DaprSubscriptionHandler(routeFor(pubsub, topic, opts.route()), pubsub,
                topic, deadLetterOrDefault(opts.deadLetterTopic()), opts.metadata(), null, 0, opts.bulk(),
                description, null, null, dispatcher.handlerInvoker(description, type, handler));
        if (!add(registered)) {
            throw new IllegalStateException("Topic " + pubsub + "/" + topic + " already has a default route; "
                    + "unsubscribe it first or give this handler a route");
        }
        if (served.get() && httpDelivery && !isMemory()) {
            log.warn("Subscription to {}/{} was registered after Dapr read GET /dapr/subscribe; the sidecar picks it "
                    + "up when it restarts. Register subscriptions while the application starts.", pubsub, topic);
        }
        if (started) {
            bindLocallyIfNeeded(registered);
        }
        AtomicBoolean active = new AtomicBoolean(true);
        return new DaprSubscription() {
            @Override
            public String id() {
                return registered.route();
            }

            @Override
            public void unsubscribe() {
                if (!active.getAndSet(false)) {
                    return;
                }
                DaprSubscription local;
                synchronized (handlersByRoute) {
                    handlersByRoute.remove(registered.route(), registered);
                    local = localBindings.remove(registered.route());
                }
                if (local != null) {
                    local.unsubscribe();
                }
            }

            @Override
            public boolean isActive() {
                return active.get();
            }
        };
    }

    private boolean add(DaprSubscriptionHandler handler) {
        synchronized (handlersByRoute) {
            DaprSubscriptionHandler existing = handlersByRoute.get(handler.route());
            if (existing != null) {
                log.warn("Duplicate Dapr subscription route '{}' from {}; keeping the handler registered by {}",
                        handler.route(), handler.beanName(), existing.beanName());
                return false;
            }
            for (DaprSubscriptionHandler other : handlersByRoute.values()) {
                if (other.pubsubName().equals(handler.pubsubName()) && other.topic().equals(handler.topic())
                        && other.match() == null && handler.match() == null) {
                    log.warn("Topic {}/{} already has a default route ({}); {} needs a routing rule (match) to share "
                            + "it and is ignored", handler.pubsubName(), handler.topic(), other.route(),
                            handler.beanName());
                    return false;
                }
            }
            handlersByRoute.put(handler.route(), handler);
        }
        log.info("Registered Dapr subscriber: pubsub={} topic={} route={} handler={}", handler.pubsubName(),
                handler.topic(), handler.route(), handler.beanName());
        return true;
    }

    private void bindLocallyIfNeeded(DaprSubscriptionHandler handler) {
        if (dapr == null || (httpDelivery && !isMemory())) {
            return;
        }
        if (handler.match() != null) {
            log.warn("Routing rule '{}' of {} is evaluated by the Dapr sidecar; without one only the topic's default "
                    + "handler receives events", handler.match(), handler.beanName());
            return;
        }
        try {
            DaprSubscription local = dapr.subscribeStreaming(handler.pubsubName(), handler.topic(),
                    handler.deadLetterTopic(), handler.metadata(), handler.invoker()::apply);
            synchronized (handlersByRoute) {
                localBindings.put(handler.route(), local);
            }
        } catch (RuntimeException e) {
            log.error("Could not subscribe {} to {}/{}: {}", handler.beanName(), handler.pubsubName(),
                    handler.topic(), e.getMessage());
        }
    }

    /**
     * The subscriptions for {@code GET /dapr/subscribe}: one entry per (pub/sub, topic), plus the
     * SDK {@code @Topic} methods.
     *
     * @return the subscriptions
     */
    public List<DaprSubscriptionEntry> getSubscriptions() {
        served.set(true);
        Map<String, List<DaprSubscriptionHandler>> byTopic = new LinkedHashMap<>();
        for (DaprSubscriptionHandler h : getHandlers()) {
            byTopic.computeIfAbsent(h.pubsubName() + "\u0000" + h.topic(), k -> new ArrayList<>()).add(h);
        }
        List<DaprSubscriptionEntry> entries = new ArrayList<>();
        for (List<DaprSubscriptionHandler> handlers : byTopic.values()) {
            entries.add(toEntry(handlers));
        }
        entries.addAll(advertisedOnly);
        return entries;
    }

    private static DaprSubscriptionEntry toEntry(List<DaprSubscriptionHandler> handlers) {
        DaprSubscriptionHandler first = handlers.getFirst();
        String defaultRoute = handlers.stream().filter(h -> h.match() == null).map(DaprSubscriptionHandler::route)
                .findFirst().orElse(null);
        List<DaprSubscriptionHandler> ruled = handlers.stream().filter(h -> h.match() != null)
                .sorted(Comparator.comparingInt(DaprSubscriptionHandler::priority)).toList();
        Map<String, String> metadata = new LinkedHashMap<>();
        String deadLetter = null;
        SubscribeOptions.BulkSubscribe bulk = null;
        for (DaprSubscriptionHandler h : handlers) {
            metadata.putAll(h.metadata());
            deadLetter = deadLetter != null ? deadLetter : h.deadLetterTopic();
            bulk = bulk != null ? bulk : h.bulk();
        }
        DaprSubscriptionEntry.BulkSubscribe bulkEntry = bulk == null ? null : new DaprSubscriptionEntry.BulkSubscribe(
                true, bulk.maxMessagesCount() > 0 ? bulk.maxMessagesCount() : null,
                bulk.maxAwaitDurationMs() >= 0 ? bulk.maxAwaitDurationMs() : null);
        if (ruled.isEmpty()) {
            return new DaprSubscriptionEntry(first.pubsubName(), first.topic(), defaultRoute, null, metadata,
                    deadLetter, bulkEntry);
        }
        List<DaprSubscriptionEntry.Rule> rules = ruled.stream()
                .map(h -> new DaprSubscriptionEntry.Rule(h.match(), h.route())).toList();
        return new DaprSubscriptionEntry(first.pubsubName(), first.topic(), null,
                new DaprSubscriptionEntry.Routes(rules, defaultRoute), metadata, deadLetter, bulkEntry);
    }

    /**
     * Looks up the handler of a delivery route.
     *
     * @param route the request path
     * @return the handler, if any
     */
    public Optional<DaprSubscriptionHandler> findHandler(String route) {
        synchronized (handlersByRoute) {
            return Optional.ofNullable(handlersByRoute.get(route));
        }
    }

    /** All registered handlers, in registration order. */
    public List<DaprSubscriptionHandler> getHandlers() {
        synchronized (handlersByRoute) {
            return List.copyOf(handlersByRoute.values());
        }
    }

    /** Number of registered handlers. */
    public int size() {
        synchronized (handlersByRoute) {
            return handlersByRoute.size();
        }
    }

    /** The dispatcher handlers are invoked through. */
    public DaprEventDispatcher dispatcher() {
        return dispatcher;
    }

    @Override
    public void destroy() {
        List<DaprSubscription> bindings;
        synchronized (handlersByRoute) {
            bindings = new ArrayList<>(localBindings.values());
            localBindings.clear();
        }
        bindings.forEach(DaprSubscription::unsubscribe);
        if (dapr != null) {
            dapr.setProgrammaticSubscriptions(null);
        }
    }

    private String deliveryDescription() {
        if (isMemory()) {
            return "in memory";
        }
        return httpDelivery ? "over HTTP (GET /dapr/subscribe)" : (dapr != null ? "by streaming subscription" : "nowhere");
    }

    private boolean isMemory() {
        return dapr != null && dapr.mode() == DaprMode.MEMORY;
    }

    private String pubsubOrDefault(String pubsub) {
        if (pubsub != null && !pubsub.isBlank()) {
            return pubsub;
        }
        return dapr != null ? dapr.components().pubsub() : DaprComponents.DEFAULT_PUBSUB;
    }

    private String deadLetterOrDefault(String deadLetterTopic) {
        if (deadLetterTopic != null && !deadLetterTopic.isBlank()) {
            return deadLetterTopic;
        }
        if (applicationContext == null) {
            return null;
        }
        return emptyToNull(applicationContext.getEnvironment().getProperty("adhar.dapr.pub-sub-config.dead-letter-topic"));
    }

    private String resolve(String value) {
        if (value == null || applicationContext == null || !value.contains("${")) {
            return value;
        }
        return applicationContext.getEnvironment().resolveRequiredPlaceholders(value);
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

    /**
     * The delivery route of a subscription: the declared route, or
     * {@code /dapr/events/<pubsub>/<topic>}.
     *
     * @param pubsubName the pub/sub component
     * @param topic      the topic
     * @param route      the declared route, or {@code null}
     * @return the route, with a leading slash and encoded for a URL path
     */
    public static String routeFor(String pubsubName, String topic, String route) {
        if (route != null && !route.isBlank()) {
            String trimmed = route.trim();
            return DaprHttpClient.encodePath(trimmed.startsWith("/") ? trimmed : "/" + trimmed);
        }
        return buildRoute(pubsubName, topic);
    }

    /**
     * The default delivery route, {@code /dapr/events/<pubsub>/<topic>}.
     *
     * @param pubsubName the pub/sub component
     * @param topic      the topic
     * @return the route
     */
    public static String buildRoute(String pubsubName, String topic) {
        return DEFAULT_ROUTE_PREFIX + DaprHttpClient.encodeSegment(pubsubName) + "/" + DaprHttpClient.encodePath(topic);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
