package com.adhar.kit.config.refresh;

import com.adhar.kit.config.annotation.RefreshConfig;
import com.adhar.kit.config.manager.ConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * BeanPostProcessor that powers the {@link RefreshConfig @RefreshConfig} annotation.
 *
 * <p>Scans every bean for methods annotated with {@code @RefreshConfig} and registers a
 * {@link ConfigManager.ConfigChangeListener} per method. When a watched configuration key
 * changes (via {@link ConfigManager#refreshAll()} or {@link ConfigManager#refreshSource(String)}),
 * the annotated method is invoked.</p>
 *
 * <p><b>Matching rules:</b></p>
 * <ul>
 *   <li>{@code keys} - method is invoked when any listed key changes</li>
 *   <li>{@code prefix} - method is invoked when any key starting with the prefix changes</li>
 *   <li>neither set - method is invoked on every configuration change</li>
 * </ul>
 *
 * <p><b>Supported method signatures:</b></p>
 * <ul>
 *   <li>zero-argument: {@code void reload()}</li>
 *   <li>three-argument: {@code void onChange(String key, Object oldValue, Object newValue)}</li>
 * </ul>
 *
 * <p>Methods with {@code refreshOnStartup = true} (zero-argument only) are invoked once
 * when the bean is initialized.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see RefreshConfig
 * @see ConfigManager.ConfigChangeListener
 */
@Slf4j
public class RefreshConfigBeanPostProcessor implements BeanPostProcessor {

    private final ObjectFactory<ConfigManager> configManagerFactory;

    /**
     * Creates the post-processor.
     *
     * @param configManagerFactory lazy factory for the ConfigManager (avoids early
     *                             bean initialization from the post-processor)
     */
    public RefreshConfigBeanPostProcessor(ObjectFactory<ConfigManager> configManagerFactory) {
        this.configManagerFactory = configManagerFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> targetClass = ClassUtils.getUserClass(bean);

        ReflectionUtils.doWithMethods(targetClass, method -> {
            RefreshConfig annotation = AnnotatedElementUtils.findMergedAnnotation(method, RefreshConfig.class);
            if (annotation != null) {
                registerListener(bean, beanName, method, annotation);
            }
        }, ReflectionUtils.USER_DECLARED_METHODS);

        return bean;
    }

    /**
     * Registers a change listener for a single annotated method.
     */
    private void registerListener(Object bean, String beanName, Method method, RefreshConfig annotation) {
        int paramCount = method.getParameterCount();
        if (paramCount != 0 && paramCount != 3) {
            log.warn("@RefreshConfig method {}.{} must take zero arguments or "
                            + "(String key, Object oldValue, Object newValue) - skipping",
                    beanName, method.getName());
            return;
        }

        ReflectionUtils.makeAccessible(method);
        ConfigManager configManager = configManagerFactory.getObject();

        configManager.addChangeListener((key, oldValue, newValue) -> {
            if (matches(annotation, key)) {
                invoke(bean, beanName, method, key, oldValue, newValue);
            }
        });

        log.info("Registered @RefreshConfig listener for {}.{} (keys={}, prefix='{}')",
                beanName, method.getName(), Arrays.toString(annotation.keys()), annotation.prefix());

        if (annotation.refreshOnStartup()) {
            if (paramCount == 0) {
                invoke(bean, beanName, method, null, null, null);
            } else {
                log.warn("@RefreshConfig(refreshOnStartup=true) on {}.{} is only supported "
                        + "for zero-argument methods - skipping startup invocation", beanName, method.getName());
            }
        }
    }

    /**
     * Checks whether a changed key matches the annotation's keys/prefix attributes.
     */
    private boolean matches(RefreshConfig annotation, String changedKey) {
        String[] keys = annotation.keys();
        String prefix = annotation.prefix();

        boolean hasKeys = keys.length > 0;
        boolean hasPrefix = prefix != null && !prefix.isEmpty();

        if (!hasKeys && !hasPrefix) {
            return true;
        }
        if (hasKeys && Arrays.asList(keys).contains(changedKey)) {
            return true;
        }
        return hasPrefix && changedKey != null && changedKey.startsWith(prefix);
    }

    /**
     * Invokes the annotated method, passing change details for three-argument signatures.
     */
    private void invoke(Object bean, String beanName, Method method,
                        String key, Object oldValue, Object newValue) {
        try {
            if (method.getParameterCount() == 3) {
                method.invoke(bean, key, oldValue, newValue);
            } else {
                method.invoke(bean);
            }
            log.debug("Invoked @RefreshConfig method {}.{} for key '{}'", beanName, method.getName(), key);
        } catch (Exception e) {
            log.error("Failed to invoke @RefreshConfig method {}.{}", beanName, method.getName(), e);
        }
    }
}
