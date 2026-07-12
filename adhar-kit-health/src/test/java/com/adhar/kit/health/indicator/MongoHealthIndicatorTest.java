package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MongoHealthIndicator}.
 *
 * <p>The indicator runs admin commands via reflection against an
 * {@code org.bson.Document}-based API, so the tests provide a public stub
 * MongoClient/database returning real {@link Document} results.</p>
 */
class MongoHealthIndicatorTest {

    private AdharHealthProperties.MongoConfig config() {
        return new AdharHealthProperties.MongoConfig();
    }

    @Test
    void getName_returnsMongodb() {
        MongoHealthIndicator indicator = new MongoHealthIndicator(new Object(), config());
        assertThat(indicator.getName()).isEqualTo("mongodb");
    }

    @Test
    void check_whenDisabled_returnsUnknown() {
        AdharHealthProperties.MongoConfig config = config();
        config.setEnabled(false);
        MongoHealthIndicator indicator = new MongoHealthIndicator(new FakeMongoClient(new FakeMongoDb()), config);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("status", "disabled");
    }

    @Test
    void check_whenReplicaSet_returnsUpWithServerInfo() {
        FakeMongoDb db = new FakeMongoDb();
        db.replicaSet = true;
        MongoHealthIndicator indicator = new MongoHealthIndicator(new FakeMongoClient(db), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails())
            .containsEntry("version", "7.0.5")
            .containsEntry("replicaSet", "rs0 (PRIMARY)")
            .containsEntry("maxBsonObjectSize", "16.02 MB");
    }

    @Test
    void check_whenStandaloneAndBuildInfoFails_returnsUpWithDefaults() {
        FakeMongoDb db = new FakeMongoDb();
        db.replicaSet = false;
        db.failBuildInfo = true;
        MongoHealthIndicator indicator = new MongoHealthIndicator(new FakeMongoClient(db), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails())
            .containsEntry("version", "unknown")
            .containsEntry("replicaSet", "standalone")
            .containsEntry("maxBsonObjectSize", "unknown");
    }

    @Test
    void check_whenPingNotOk_returnsDown() {
        FakeMongoDb db = new FakeMongoDb();
        db.pingOk = 0.0;
        MongoHealthIndicator indicator = new MongoHealthIndicator(new FakeMongoClient(db), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "Ping command failed");
    }

    @Test
    void check_whenPingResultMissingOk_returnsDown() {
        FakeMongoDb db = new FakeMongoDb();
        db.pingOk = null;
        MongoHealthIndicator indicator = new MongoHealthIndicator(new FakeMongoClient(db), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "Ping command failed");
    }

    @Test
    void check_whenClientHasNoGetDatabase_returnsDown() {
        MongoHealthIndicator indicator = new MongoHealthIndicator(new Object(), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
    }

    @Test
    void getReplicaStateDescription_coversAllStates() throws Exception {
        Method m = MongoHealthIndicator.class.getDeclaredMethod("getReplicaStateDescription", Object.class);
        m.setAccessible(true);
        MongoHealthIndicator indicator = new MongoHealthIndicator(new Object(), config());

        assertThat((String) m.invoke(indicator, (Object) null)).isEqualTo("unknown");
        assertThat((String) m.invoke(indicator, 0)).isEqualTo("STARTUP");
        assertThat((String) m.invoke(indicator, 1)).isEqualTo("PRIMARY");
        assertThat((String) m.invoke(indicator, 2)).isEqualTo("SECONDARY");
        assertThat((String) m.invoke(indicator, 3)).isEqualTo("RECOVERING");
        assertThat((String) m.invoke(indicator, 5)).isEqualTo("STARTUP2");
        assertThat((String) m.invoke(indicator, 6)).isEqualTo("UNKNOWN");
        assertThat((String) m.invoke(indicator, 7)).isEqualTo("ARBITER");
        assertThat((String) m.invoke(indicator, 8)).isEqualTo("DOWN");
        assertThat((String) m.invoke(indicator, 9)).isEqualTo("ROLLBACK");
        assertThat((String) m.invoke(indicator, 10)).isEqualTo("REMOVED");
        assertThat((String) m.invoke(indicator, 42)).isEqualTo("STATE_42");
    }

    @Test
    void formatBytes_coversAllUnitBranches() throws Exception {
        Method m = MongoHealthIndicator.class.getDeclaredMethod("formatBytes", long.class);
        m.setAccessible(true);
        MongoHealthIndicator indicator = new MongoHealthIndicator(new Object(), config());

        assertThat((String) m.invoke(indicator, 512L)).isEqualTo("512 B");
        assertThat((String) m.invoke(indicator, 2048L)).contains("KB");
        assertThat((String) m.invoke(indicator, 5L * 1024 * 1024)).contains("MB");
    }

    // ---- Reflection stub classes ----

    public static class FakeMongoClient {
        private final FakeMongoDb db;

        public FakeMongoClient(FakeMongoDb db) {
            this.db = db;
        }

        public Object getDatabase(String name) {
            return db;
        }
    }

    public static class FakeMongoDb {
        Object pingOk = 1.0;
        boolean replicaSet = false;
        boolean failBuildInfo = false;

        public Document runCommand(Document command) {
            if (command.containsKey("ping")) {
                Document result = new Document();
                if (pingOk != null) {
                    result.put("ok", pingOk);
                }
                return result;
            }
            if (command.containsKey("buildInfo")) {
                if (failBuildInfo) {
                    throw new IllegalStateException("buildInfo not permitted");
                }
                return new Document("version", "7.0.5").append("maxBsonObjectSize", 16793600L);
            }
            if (command.containsKey("replSetGetStatus")) {
                if (!replicaSet) {
                    throw new IllegalStateException("not running with --replSet");
                }
                return new Document("set", "rs0").append("myState", 1);
            }
            return new Document();
        }
    }
}
