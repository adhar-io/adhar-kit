package com.adhar.kit.batch.lock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JdbcSchedulerLock} backed by an in-memory H2 database.
 */
class JdbcSchedulerLockTest {

    private EmbeddedDatabase db;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("schedlock-" + System.nanoTime())
                .build();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    @DisplayName("acquires a free lock and reports a non-null instance id")
    void acquiresFreeLock() {
        var lock = new JdbcSchedulerLock(db);

        assertThat(lock.tryLock("job", Duration.ofMinutes(5))).isTrue();
        assertThat(lock.getInstanceId()).isNotBlank();
    }

    @Test
    @DisplayName("a second instance cannot acquire a held, unexpired lock")
    void secondInstanceBlockedWhileHeld() {
        var lock1 = new JdbcSchedulerLock(db);
        var lock2 = new JdbcSchedulerLock(db);

        assertThat(lock1.tryLock("job", Duration.ofMinutes(5))).isTrue();
        assertThat(lock2.tryLock("job", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    @DisplayName("an expired lock can be taken over by another instance")
    void expiredLockTakenOver() {
        var lock1 = new JdbcSchedulerLock(db);
        var lock2 = new JdbcSchedulerLock(db);

        // Zero TTL means the lock is already expired at insert time.
        assertThat(lock1.tryLock("job", Duration.ZERO)).isTrue();
        assertThat(lock2.tryLock("job", Duration.ofMinutes(5))).isTrue();
    }

    @Test
    @DisplayName("unlock releases the lock so another instance can acquire it")
    void unlockReleases() {
        var lock1 = new JdbcSchedulerLock(db);
        var lock2 = new JdbcSchedulerLock(db);

        assertThat(lock1.tryLock("job", Duration.ofMinutes(5))).isTrue();
        assertThat(lock2.tryLock("job", Duration.ofMinutes(5))).isFalse();

        lock1.unlock("job");

        assertThat(lock2.tryLock("job", Duration.ofMinutes(5))).isTrue();
    }

    @Test
    @DisplayName("unlock by a non-owning instance is a no-op")
    void unlockByNonOwnerIsNoOp() {
        var lock1 = new JdbcSchedulerLock(db);
        var lock2 = new JdbcSchedulerLock(db);

        assertThat(lock1.tryLock("job", Duration.ofMinutes(5))).isTrue();

        // lock2 does not own it; releasing must not free lock1's hold.
        lock2.unlock("job");

        assertThat(lock2.tryLock("job", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    @DisplayName("distinct lock names are independent")
    void distinctNamesIndependent() {
        var lock = new JdbcSchedulerLock(db);

        assertThat(lock.tryLock("jobA", Duration.ofMinutes(5))).isTrue();
        assertThat(lock.tryLock("jobB", Duration.ofMinutes(5))).isTrue();
    }

    @Test
    @DisplayName("constructing over an existing table is idempotent")
    void tableInitIdempotent() {
        new JdbcSchedulerLock(db);
        var second = new JdbcSchedulerLock(db, JdbcSchedulerLock.DEFAULT_TABLE);

        assertThat(second.tryLock("job", Duration.ofMinutes(5))).isTrue();
    }
}
