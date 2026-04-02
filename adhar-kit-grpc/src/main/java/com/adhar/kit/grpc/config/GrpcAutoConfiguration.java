package com.adhar.kit.grpc.config;

import com.adhar.kit.grpc.GrpcServerLifecycle;
import com.adhar.kit.grpc.annotation.GrpcService;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.util.Map;

/**
 * Auto-configuration for Adhar gRPC module.
 *
 * <p>Automatically configures a gRPC server with services annotated with {@link GrpcService}.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Automatic gRPC server lifecycle management</li>
 *   <li>Service discovery via @GrpcService annotation</li>
 *   <li>Configurable server port and options</li>
 *   <li>Integration with Spring Boot health indicators</li>
 * </ul>
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * adhar:
 *   grpc:
 *     enabled: true
 *     server:
 *       port: 9090
 *       max-inbound-message-size: 4194304
 *       max-inbound-metadata-size: 8192
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(GrpcProperties.class)
@ConditionalOnClass(name = "io.grpc.Server")
@ConditionalOnProperty(prefix = "adhar.grpc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GrpcAutoConfiguration {

    private final GrpcProperties grpcProperties;
    private final ApplicationContext applicationContext;

    public GrpcAutoConfiguration(GrpcProperties grpcProperties, ApplicationContext applicationContext) {
        this.grpcProperties = grpcProperties;
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void logGrpcConfiguration() {
        log.info("Adhar gRPC module initialized - server port: {}", grpcProperties.getServer().getPort());
    }

    /**
     * Creates the gRPC server lifecycle manager.
     *
     * @param server the gRPC server
     * @return lifecycle manager for the gRPC server
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(Server.class)
    public GrpcServerLifecycle grpcServerLifecycle(Server server) {
        log.info("Registering gRPC server lifecycle manager");
        return new GrpcServerLifecycle(server);
    }

    /**
     * Creates and configures the gRPC server with all discovered services.
     *
     * @return configured gRPC server
     * @throws IOException if server creation fails
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(annotation = GrpcService.class)
    public Server grpcServer() throws IOException {
        int port = grpcProperties.getServer().getPort();
        ServerBuilder<?> serverBuilder = ServerBuilder.forPort(port);

        Map<String, Object> grpcServices = applicationContext.getBeansWithAnnotation(GrpcService.class);
        int serviceCount = 0;

        for (Map.Entry<String, Object> entry : grpcServices.entrySet()) {
            Object grpcService = entry.getValue();
            if (grpcService instanceof BindableService) {
                serverBuilder.addService((BindableService) grpcService);
                log.debug("Registered gRPC service: {}", entry.getKey());
                serviceCount++;
            }
        }

        log.info("gRPC server configured on port {} with {} services", port, serviceCount);
        return serverBuilder.build();
    }
}

