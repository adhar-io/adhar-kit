package com.adhar.kit.dapr.workflow;

import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.observation.DaprObservations;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.WorkflowInstanceStatus;
import io.dapr.workflows.client.WorkflowState;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Starts and manages workflow instances (the client side of Dapr Workflow): start, get, raise an
 * event, pause, resume, wait for start or completion, terminate and purge, with the same names as
 * the other adhar-kit languages.
 *
 * <p>Workflows and activities themselves are written with the Dapr Java SDK
 * ({@link Workflow}, {@code WorkflowActivity}); declare them as Spring beans and the kit registers
 * them with the workflow runtime and starts it. Workflow authoring is Java-only: the TypeScript,
 * Go and Python kits can start and drive these workflows over HTTP but not host them.</p>
 *
 * <pre>{@code
 * String id = workflows.start(OrderWorkflow.class, order);
 * workflows.raiseEvent(id, "approval", approval);
 * WorkflowState state = workflows.waitForCompletion(id, Duration.ofMinutes(5));
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DaprWorkflowFacade implements AutoCloseable {

    private final DaprWorkflowClient client;
    private final DaprObservations observations;

    /**
     * Creates a facade backed by a default {@link DaprWorkflowClient} (connects to the local
     * sidecar).
     */
    public DaprWorkflowFacade() {
        this(new DaprWorkflowClient());
    }

    /**
     * Creates a facade on the kit's sidecar connection (endpoint and API token).
     *
     * @param connection the resolved connection
     */
    public DaprWorkflowFacade(DaprConnectionDetails connection) {
        this(new DaprWorkflowClient(connection.sdkProperties()), DaprObservations.NOOP);
    }

    public DaprWorkflowFacade(DaprWorkflowClient client) {
        this(client, DaprObservations.NOOP);
    }

    public DaprWorkflowFacade(DaprWorkflowClient client, DaprObservations observations) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.observations = observations != null ? observations : DaprObservations.NOOP;
    }

    /**
     * Schedules a new workflow instance of the given workflow class.
     *
     * @return the generated workflow instance id
     */
    public <T extends Workflow> String start(Class<T> workflowClass, Object input) {
        Objects.requireNonNull(workflowClass, "workflowClass must not be null");
        String instanceId = observe("start", () -> client.scheduleNewWorkflow(workflowClass, input));
        log.debug("Started workflow: class={}, instanceId={}", workflowClass.getName(), instanceId);
        return instanceId;
    }

    /**
     * Schedules a new workflow instance with a chosen instance id (idempotent starts).
     *
     * @return the instance id
     */
    public <T extends Workflow> String start(Class<T> workflowClass, Object input, String instanceId) {
        Objects.requireNonNull(workflowClass, "workflowClass must not be null");
        return observe("start", () -> client.scheduleNewWorkflow(workflowClass, input, instanceId));
    }

    /**
     * Schedules a new workflow instance by registered workflow name.
     *
     * @return the generated workflow instance id
     */
    public String start(String workflowName, Object input) {
        requireName(workflowName, "workflowName");
        String instanceId = observe("start", () -> client.scheduleNewWorkflow(workflowName, input));
        log.debug("Started workflow: name={}, instanceId={}", workflowName, instanceId);
        return instanceId;
    }

    /**
     * Schedules a new workflow instance by name with a chosen instance id.
     *
     * @return the instance id
     */
    public String start(String workflowName, Object input, String instanceId) {
        requireName(workflowName, "workflowName");
        return observe("start", () -> client.scheduleNewWorkflow(workflowName, input, instanceId));
    }

    /**
     * The state of an instance (status, input, output, failure), or {@code null} when unknown.
     */
    public WorkflowState get(String instanceId) {
        requireName(instanceId, "instanceId");
        return observe("get", () -> client.getWorkflowState(instanceId, true));
    }

    /**
     * Fetches the current status of a workflow instance (including inputs/outputs).
     *
     * @deprecated the SDK removes {@link WorkflowInstanceStatus}; use {@link #get(String)}
     */
    @Deprecated(since = "0.2.0")
    @SuppressWarnings("removal")
    public WorkflowInstanceStatus status(String instanceId) {
        requireName(instanceId, "instanceId");
        return client.getInstanceState(instanceId, true);
    }

    /**
     * Raises an external event on a running workflow instance.
     */
    public void raiseEvent(String instanceId, String eventName, Object eventData) {
        requireName(instanceId, "instanceId");
        requireName(eventName, "eventName");
        observe("raiseEvent", () -> {
            client.raiseEvent(instanceId, eventName, eventData);
            return null;
        });
        log.debug("Raised event '{}' on workflow: instanceId={}", eventName, instanceId);
    }

    /** Suspends a running instance. */
    public void pause(String instanceId, String reason) {
        requireName(instanceId, "instanceId");
        observe("pause", () -> {
            client.suspendWorkflow(instanceId, reason);
            return null;
        });
    }

    /** Resumes a suspended instance. */
    public void resume(String instanceId, String reason) {
        requireName(instanceId, "instanceId");
        observe("resume", () -> {
            client.resumeWorkflow(instanceId, reason);
            return null;
        });
    }

    /**
     * Waits until the instance has started.
     *
     * @throws TimeoutException when it has not started within {@code timeout}
     */
    public WorkflowState waitForStart(String instanceId, Duration timeout) throws TimeoutException {
        requireName(instanceId, "instanceId");
        return client.waitForWorkflowStart(instanceId, timeout, true);
    }

    /**
     * Waits until the instance completes, fails or is terminated.
     *
     * @throws TimeoutException when it has not finished within {@code timeout}
     */
    public WorkflowState waitForCompletion(String instanceId, Duration timeout) throws TimeoutException {
        requireName(instanceId, "instanceId");
        return client.waitForWorkflowCompletion(instanceId, timeout, true);
    }

    /**
     * Terminates a running workflow instance, setting the given output.
     */
    public void terminate(String instanceId, Object output) {
        requireName(instanceId, "instanceId");
        observe("terminate", () -> {
            client.terminateWorkflow(instanceId, output);
            return null;
        });
        log.debug("Terminated workflow: instanceId={}", instanceId);
    }

    /**
     * Purges a completed/terminated workflow instance's history.
     *
     * @return {@code true} if the instance was found and purged
     */
    public boolean purge(String instanceId) {
        requireName(instanceId, "instanceId");
        return observe("purge", () -> client.purgeWorkflow(instanceId));
    }

    /** The SDK client, for calls the facade does not wrap. */
    public DaprWorkflowClient client() {
        return client;
    }

    private <T> T observe(String operation, Supplier<T> call) {
        return observations.observe("workflow", operation, "dapr", call);
    }

    private static void requireName(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    @Override
    public void close() throws InterruptedException {
        client.close();
    }
}
