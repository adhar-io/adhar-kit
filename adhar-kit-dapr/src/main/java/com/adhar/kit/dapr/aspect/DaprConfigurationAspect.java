package com.adhar.kit.dapr.aspect;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.DaprSubscription;
import com.adhar.kit.dapr.annotation.DaprConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces {@link DaprConfiguration}: the method returns the configuration value converted to its
 * return type ({@code boolean}, {@code int}, {@code Duration}, ...), the annotation's
 * {@code defaultValue} when the store has none, or what the method body returns when there is
 * neither.
 *
 * <p>With {@code subscribe = true} the first call subscribes to the key and later calls read the
 * value the subscription keeps current, so a feature flag flips without a restart and without a
 * store read per call.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
@Aspect
public class DaprConfigurationAspect implements DisposableBean {

    private static final Optional<String> ABSENT = Optional.empty();

    private final DaprFacade daprFacade;
    private final boolean subscriptionsEnabled;
    private final ConversionService conversion = DefaultConversionService.getSharedInstance();
    private final Map<String, Optional<String>> liveValues = new ConcurrentHashMap<>();
    private final Map<String, DaprSubscription> subscriptions = new ConcurrentHashMap<>();

    public DaprConfigurationAspect(DaprFacade daprFacade) {
        this(daprFacade, true);
    }

    public DaprConfigurationAspect(DaprFacade daprFacade, boolean subscriptionsEnabled) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
        this.subscriptionsEnabled = subscriptionsEnabled;
    }

    @Around("@annotation(daprConfiguration)")
    public Object applyConfiguration(ProceedingJoinPoint joinPoint, DaprConfiguration daprConfiguration)
            throws Throwable {
        Method method = AspectSupport.resolveMethod(joinPoint);
        String store = daprConfiguration.storeName().isBlank()
                ? daprFacade.components().configStore() : daprConfiguration.storeName();
        String key = daprConfiguration.key();
        String value = daprConfiguration.subscribe() && subscriptionsEnabled
                ? liveValue(store, key)
                : daprFacade.getConfiguration(store, key);
        if (value == null && !daprConfiguration.defaultValue().isEmpty()) {
            value = daprConfiguration.defaultValue();
        }
        if (value == null) {
            return joinPoint.proceed();
        }
        Class<?> returnType = method != null ? method.getReturnType() : String.class;
        if (returnType == void.class || returnType == Void.class) {
            return joinPoint.proceed();
        }
        return conversion.convert(value, returnType);
    }

    private String liveValue(String store, String key) {
        String cacheKey = store + "\u0000" + key;
        Optional<String> cached = liveValues.get(cacheKey);
        if (cached != null) {
            return cached.orElse(null);
        }
        synchronized (subscriptions) {
            if (!subscriptions.containsKey(cacheKey)) {
                subscriptions.put(cacheKey, daprFacade.subscribeConfiguration(store, List.of(key), changed -> {
                    if (changed.containsKey(key)) {
                        liveValues.put(cacheKey, Optional.ofNullable(changed.get(key)));
                        log.debug("Configuration '{}' in store '{}' changed", key, store);
                    }
                }));
            }
        }
        String current = daprFacade.getConfiguration(store, key);
        liveValues.putIfAbsent(cacheKey, current == null ? ABSENT : Optional.of(current));
        return liveValues.get(cacheKey).orElse(null);
    }

    @Override
    public void destroy() {
        subscriptions.values().forEach(subscription -> {
            try {
                subscription.unsubscribe();
            } catch (RuntimeException e) {
                log.debug("Ending configuration subscription failed: {}", e.getMessage());
            }
        });
        subscriptions.clear();
    }
}
