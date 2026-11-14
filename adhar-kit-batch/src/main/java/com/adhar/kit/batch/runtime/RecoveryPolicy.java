package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.runtime.ExecutionLease.Lease;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.KubernetesLookupException;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.Pod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Decides what a run does when the last execution of its identity is still STARTING, STARTED or
 * STOPPING (design 0025 §5.5, §5.9): take it over, wait for its owner to finish terminating, or
 * leave it alone as a duplicate.
 *
 * <ul>
 *   <li>{@link Decision#RECOVER} when the owner pod is gone (404), replaced (another uid under
 *       its name, as a Volcano restart or this very pod's previous incarnation), or ended; or
 *       when the lease is older than {@code stale-after}, whatever the pod says.</li>
 *   <li>{@link Decision#WAIT} while the owner pod is terminating with a fresh lease: it is
 *       recording STOPPED, and recovering under it would race that write.</li>
 *   <li>{@link Decision#DUPLICATE} otherwise: another pod is running this identity.</li>
 * </ul>
 *
 * <p>With no lease at all (the owner died before writing it, or it was started outside a
 * Kubernetes run), the execution's last update stands in for the heartbeat. An execution that has
 * already ended FAILED or STOPPED with a step still marked running, and no lease, is recovered at
 * once: its owner released the lease when its job ended, so no process of the run holds it, and the
 * step left running is a remote partition the manager could not fence (or one that died with its
 * pod). Calling it a duplicate would exit 0, which ends a gang (CompleteJob) or an Indexed Job (its
 * success policy) as a success for a run that failed.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class RecoveryPolicy {

    private final ExecutionLease lease;
    private final KubernetesOwnerLookup lookup;
    private final Duration staleAfter;
    private final Clock clock;

    /**
     * @param lease      the leases
     * @param lookup     reads the owner pod
     * @param staleAfter {@code adhar.batch.recovery.stale-after}
     * @param clock      the clock the execution's own timestamps are compared with
     */
    public RecoveryPolicy(ExecutionLease lease, KubernetesOwnerLookup lookup, Duration staleAfter, Clock clock) {
        this.lease = lease;
        this.lookup = lookup;
        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    /**
     * @param running the last execution of the identity, not yet ended, or ended with a step still running
     * @param self    this run
     * @return what to do, and why
     */
    public Verdict decide(JobExecution running, RunContext self) {
        Optional<Lease> found = lease.find(running.getId());
        if (found.isEmpty() && !running.getStatus().isRunning()) {
            return new Verdict(Decision.RECOVER, "it ended " + running.getStatus() + " with a step still running "
                    + "and nobody holds its lease");
        }
        if (found.isEmpty()) {
            Duration idle = idle(running);
            return idle.compareTo(staleAfter) > 0
                    ? new Verdict(Decision.RECOVER, "it has no lease and has not been updated for " + idle)
                    : new Verdict(Decision.DUPLICATE, "it started " + idle + " ago and has no lease yet");
        }
        Lease owner = found.get();
        if (owner.owner().pod().equals(self.podName()) && !owner.owner().uid().equals(self.podUid())) {
            return new Verdict(Decision.RECOVER, "its owner was an earlier pod named " + self.podName());
        }
        Optional<Pod> pod = Optional.empty();
        boolean asked = false;
        try {
            pod = lookup.pod(owner.owner().pod());
            asked = lookup.isAvailable();
        } catch (KubernetesLookupException ex) {
            log.warn("Could not read the owner pod {}; deciding by its lease alone: {}", owner.owner().pod(),
                    ex.getMessage());
        }
        boolean stale = owner.age().compareTo(staleAfter) > 0;
        if (asked && pod.isEmpty()) {
            return new Verdict(Decision.RECOVER, "its owner pod " + owner.owner().pod() + " is gone");
        }
        if (pod.isPresent()) {
            Pod current = pod.get();
            if (!owner.owner().uid().isEmpty() && !current.uid().isEmpty() && !owner.owner().uid().equals(current.uid())) {
                return new Verdict(Decision.RECOVER, "its owner pod " + owner.owner().pod() + " was replaced");
            }
            if (current.isTerminal()) {
                return new Verdict(Decision.RECOVER, "its owner pod " + owner.owner().pod() + " has ended ("
                        + current.phase() + ")");
            }
            if (current.terminating() && !stale) {
                return new Verdict(Decision.WAIT, "its owner pod " + owner.owner().pod() + " is terminating");
            }
        }
        return stale ? new Verdict(Decision.RECOVER, "its lease is " + owner.age() + " old (stale after "
                + staleAfter + ")")
                : new Verdict(Decision.DUPLICATE, "pod " + owner.owner().pod() + " is running it (lease renewed "
                + owner.age() + " ago)");
    }

    private Duration idle(JobExecution execution) {
        LocalDateTime last = execution.getLastUpdated() != null ? execution.getLastUpdated()
                : execution.getCreateTime();
        Duration idle = Duration.between(last.atZone(ZoneId.systemDefault()).toInstant(), clock.instant());
        return idle.isNegative() ? Duration.ZERO : idle;
    }

    /**
     * What to do with a running execution.
     */
    public enum Decision {
        /** Recover it (mark it FAILED) and restart the identity. */
        RECOVER,
        /** Poll again: its owner is terminating. */
        WAIT,
        /** Leave it: another pod is running it. */
        DUPLICATE
    }

    /**
     * @param decision what to do
     * @param reason   why, for the log and the event
     */
    public record Verdict(Decision decision, String reason) {
    }
}
