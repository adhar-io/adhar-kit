package com.adhar.adharkit.logging.processor;

import com.adhar.adharkit.logging.LoggingFacade;
import com.adhar.adharkit.logging.annotation.InjectLogger;
import com.adhar.adharkit.logging.util.AdharLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

/**
 * {@link BeanPostProcessor} that automatically sets up loggers for fields annotated with
 * {@link InjectLogger @InjectLogger} on any Spring bean.
 *
 * <p>Supported field types:</p>
 * <ul>
 *   <li>{@link Logger org.slf4j.Logger} — created via {@link LoggerFactory}, named after the
 *       declaring class (or the explicit {@code @InjectLogger("name")} value)</li>
 *   <li>{@link LoggingFacade} — the framework-agnostic facade for the declaring class</li>
 *   <li>{@link AdharLogger} — the shared, fully configured AdharLogger bean</li>
 * </ul>
 *
 * <p>This removes per-class logger boilerplate while keeping conventional logger naming intact.</p>
 */
public class LoggerInjectionBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<AdharLogger> adharLoggerProvider;

    /**
     * Creates the post-processor.
     *
     * @param adharLoggerProvider lazy provider for the shared {@link AdharLogger} bean
     */
    public LoggerInjectionBeanPostProcessor(ObjectProvider<AdharLogger> adharLoggerProvider) {
        this.adharLoggerProvider = adharLoggerProvider;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        ReflectionUtils.doWithFields(targetClass, field -> {
            InjectLogger annotation = field.getAnnotation(InjectLogger.class);
            if (annotation == null) {
                return;
            }
            Object logger = resolveLogger(field.getType(), field.getDeclaringClass(), annotation);
            if (logger != null) {
                ReflectionUtils.makeAccessible(field);
                field.set(bean, logger);
            }
        });
        return bean;
    }

    private Object resolveLogger(Class<?> fieldType, Class<?> declaringClass, InjectLogger annotation) {
        if (Logger.class.isAssignableFrom(fieldType)) {
            return annotation.value().isEmpty()
                    ? LoggerFactory.getLogger(declaringClass)
                    : LoggerFactory.getLogger(annotation.value());
        }
        if (LoggingFacade.class.isAssignableFrom(fieldType)) {
            return annotation.value().isEmpty()
                    ? LoggingFacade.getLogger(declaringClass)
                    : LoggingFacade.getLogger(annotation.value());
        }
        if (AdharLogger.class.isAssignableFrom(fieldType)) {
            return adharLoggerProvider.getIfAvailable();
        }
        return null;
    }
}
