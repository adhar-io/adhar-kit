package com.adhar.kit.batch.lock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JdbcSchedulerLock} backed by an in-memory H2 database. The kit never
 * creates the table, so the test does, with the DDL {@link JdbcSchedulerLock#verify()} prints.
 * Each holder has its own fixed clock, which makes lease expiry exact.
 */
class JdbcSchedulerLockTest {

    private static final Instant T0 = Instant.parse("2026-11-06T12:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(5);

    private EmbeddedDatabase db;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("schedlock-" + System.nanoTime())
                .build();
        new JdbcTemplate(db).execute("CREATE TABLE " + JdbcSchedulerLock.DEFAULT_TABLE
                + " (LOCK_NAME VARCHAR(200) NOT NULL PRIMARY KEY, LOCKED_BY VARCHAR(255) NOT NULL, "
                + "LOCKED_AT TIMESTAMP WITH TIME ZONE NOT NULL, LOCKED_UNTIL TIMESTAMP WITH TIME ZONE NOT NULL)");
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private JdbcSchedulerLock holder(String owner, Instant now) {
        return new JdbcSchedulerLock(db, JdbcSchedulerLock.DEFAULT_TABLE, Clock.fixed(now, ZoneOffset.UTC), owner);
    }

    @Test
    @DisplayName("acquires a free lock and reports a non-blank owner")
    void acquiresFreeLock() {
        var lock = new JdbcSchedulerLock(db, JdbcSchedulerLock.DEFAULT_TABLE);

        assertThat(lock.tryLock("job", LEASE)).isTrue();
        assertThat(lock.getOwner()).isNotBlank();
        assertThat(lock.getTable()).isEqualTo(JdbcSchedulerLock.DEFAULT_TABLE);
    }

    @Test
    @DisplayName("a second holder cannot acquire a held, unexpired lock")
    void secondHolderBlockedWhileHeld() {
        assertThat(holder("a", T0).tryLock("job", LEASE)).isTrue();
        assertThat(holder("b", T0.plus(LEASE).minusSeconds(1)).tryLock("job", LEASE)).isFalse();
    }

    @Test
    @DisplayName("the holder itself cannot acquire its own lock twice")
    void notReentrant() {
        var lock = holder("a", T0);

        assertThat(lock.tryLock("job", LEASE)).isTrue();
        assertThat(lock.tryLock("job", LEASE)).isFalse();
    }

    @Test
    @DisplayName("an expired lock can be taken over by another holder")
    void expiredLockTakenOver() {
        assertThat(holder("a", T0).tryLock("job", LEASE)).isTrue();
        assertThat(holder("b", T0.plus(LEASE)).tryLock("job", LEASE)).isTrue();
    }

    @Test
    @DisplayName("unlock releases the lock so another holder can acquire it")
    void unlockReleases() {
        var a = holder("a", T0);
        var b = holder("b", T0);

        assertThat(a.tryLock("job", LEASE)).isTrue();
        assertThat(b.tryLock("job", LEASE)).isFalse();

        a.unlock("job");

        assertThat(b.tryLock("job", LEASE)).isTrue();
    }

    @Test
    @DisplayName("unlock by a non-owning holder is a no-op")
    void unlockByNonOwnerIsNoOp() {
        var a = holder("a", T0);
        var b = holder("b", T0);

        assertThat(a.tryLock("job", LEASE)).isTrue();

        // b does not own it; releasing must not free a's hold.
        b.unlock("job");

        assertThat(b.tryLock("job", LEASE)).isFalse();
    }

    @Test
    @DisplayName("renew extends a lock the holder owns")
    void renewExtends() {
        var a = holder("a", T0);

        assertThat(a.tryLock("job", LEASE)).isTrue();
        assertThat(holder("a", T0.plus(LEASE).minusSeconds(1)).renew("job", LEASE)).isTrue();

        assertThat(holder("b", T0.plus(LEASE).plusSeconds(1)).tryLock("job", LEASE)).isFalse();
    }

    @Test
    @DisplayName("renew reports a lock another holder took over")
    void renewAfterTakeover() {
        var a = holder("a", T0);

        assertThat(a.tryLock("job", LEASE)).isTrue();
        assertThat(holder("b", T0.plus(LEASE)).tryLock("job", LEASE)).isTrue();

        assertThat(a.renew("job", LEASE)).isFalse();
    }

    @Test
    @DisplayName("distinct lock names are independent")
    void distinctNamesIndependent() {
        var lock = holder("a", T0);

        assertThat(lock.tryLock("jobA", LEASE)).isTrue();
        assertThat(lock.tryLock("jobB", LEASE)).isTrue();
    }

    @Test
    @DisplayName("verify passes on the table and names the DDL when it is missing")
    void verifyNamesTheMissingTable() {
        holder("a", T0).verify();

        var missing = new JdbcSchedulerLock(db, "NO_SUCH_LOCK_TABLE");

        assertThatThrownBy(missing::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREATE TABLE NO_SUCH_LOCK_TABLE");
        // tryLock reports an unusable database as "not acquired" rather than failing the fire.
        assertThat(missing.tryLock("job", LEASE)).isFalse();
    }
}
