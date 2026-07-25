package com.adhar.kit.grpc.spring;

import com.adhar.kit.grpc.annotation.GrpcService;
import com.adhar.kit.grpc.config.GrpcProperties;
import com.adhar.kit.grpc.server.AdharGrpcServer;
import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GrpcServiceRegistrar}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class GrpcServiceRegistrarTest {

    @Test
    void registersBindableServiceBeans_andSkipsNonBindableOnes() {
        ApplicationContext context = mock(ApplicationContext.class);
        Map<String, Object> beans = new LinkedHashMap<>();
        beans.put("goodService", new SampleBindableService());
        beans.put("notAService", new Object());
        when(context.getBeansWithAnnotation(GrpcService.class)).thenReturn(beans);

        AdharGrpcServer server = new AdharGrpcServer(new GrpcProperties());
        GrpcServiceRegistrar registrar = new GrpcServiceRegistrar(context, server);

        registrar.afterSingletonsInstantiated();

        assertThat(server.getServiceCount()).isEqualTo(1);
    }

    @Test
    void noAnnotatedBeans_registersNothing() {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeansWithAnnotation(GrpcService.class)).thenReturn(Map.of());

        AdharGrpcServer server = new AdharGrpcServer(new GrpcProperties());
        new GrpcServiceRegistrar(context, server).afterSingletonsInstantiated();

        assertThat(server.getServiceCount()).isEqualTo(0);
    }

    private static class SampleBindableService implements BindableService {
        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder("test.Sample").build();
        }
    }
}
