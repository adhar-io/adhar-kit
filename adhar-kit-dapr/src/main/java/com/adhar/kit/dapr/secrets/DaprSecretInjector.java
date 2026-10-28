package com.adhar.kit.dapr.secrets;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprSecret;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

/**
 * Injects {@link DaprSecret} fields when a bean is created: a {@code String} (or {@code char[]})
 * field receives one value, a {@code Map<String, String>} field the whole secret. A secret that
 * cannot be read fails the bean's creation, like a missing required property, rather than leaving
 * a {@code null} credential to fail later.
 *
 * <p>The facade is looked up on first use, so this post-processor does not pull the Dapr client
 * into the early, post-processor phase of the context.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprSecretInjector implements BeanPostProcessor {

    private final ObjectProvider<DaprFacade> dapr;

    public DaprSecretInjector(ObjectProvider<DaprFacade> dapr) {
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> type = ClassUtils.getUserClass(bean.getClass());
        ReflectionUtils.doWithFields(type, field -> inject(bean, beanName, field),
                field -> field.isAnnotationPresent(DaprSecret.class));
        return bean;
    }

    private void inject(Object bean, String beanName, Field field) {
        DaprSecret secret = field.getAnnotation(DaprSecret.class);
        DaprFacade facade = dapr.getObject();
        String store = secret.storeName().isBlank() ? facade.components().secretStore() : secret.storeName();
        Object value;
        try {
            Map<String, String> values = facade.getSecretValues(store, secret.secretName());
            if (Map.class.isAssignableFrom(field.getType())) {
                value = values;
            } else {
                String text;
                if (!secret.key().isBlank()) {
                    text = values.get(secret.key());
                } else {
                    text = values.get(secret.secretName());
                    if (text == null && values.size() == 1) {
                        text = values.values().iterator().next();
                    }
                }
                if (text == null) {
                    throw new IllegalStateException("secret '" + secret.secretName() + "' in store '" + store
                            + "' has no " + (secret.key().isBlank() ? "single value" : "key '" + secret.key() + "'"));
                }
                value = field.getType() == char[].class ? text.toCharArray() : text;
            }
        } catch (RuntimeException e) {
            throw new BeanCreationException(beanName, "Could not inject @DaprSecret field '" + field.getName()
                    + "' (secret '" + secret.secretName() + "' in store '" + store + "'): " + e.getMessage(), e);
        }
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, bean, value);
    }
}
