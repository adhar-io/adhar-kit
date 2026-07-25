package com.adhar.kit.grpc.spring;

import com.adhar.kit.grpc.annotation.GrpcClient;
import com.adhar.kit.grpc.client.AdharGrpcClientFactory;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * {@link BeanPostProcessor} that injects a {@link ManagedChannel} into any
 * field annotated with {@link GrpcClient}, looking up the channel by the
 * annotation's {@code value()} (the configured channel name).
 *
 * <p>Only fields declared as (or assignable from) {@link ManagedChannel} are
 * currently supported; other field types are logged and left untouched so
 * that unrelated {@code @GrpcClient} usages do not break bean creation.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class GrpcClientBeanPostProcessor implements BeanPostProcessor {

    private final AdharGrpcClientFactory clientFactory;

    /**
     * Creates the post-processor.
     *
     * @param clientFactory factory used to resolve named channels
     */
    public GrpcClientBeanPostProcessor(AdharGrpcClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        ReflectionUtils.doWithFields(bean.getClass(), field -> injectIfAnnotated(bean, field));
        return bean;
    }

    private void injectIfAnnotated(Object bean, Field field) {
        GrpcClient annotation = field.getAnnotation(GrpcClient.class);
        if (annotation == null) {
            return;
        }

        if (!ManagedChannel.class.isAssignableFrom(field.getType())) {
            log.warn("Field '{}' on {} is annotated with @GrpcClient but is of unsupported type {}; "
                            + "only ManagedChannel fields are currently supported, skipping",
                    field.getName(), bean.getClass().getName(), field.getType().getName());
            return;
        }

        ManagedChannel channel = clientFactory.getChannel(annotation.value());
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, bean, channel);
        log.info("Injected gRPC channel '{}' into field '{}' of {}",
                annotation.value(), field.getName(), bean.getClass().getSimpleName());
    }
}
