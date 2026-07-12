package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RedisHealthIndicator}.
 *
 * <p>The indicator interacts with the connection factory purely via reflection, so
 * the tests provide lightweight public stub classes that expose the expected method
 * signatures instead of depending on a real Redis client.</p>
 */
class RedisHealthIndicatorTest {

    private AdharHealthProperties.RedisConfig config() {
        return new AdharHealthProperties.RedisConfig();
    }

    @Test
    void getName_returnsRedis() {
        RedisHealthIndicator indicator = new RedisHealthIndicator(new Object(), config());
        assertThat(indicator.getName()).isEqualTo("redis");
    }

    @Test
    void check_whenDisabled_returnsUnknown() {
        AdharHealthProperties.RedisConfig config = config();
        config.setEnabled(false);
        RedisHealthIndicator indicator = new RedisHealthIndicator(new FakeFactory(new FakeConnection()), config);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("status", "disabled");
    }

    @Test
    void check_whenPingReturnsPong_returnsUpWithServerInfo() {
        RedisHealthIndicator indicator = new RedisHealthIndicator(new FakeFactory(new FakeConnection()), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails())
            .containsEntry("version", "7.2.4")
            .containsEntry("mode", "standalone")
            .containsEntry("connectedClients", "12");
    }

    @Test
    void check_whenPingNotPong_returnsDown() {
        FakeConnection connection = new FakeConnection();
        connection.pingResponse = "NOPONG";
        RedisHealthIndicator indicator = new RedisHealthIndicator(new FakeFactory(connection), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails().get("error").toString()).contains("Unexpected ping response");
    }

    @Test
    void check_whenServerInfoUnavailable_stillReturnsUpWithUnknownDetails() {
        FakeConnection connection = new FakeConnection();
        connection.failServerCommands = true;
        RedisHealthIndicator indicator = new RedisHealthIndicator(new FakeFactory(connection), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails())
            .containsEntry("version", "unknown")
            .containsEntry("mode", "unknown")
            .containsEntry("connectedClients", "unknown");
    }

    @Test
    void check_whenConnectionCloseThrows_stillReturnsUp() {
        FakeConnection connection = new FakeConnection();
        connection.failClose = true;
        RedisHealthIndicator indicator = new RedisHealthIndicator(new FakeFactory(connection), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
    }

    @Test
    void check_whenFactoryHasNoConnectionMethod_returnsDown() {
        RedisHealthIndicator indicator = new RedisHealthIndicator(new Object(), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
    }

    // ---- Reflection stub classes (must be public for reflective method access) ----

    public static class FakeFactory {
        private final FakeConnection connection;

        public FakeFactory(FakeConnection connection) {
            this.connection = connection;
        }

        public Object getConnection() {
            return connection;
        }
    }

    public static class FakeConnection {
        String pingResponse = "PONG";
        boolean failServerCommands = false;
        boolean failClose = false;
        boolean closed = false;

        public String ping() {
            return pingResponse;
        }

        public Object serverCommands() {
            if (failServerCommands) {
                throw new IllegalStateException("server commands unavailable");
            }
            return new FakeServerCommands();
        }

        public void close() {
            if (failClose) {
                throw new IllegalStateException("close failed");
            }
            closed = true;
        }
    }

    public static class FakeServerCommands {
        public Object info(String section) {
            return new FakeInfo(section);
        }
    }

    public static class FakeInfo {
        private final Map<String, Object> values = new HashMap<>();

        public FakeInfo(String section) {
            if ("server".equals(section)) {
                values.put("redis_version", "7.2.4");
                values.put("redis_mode", "standalone");
            } else if ("clients".equals(section)) {
                values.put("connected_clients", "12");
            }
        }

        public Object get(String key) {
            return values.get(key);
        }
    }
}
