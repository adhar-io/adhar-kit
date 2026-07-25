package com.adhar.kit.grpc.spring;

import com.adhar.kit.grpc.annotation.GrpcService;
import com.adhar.kit.grpc.server.AdharGrpcServer;
import io.grpc.BindableService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * Discovers Spring beans annotated with {@link GrpcService} and registers
 * them with an {@link AdharGrpcServer}.
 *
 * <p>Runs as a {@link SmartInitializingSingleton} so that every singleton bean
 * (including all {@code @GrpcService} beans) has already been fully
 * initialized by the time registration happens, but before the
 * {@link org.springframework.context.SmartLifecycle} phase starts the actual
 * gRPC server.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class GrpcServiceRegistrar implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final AdharGrpcServer adharGrpcServer;

    /**
     * Creates the registrar.
     *
     * @param applicationContext context to scan for {@code @GrpcService} beans
     * @param adharGrpcServer    server to register discovered services with
     */
    public GrpcServiceRegistrar(ApplicationContext applicationContext, AdharGrpcServer adharGrpcServer) {
        this.applicationContext = applicationContext;
        this.adharGrpcServer = adharGrpcServer;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Object> grpcServiceBeans = applicationContext.getBeansWithAnnotation(GrpcService.class);

        int registered = 0;
        for (Map.Entry<String, Object> entry : grpcServiceBeans.entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();

            if (bean instanceof BindableService bindableService) {
                adharGrpcServer.addService(bindableService);
                registered++;
                log.info("Registered gRPC service bean '{}' ({}) with AdharGrpcServer",
                        beanName, bean.getClass().getSimpleName());
            } else {
                log.warn("Bean '{}' is annotated with @GrpcService but does not implement "
                        + "io.grpc.BindableService; skipping", beanName);
            }
        }

        log.info("GrpcServiceRegistrar registered {} of {} @GrpcService bean(s)",
                registered, grpcServiceBeans.size());
    }
}
