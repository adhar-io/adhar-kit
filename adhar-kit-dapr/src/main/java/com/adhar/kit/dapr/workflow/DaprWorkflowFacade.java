package com.adhar.kit.dapr.workflow;

import io.dapr.workflows.Workflow;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.WorkflowInstanceStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Thin facade over {@link DaprWorkflowClient} exposing the common workflow lifecycle
 * operations: start, terminate, status, and raise-event.
 *
 * <p>Only referenced from workflow-gated auto-configuration, so the optional
 * {@code dapr-sdk-workflows} dependency is never required by consumers that don't use
 * workflows.</p>
 *
 * <pre>{@code
 * DaprWorkflowFacade workflows = new DaprWorkflowFacade();
 * String id = workflows.start(OrderWorkflow.class, order);
 * workflows.raiseEvent(id, "approval", approval);
 * WorkflowInstanceStatus status = workflows.status(id);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DaprWorkflowFacade implements AutoCloseable {

    private final DaprWorkflowClient client;

    /**
     * Creates a facade backed by a default {@link DaprWorkflowClient} (connects to the local
     * sidecar).
     */
    public DaprWorkflowFacade() {
        this(new DaprWorkflowClient());
    }

    public DaprWorkflowFacade(DaprWorkflowClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /**
     * Schedules a new workflow instance of the given workflow class.
     *
     * @return the generated workflow instance id
     */
    public <T extends Workflow> String start(Class<T> workflowClass, Object input) {
        Objects.requireNonNull(workflowClass, "workflowClass must not be null");
        String instanceId = client.scheduleNewWorkflow(workflowClass, input);
        log.debug("Started workflow: class={}, instanceId={}", workflowClass.getName(), instanceId);
        return instanceId;
    }

    /**
     * Schedules a new workflow instance by registered workflow name.
     *
     * @return the generated workflow instance id
     */
    public String start(String workflowName, Object input) {
        if (workflowName == null || workflowName.isBlank()) {
            throw new IllegalArgumentException("workflowName must not be blank");
        }
        String instanceId = client.scheduleNewWorkflow(workflowName, input);
        log.debug("Started workflow: name={}, instanceId={}", workflowName, instanceId);
        return instanceId;
    }

    /**
     * Terminates a running workflow instance, setting the given output.
     */
    public void terminate(String instanceId, Object output) {
        requireInstanceId(instanceId);
        client.terminateWorkflow(instanceId, output);
        log.debug("Terminated workflow: instanceId={}", instanceId);
    }

    /**
     * Fetches the current status of a workflow instance (including inputs/outputs).
     */
    public WorkflowInstanceStatus status(String instanceId) {
        requireInstanceId(instanceId);
        return client.getInstanceState(instanceId, true);
    }

    /**
     * Raises an external event on a running workflow instance.
     */
    public void raiseEvent(String instanceId, String eventName, Object eventData) {
        requireInstanceId(instanceId);
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("eventName must not be blank");
        }
        client.raiseEvent(instanceId, eventName, eventData);
        log.debug("Raised event '{}' on workflow: instanceId={}", eventName, instanceId);
    }

    /**
     * Purges a completed/terminated workflow instance's history.
     *
     * @return {@code true} if the instance was found and purged
     */
    public boolean purge(String instanceId) {
        requireInstanceId(instanceId);
        return client.purgeInstance(instanceId);
    }

    private static void requireInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId must not be blank");
        }
    }

    @Override
    public void close() throws InterruptedException {
        client.close();
    }
}
