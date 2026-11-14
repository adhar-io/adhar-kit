package com.adhar.kit.batch.runtime;

import org.springframework.boot.ExitCodeGenerator;
import org.springframework.core.Ordered;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The process exit code of a Kubernetes run: the {@link RunOutcome} the runner recorded. Ordered
 * first, so {@code SpringApplication.exit} returns it whatever other generators say; before the
 * runner has finished it answers 0, which lets them speak.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class BatchExitCodeGenerator implements ExitCodeGenerator, Ordered {

    private final AtomicReference<RunOutcome> outcome = new AtomicReference<>();

    /**
     * @param outcome how the run ended
     */
    public void record(RunOutcome outcome) {
        this.outcome.set(outcome);
    }

    /**
     * @return how the run ended, empty while it runs
     */
    public Optional<RunOutcome> outcome() {
        return Optional.ofNullable(outcome.get());
    }

    @Override
    public int getExitCode() {
        RunOutcome recorded = outcome.get();
        return recorded == null ? 0 : recorded.exitCode();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
