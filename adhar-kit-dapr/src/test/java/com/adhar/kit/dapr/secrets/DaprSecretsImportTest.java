package com.adhar.kit.dapr.secrets;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprSecret;
import com.adhar.kit.dapr.support.FakeSidecar;
import com.adhar.kit.dapr.support.MemoryDapr;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.env.PropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Secrets as configuration ({@code dapr-secrets:} imports) and as injected fields.
 */
class DaprSecretsImportTest {

    @Test
    void locationsNameAStoreSecretsAndAPrefix() {
        DaprSecretsConfigDataResource all = DaprSecretsConfigDataLocationResolver.parse("vault", false);
        assertThat(all.getStore()).isEqualTo("vault");
        assertThat(all.getNames()).isEmpty();

        DaprSecretsConfigDataResource named = DaprSecretsConfigDataLocationResolver.parse(
                "secretstore/db, api-key?prefix=secrets.", true);
        assertThat(named.getNames()).containsExactly("db", "api-key");
        assertThat(named.getPrefix()).isEqualTo("secrets.");
        assertThat(DaprSecretsConfigDataLocationResolver.parse("", false).getStore()).isEqualTo("secretstore");
        assertThat(named.toString()).isEqualTo("dapr-secrets:secretstore/db,api-key");
    }

    @Test
    void namedSecretsAreReadFromTheSidecarAsProperties() throws Exception {
        try (FakeSidecar sidecar = new FakeSidecar()) {
            sidecar.respond("/v1.0/healthz/outbound", 204, "");
            sidecar.respond("/v1.0/secrets/secretstore/db", 200, "{\"username\":\"app\",\"password\":\"p\"}");
            sidecar.respond("/v1.0/secrets/secretstore/api-key", 200, "{\"api-key\":\"k\"}");
            DaprSecretsConfigDataResource resource = new DaprSecretsConfigDataResource("secretstore",
                    List.of("db", "api-key"), "", false, sidecar.connection("app", "tok"), Duration.ofSeconds(1), Map.of());

            PropertySource<?> source = new DaprSecretsConfigDataLoader().load(null, resource).getPropertySources().getFirst();

            assertThat(source.getProperty("db.password")).isEqualTo("p");
            assertThat(source.getProperty("db.username")).isEqualTo("app");
            assertThat(source.getProperty("api-key")).isEqualTo("k");
            assertThat(sidecar.lastRequest().header("dapr-api-token")).isEqualTo("tok");
        }
    }

    @Test
    void theWholeStoreCanBeImportedAndFailuresAreReported() throws Exception {
        try (FakeSidecar sidecar = new FakeSidecar()) {
            sidecar.respond("/v1.0/healthz/outbound", 204, "");
            sidecar.respond("/v1.0/secrets/vault/bulk", 200, "{\"db\":{\"password\":\"p\"}}");
            DaprSecretsConfigDataResource bulk = new DaprSecretsConfigDataResource("vault", List.of(), "secrets.", false,
                    sidecar.connection("app", null), Duration.ofSeconds(1), Map.of());
            ConfigData data = new DaprSecretsConfigDataLoader().load(null, bulk);
            assertThat(data.getPropertySources().getFirst().getProperty("secrets.db")).isEqualTo("p");

            DaprSecretsConfigDataResource missing = new DaprSecretsConfigDataResource("vault", List.of("nope"), "", false,
                    sidecar.connection("app", null), Duration.ofSeconds(1), Map.of());
            assertThatThrownBy(() -> new DaprSecretsConfigDataLoader().load(null, missing))
                    .isInstanceOf(ConfigDataResourceNotFoundException.class);
        }
    }

    @Test
    void memoryModeImportsTheConfiguredSecrets() throws Exception {
        DaprSecretsConfigDataResource resource = new DaprSecretsConfigDataResource("secretstore", List.of("db"), "",
                false, MemoryDapr.memoryConnection("app"), Duration.ZERO, Map.of("db", Map.of("password", "local")));

        PropertySource<?> source = new DaprSecretsConfigDataLoader().load(null, resource).getPropertySources().getFirst();

        assertThat(source.getProperty("db.password")).isEqualTo("local");
        assertThat(source.getProperty("db")).isEqualTo("local");
    }

    static class NeedsSecrets {
        @DaprSecret(secretName = "db", key = "password")
        String password;

        @DaprSecret(secretName = "api-key")
        char[] apiKey;

        @DaprSecret(secretName = "db")
        Map<String, String> db;
    }

    static class NeedsMissingSecret {
        @DaprSecret(secretName = "absent")
        String value;
    }

    @Test
    void annotatedFieldsAreInjectedOrFailTheBean() {
        DaprFacade dapr = MemoryDapr.facade();
        dapr.memory().putSecret("secretstore", "db", Map.of("username", "app", "password", "p"));
        dapr.memory().putSecret("secretstore", "api-key", Map.of("api-key", "k"));
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("dapr", dapr);
        DaprSecretInjector injector = new DaprSecretInjector(context.getBeanProvider(DaprFacade.class));

        NeedsSecrets bean = (NeedsSecrets) injector.postProcessBeforeInitialization(new NeedsSecrets(), "bean");

        assertThat(bean.password).isEqualTo("p");
        assertThat(bean.apiKey).containsExactly('k');
        assertThat(bean.db).containsEntry("username", "app");
        assertThatThrownBy(() -> injector.postProcessBeforeInitialization(new NeedsMissingSecret(), "missing"))
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("absent");
    }
}
