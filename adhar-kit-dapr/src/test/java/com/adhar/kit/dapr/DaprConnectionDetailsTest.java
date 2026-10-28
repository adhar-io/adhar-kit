package com.adhar.kit.dapr;

import io.dapr.config.Properties;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The adhar-kit sidecar discovery contract: endpoint, token, app id, mode and component names.
 */
class DaprConnectionDetailsTest {

    private static DaprConnectionDetails resolve(String... pairs) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return DaprConnectionDetails.resolve(values::get);
    }

    @Test
    void autoModeIsMemoryWithoutTheInjectorsVariables() {
        DaprConnectionDetails details = resolve();
        assertThat(details.mode()).isEqualTo(DaprMode.MEMORY);
        assertThat(details.requestedMode()).isEqualTo(DaprMode.AUTO);
        assertThat(details.httpEndpoint()).isEqualTo("http://127.0.0.1:3500");
        assertThat(details.appId()).isEqualTo("unknown");
    }

    @Test
    void aSidecarPortOrEndpointSelectsSidecarMode() {
        assertThat(resolve("DAPR_HTTP_PORT", "3601").mode()).isEqualTo(DaprMode.SIDECAR);
        assertThat(resolve("DAPR_HTTP_PORT", "3601").httpEndpoint()).isEqualTo("http://127.0.0.1:3601");
        DaprConnectionDetails remote = resolve("DAPR_HTTP_ENDPOINT", "https://dapr.example:443/", "DAPR_SIDECAR_IP", "x");
        assertThat(remote.mode()).isEqualTo(DaprMode.SIDECAR);
        assertThat(remote.httpEndpoint()).isEqualTo("https://dapr.example:443");
        assertThat(resolve("DAPR_SIDECAR_IP", "10.0.0.5", "DAPR_HTTP_PORT", "3500").httpEndpoint())
                .isEqualTo("http://10.0.0.5:3500");
    }

    @Test
    void explicitModesWinAndTyposAreRejected() {
        assertThat(resolve("ADHAR_DAPR_MODE", "sidecar").mode()).isEqualTo(DaprMode.SIDECAR);
        assertThat(resolve("adhar.dapr.mode", "MEMORY", "DAPR_HTTP_PORT", "3500").mode()).isEqualTo(DaprMode.MEMORY);
        assertThatThrownBy(() -> resolve("ADHAR_DAPR_MODE", "sidcar")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("auto, sidecar or memory");
    }

    @Test
    void appIdComesFromTheFirstAvailableSource() {
        assertThat(resolve("ADHAR_APP_NAME", "c", "DAPR_APP_ID", "b", "APP_ID", "a").appId()).isEqualTo("a");
        assertThat(resolve("ADHAR_APP_NAME", "c", "DAPR_APP_ID", "b").appId()).isEqualTo("b");
        assertThat(resolve("spring.application.name", "d", "ADHAR_APP_NAME", "c").appId()).isEqualTo("c");
        assertThat(resolve("adhar.dapr.app-id", "kit", "APP_ID", "a").appId()).isEqualTo("kit");
    }

    @Test
    void sdkPropertiesCarryTheSameEndpointAndToken() {
        DaprConnectionDetails details = resolve("DAPR_HTTP_PORT", "3700", "DAPR_GRPC_PORT", "3701",
                "DAPR_API_TOKEN", "t0k3n", "DAPR_GRPC_ENDPOINT", "dns://localhost:3701");
        Properties properties = details.sdkProperties();
        assertThat(properties.getValue(Properties.HTTP_ENDPOINT)).isEqualTo("http://127.0.0.1:3700");
        assertThat(properties.getValue(Properties.GRPC_PORT)).isEqualTo(3701);
        assertThat(properties.getValue(Properties.API_TOKEN)).isEqualTo("t0k3n");
        assertThat(properties.getValue(Properties.GRPC_ENDPOINT)).isEqualTo("dns://localhost:3701");
        assertThat(details.toString()).doesNotContain("t0k3n").contains("apiToken=***");
    }

    @Test
    void componentNamesDefaultToThePlatformsAndFollowTheEnvironment() {
        assertThat(DaprComponents.defaults()).isEqualTo(new DaprComponents("pubsub", "statestore", "secretstore",
                "configstore", "lockstore", "crypto"));
        Map<String, String> env = Map.of("ADHAR_DAPR_PUBSUB", "kafka", "ADHAR_DAPR_STATESTORE", "pg",
                "ADHAR_DAPR_SECRETSTORE", "vault", "ADHAR_DAPR_CONFIGSTORE", "flags", "ADHAR_DAPR_LOCKSTORE", "redis-lock");
        assertThat(DaprComponents.resolve(env::get)).isEqualTo(new DaprComponents("kafka", "pg", "vault", "flags",
                "redis-lock", "crypto"));
        assertThat(DaprComponents.resolve(Map.of("adhar.dapr.configuration-store", "legacy")::get).configStore())
                .isEqualTo("legacy");
    }
}
