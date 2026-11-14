package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.runtime.ExecutionLease.Lease;
import com.adhar.kit.batch.runtime.ExecutionLease.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JdbcExecutionLease} on H2 with the kit's table: acquire, renew, release, ownership, and an
 * age measured by the database's clock. {@code JdbcExecutionLeaseTestContainerTest} repeats it on
 * PostgreSQL 18 in the kit's schema.
 */
class JdbcExecutionLeaseTest {

    static final Owner SELF = new Owner("reporter-abc12", "uid-self", "worker-1");

    private JdbcTemplate jdbc;
    private JdbcExecutionLease lease;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:lease-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("adhar-batch-test/h2-schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        lease = new JdbcExecutionLease(jdbc, "ADHAR_BATCH_EXECUTION_LEASE");
    }

    static void contract(ExecutionLease lease, long id, Runnable backdateFiveMinutes) {
        lease.acquire(id, SELF);
        Lease acquired = lease.find(id).orElseThrow();
        assertThat(acquired.owner()).isEqualTo(SELF);
        assertThat(acquired.lastCommitAt()).isNull();
        assertThat(acquired.age()).isLessThan(Duration.ofSeconds(5));

        Instant commit = Instant.parse("2026-11-03T17:31:00Z");
        assertThat(lease.heartbeat(id, SELF, commit)).isTrue();
        assertThat(lease.find(id).orElseThrow().lastCommitAt()).isEqualTo(commit);
        assertThat(lease.heartbeat(id, SELF, null)).as("no commit since: the last one stays").isTrue();
        assertThat(lease.find(id).orElseThrow().lastCommitAt()).isEqualTo(commit);

        assertThat(lease.heartbeat(id, new Owner("reporter-abc12", "uid-later", ""), null))
                .as("the same name with another uid is another pod").isFalse();
        assertThat(lease.heartbeat(id, new Owner("someone-else", "uid-self", ""), null)).isFalse();

        backdateFiveMinutes.run();
        assertThat(lease.find(id).orElseThrow().age()).isGreaterThanOrEqualTo(Duration.ofMinutes(5).minusSeconds(1));
        assertThat(lease.heartbeat(id, SELF, null)).isTrue();
        assertThat(lease.find(id).orElseThrow().age()).isLessThan(Duration.ofSeconds(5));

        lease.release(id);
        assertThat(lease.find(id)).isEmpty();
        assertThat(lease.heartbeat(id, SELF, null)).as("a released lease cannot be renewed").isFalse();
    }

    @Test
    @DisplayName("acquire, renew (with the last commit), lose to another owner, age by the database clock, release")
    void lifecycle() {
        contract(lease, 97, () -> jdbc.update(
                "UPDATE ADHAR_BATCH_EXECUTION_LEASE SET HEARTBEAT_AT = DATEADD('MINUTE', -5, CURRENT_TIMESTAMP)"));
    }

    @Test
    @DisplayName("one execution has one lease")
    void oneLeasePerExecution() {
        lease.acquire(98, SELF);

        assertThatThrownBy(() -> lease.acquire(98, new Owner("other", "u", "n")))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(lease.find(99)).isEmpty();
    }

    @Test
    @DisplayName("a pod without a uid still owns its lease")
    void noUid() {
        Owner bare = new Owner("laptop", "", "");
        lease.acquire(1, bare);

        assertThat(lease.find(1).orElseThrow().owner()).isEqualTo(bare);
        assertThat(lease.heartbeat(1, bare, Instant.now().truncatedTo(ChronoUnit.MILLIS))).isTrue();
        assertThat(ExecutionLease.none().find(1)).isEmpty();
        assertThat(ExecutionLease.none().heartbeat(1, bare, null)).isTrue();
    }
}
