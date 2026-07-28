package com.adhar.kit.config.autoconfigure;

import com.adhar.kit.config.audit.ConfigChangeAuditPublisher;
import com.adhar.kit.config.endpoint.AdharConfigEndpoint;
import com.adhar.kit.config.featureflag.FeatureFlagService;
import com.adhar.kit.config.manager.ConfigManager;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigAutoConfiguration.class));

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void registersCoreBeansByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ConfigManager.class);
            assertThat(context).hasSingleBean(ConfigChangeAuditPublisher.class);
            // actuator on classpath -> endpoint present
            assertThat(context).hasSingleBean(AdharConfigEndpoint.class);
            // feature flags disabled by default
            assertThat(context).doesNotHaveBean(FeatureFlagService.class);
        });
    }

    @Test
    void featureFlagServiceCreatedAndSeededWhenEnabled() {
        runner.withPropertyValues(
                "adhar.config.feature-flags.enabled=true",
                "adhar.config.feature-flags.flags.new-ui.enabled=true",
                "adhar.config.feature-flags.flags.new-ui.rollout-percentage=25",
                "adhar.config.feature-flags.flags.new-ui.allow-list[0]=vip"
        ).run(context -> {
            assertThat(context).hasSingleBean(FeatureFlagService.class);
            FeatureFlagService service = context.getBean(FeatureFlagService.class);
            assertThat(service.getFlag("new-ui")).isNotNull();
            assertThat(service.getFlag("new-ui").rolloutPercentage()).isEqualTo(25);
            assertThat(service.isEnabled("new-ui", "vip")).isTrue();
        });
    }

    @Test
    void auditDisabledRemovesPublisher() {
        runner.withPropertyValues("adhar.config.audit.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ConfigChangeAuditPublisher.class));
    }

    @Test
    void endpointDisabledRemovesEndpoint() {
        runner.withPropertyValues("adhar.config.endpoint.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AdharConfigEndpoint.class));
    }

    @Test
    void wiresConfigMapSourceFromProperties(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("app.mode"), "prod");
        runner.withPropertyValues(
                "adhar.config.sources.cm.type=configmap",
                "adhar.config.sources.cm.location=" + dir,
                "adhar.config.sources.cm.priority=130"
        ).run(context -> {
            ConfigManager manager = context.getBean(ConfigManager.class);
            assertThat(manager.getHealthStatus()).containsKey("configmap");
            assertThat(manager.getProperty("app.mode")).isEqualTo("prod");
        });
    }

    @Test
    void wiresConsulSourceFromProperties() throws IOException {
        String value = Base64.getEncoder().encodeToString("jdbc:pg".getBytes(StandardCharsets.UTF_8));
        String body = "[{\"Key\":\"config/app/database.url\",\"Value\":\"" + value + "\"}]";
        String url = startServer("/v1/kv/", body);

        runner.withPropertyValues(
                "adhar.config.sources.consul.type=consul",
                "adhar.config.sources.consul.location=" + url,
                "adhar.config.sources.consul.prefix=config/app",
                "adhar.config.sources.consul.priority=140"
        ).run(context -> {
            ConfigManager manager = context.getBean(ConfigManager.class);
            assertThat(manager.getHealthStatus()).containsKey("consul");
            assertThat(manager.getProperty("database.url")).isEqualTo("jdbc:pg");
        });
    }

    @Test
    void wiresVaultSourceFromProperties() throws IOException {
        String body = "{\"data\":{\"data\":{\"db.password\":\"secret\"}}}";
        String address = startServer("/v1/secret/data/app", body);

        runner.withPropertyValues(
                "adhar.config.sources.vault.type=vault",
                "adhar.config.sources.vault.location=" + address,
                "adhar.config.sources.vault.prefix=secret/app",
                "adhar.config.sources.vault.priority=150",
                "adhar.config.sources.vault.auth.token=my-token"
        ).run(context -> {
            ConfigManager manager = context.getBean(ConfigManager.class);
            assertThat(manager.getHealthStatus()).containsKey("vault");
            assertThat(manager.getProperty("db.password")).isEqualTo("secret");
        });
    }

    private String startServer(String path, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
