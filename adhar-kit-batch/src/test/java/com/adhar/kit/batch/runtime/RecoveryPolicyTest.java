package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.runtime.ExecutionLease.Lease;
import com.adhar.kit.batch.runtime.ExecutionLease.Owner;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.KubernetesLookupException;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.Pod;
import com.adhar.kit.batch.runtime.RecoveryPolicy.Decision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RecoveryPolicy}: every rule that decides between taking a running execution over,
 * waiting for its owner, and leaving it to the pod that runs it (design 0025 §5.9).
 */
class RecoveryPolicyTest {

    private static final Duration STALE_AFTER = Duration.ofMinutes(2);
    private static final Instant NOW = Instant.parse("2026-11-03T17:40:00Z");
    private static final Owner OWNER = new Owner("reporter-old", "uid-old", "worker-2");
    private static final RunContext SELF = new RunContext("reportJob", "reporter", BatchRole.SINGLE,
            RunTrigger.SCHEDULE, "", "reporter-1", "", "reporter-new", "uid-new", "", Optional.empty(),
            ZoneId.of("UTC"), Optional.empty(), "", 2, OptionalInt.empty(), "c");

    private static JobExecution running(Duration sinceLastUpdate) {
        JobExecution execution = new JobExecution(97L, new JobInstance(42L, "reportJob"), new JobParameters());
        execution.setStatus(BatchStatus.STARTED);
        execution.setLastUpdated(LocalDateTime.ofInstant(NOW.minus(sinceLastUpdate), ZoneId.systemDefault()));
        return execution;
    }

    private static ExecutionLease leased(Owner owner, Duration age) {
        ExecutionLease lease = ExecutionLease.none();
        return new ExecutionLease() {
            @Override
            public void acquire(long executionId, Owner owner) {
            }

            @Override
            public boolean heartbeat(long executionId, Owner owner, Instant lastCommit) {
                return lease.heartbeat(executionId, owner, lastCommit);
            }

            @Override
            public void release(long executionId) {
            }

            @Override
            public Optional<Lease> find(long executionId) {
                return owner == null ? Optional.empty()
                        : Optional.of(new Lease(executionId, owner, NOW, NOW.minus(age), null, age));
            }
        };
    }

    /** A lookup answering every pod GET with {@code answer}. */
    private static KubernetesOwnerLookup pods(Function<String, Optional<Pod>> answer) {
        return new KubernetesOwnerLookup(URI.create("http://api"), () -> "t", "ns", null, Duration.ZERO) {
            @Override
            public Optional<Pod> pod(String name) {
                return answer.apply(name);
            }
        };
    }

    private static RecoveryPolicy.Verdict decide(Owner owner, Duration age, KubernetesOwnerLookup lookup) {
        return new RecoveryPolicy(leased(owner, age), lookup, STALE_AFTER, Clock.fixed(NOW, ZoneId.of("UTC")))
                .decide(running(Duration.ofSeconds(10)), SELF);
    }

    private static Optional<Pod> pod(String uid, String phase, boolean terminating) {
        return Optional.of(new Pod(uid, phase, terminating, ""));
    }

    @Test
    @DisplayName("the owner pod running with a fresh lease: a duplicate")
    void ownerRunning() {
        assertThat(decide(OWNER, Duration.ofSeconds(20), pods(name -> pod("uid-old", "Running", false))).decision())
                .isEqualTo(Decision.DUPLICATE);
    }

    @Test
    @DisplayName("the owner pod gone, replaced or ended: recover")
    void ownerGone() {
        Duration fresh = Duration.ofSeconds(20);
        assertThat(decide(OWNER, fresh, pods(name -> Optional.empty())).reason()).contains("is gone");
        assertThat(decide(OWNER, fresh, pods(name -> pod("uid-other", "Running", false))).reason())
                .contains("was replaced");
        assertThat(decide(OWNER, fresh, pods(name -> pod("uid-old", "Failed", false))).reason()).contains("has ended");
        assertThat(decide(OWNER, fresh, pods(name -> pod("uid-old", "Succeeded", true))).decision())
                .isEqualTo(Decision.RECOVER);
        assertThat(decide(new Owner("reporter-new", "uid-before", ""), fresh, pods(name -> {
            throw new AssertionError("no need to ask");
        })).reason()).as("this pod's own previous incarnation").contains("earlier pod named reporter-new");
    }

    @Test
    @DisplayName("a terminating owner with a fresh lease: wait; once its lease is stale: recover")
    void terminatingOwner() {
        assertThat(decide(OWNER, Duration.ofSeconds(20), pods(name -> pod("uid-old", "Running", true))).decision())
                .isEqualTo(Decision.WAIT);
        assertThat(decide(OWNER, Duration.ofMinutes(3), pods(name -> pod("uid-old", "Running", true))).decision())
                .isEqualTo(Decision.RECOVER);
    }

    @Test
    @DisplayName("a stale lease is recovered whatever the pod says; a fresh one stands when the pod cannot be read")
    void byTheLease() {
        assertThat(decide(OWNER, Duration.ofMinutes(3), pods(name -> pod("uid-old", "Running", false))).reason())
                .contains("old (stale after PT2M)");
        KubernetesOwnerLookup failing = pods(name -> {
            throw new KubernetesLookupException("503");
        });
        assertThat(decide(OWNER, Duration.ofSeconds(20), failing).decision()).isEqualTo(Decision.DUPLICATE);
        assertThat(decide(OWNER, Duration.ofMinutes(3), failing).decision()).isEqualTo(Decision.RECOVER);
        assertThat(decide(OWNER, Duration.ofSeconds(20), KubernetesOwnerLookup.unavailable()).decision())
                .as("outside a cluster an empty answer is no evidence").isEqualTo(Decision.DUPLICATE);
        assertThat(decide(OWNER, Duration.ofMinutes(3), KubernetesOwnerLookup.unavailable()).decision())
                .isEqualTo(Decision.RECOVER);
    }

    @Test
    @DisplayName("no lease at all: the execution's last update stands in for the heartbeat")
    void noLease() {
        RecoveryPolicy policy = new RecoveryPolicy(leased(null, Duration.ZERO), KubernetesOwnerLookup.unavailable(),
                STALE_AFTER, Clock.fixed(NOW, ZoneId.of("UTC")));

        assertThat(policy.decide(running(Duration.ofSeconds(30)), SELF).decision()).isEqualTo(Decision.DUPLICATE);
        assertThat(policy.decide(running(Duration.ofMinutes(5)), SELF).decision()).isEqualTo(Decision.RECOVER);
        JobExecution neverUpdated = running(Duration.ZERO);
        neverUpdated.setLastUpdated(null);
        assertThat(policy.decide(neverUpdated, SELF).decision()).as("created just now").isEqualTo(Decision.DUPLICATE);
    }
}
