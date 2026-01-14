package com.adhar.kit.grpc.config;

import com.adhar.kit.grpc.GrpcServerLifecycle;
import com.adhar.kit.grpc.annotation.GrpcService;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(GrpcProperties.class)
@ConditionalOnBean(annotation = GrpcService.class)
public class GrpcAutoConfiguration {

    @Autowired
    private GrpcProperties grpcProperties;

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public GrpcServerLifecycle grpcServerLifecycle(Server server) {
        return new GrpcServerLifecycle(server);
    }

    @Bean
    public Server grpcServer() throws IOException {
        ServerBuilder<?> serverBuilder = ServerBuilder.forPort(grpcProperties.getServer().getPort());
        Map<String, Object> grpcServices = applicationContext.getBeansWithAnnotation(GrpcService.class);
        for (Object grpcService : grpcServices.values()) {
            if (grpcService instanceof BindableService) {
                serverBuilder.addService((BindableService) grpcService);
            }
        }
        return serverBuilder.build();
    }
}

