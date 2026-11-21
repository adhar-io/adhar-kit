package com.adhar.kit.batch.fault;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemStream;
import org.springframework.batch.infrastructure.item.ItemWriter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The processor and writer of every step that dead-letters its skipped items
 * ({@link FaultTolerance#deadLetter()}), by step name: what the {@code dlq-replay} ops command sends
 * a rejected item through again (design 0025 A3.6). {@code AdharSteps} registers each such step as
 * it builds it, so an application declares nothing for its dead letters to be replayable.
 *
 * <p>Some steps cannot be replayed outside their own run, and are registered with the reason:</p>
 * <ul>
 *   <li>a writer that is an {@link ItemStream} keeps state between chunks (a file's position, an
 *       open multipart upload); opening it anew for a replay would start a new output over the
 *       old one;</li>
 *   <li>a writer set through {@code customize(...)} is not known to the kit;</li>
 *   <li>two steps of the same name with different processors or writers (one per job) cannot be
 *       told apart by the step name a dead letter carries.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class DeadLetterReplayRegistry {

    private final Map<String, Route> routes = new ConcurrentHashMap<>();

    /**
     * @param stepName  the step's name
     * @param processor its processor, {@code null} when it has none
     * @param writer    its writer, {@code null} when the kit was not given it
     */
    public void register(String stepName, ItemProcessor<?, ?> processor, ItemWriter<?> writer) {
        Route route = writer == null
                ? Route.refused(stepName, "its writer was set through customize(...), which the kit cannot replay into")
                : writer instanceof ItemStream
                ? Route.refused(stepName, "its writer " + writer.getClass().getName() + " is an ItemStream, whose "
                + "output a replay outside the step would start anew")
                : new Route(stepName, processor, writer, null);
        routes.merge(stepName, route, (existing, added) -> existing.processor == added.processor
                && existing.writer == added.writer && existing.problem == null && added.problem == null
                ? existing : Route.refused(stepName, "more than one step is named [" + stepName + "], and a dead "
                + "letter names only the step"));
    }

    /**
     * @param stepName the step name a dead letter carries; a partition's name
     *                 ({@code workerStep:partition3}) finds its step ({@code workerStep})
     * @return the route, when that step dead-letters in this application
     */
    public Optional<Route> find(String stepName) {
        Route exact = routes.get(stepName);
        if (exact != null) {
            return Optional.of(exact);
        }
        int colon = stepName.indexOf(':');
        return colon > 0 ? Optional.ofNullable(routes.get(stepName.substring(0, colon))) : Optional.empty();
    }

    /**
     * Where a step's rejected items go again.
     *
     * @param stepName  the step
     * @param processor its processor, {@code null} when it has none
     * @param writer    its writer, {@code null} when {@code problem} is set
     * @param problem   why its items cannot be replayed, {@code null} when they can
     */
    public record Route(String stepName, ItemProcessor<?, ?> processor, ItemWriter<?> writer, String problem) {

        static Route refused(String stepName, String problem) {
            return new Route(stepName, null, null, problem);
        }
    }
}
