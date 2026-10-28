package com.adhar.kit.dapr.workflow;

import com.adhar.kit.dapr.DaprConnectionDetails;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.runtime.WorkflowRuntime;
import io.dapr.workflows.runtime.WorkflowRuntimeBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.ClassUtils;

import java.util.Map;
import java.util.Objects;

/**
 * Hosts the application's workflows: every {@link Workflow} and {@link WorkflowActivity} bean is
 * registered with the Dapr workflow runtime under its class's canonical name (the name
 * {@code DaprWorkflowFacade.start(SomeWorkflow.class, ...)} and {@code ctx.callActivity(...)}
 * use), and the runtime starts once the sidecar is ready and stops with the application.
 *
 * <p>Beans are registered as instances, so they can use injected collaborators; the runtime
 * replays workflows, so a workflow bean must keep no per-instance state of its own.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
public class DaprWorkflowRuntimeLifecycle implements SmartLifecycle {

    private final ListableBeanFactory beanFactory;
    private final DaprConnectionDetails connection;
    private volatile WorkflowRuntime runtime;
    private volatile boolean running;

    public DaprWorkflowRuntimeLifecycle(ListableBeanFactory beanFactory, DaprConnectionDetails connection) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory must not be null");
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
    }

    @Override
    public void start() {
        running = true;
        Map<String, Workflow> workflows = beanFactory.getBeansOfType(Workflow.class);
        Map<String, WorkflowActivity> activities = beanFactory.getBeansOfType(WorkflowActivity.class);
        if (workflows.isEmpty() && activities.isEmpty()) {
            return;
        }
        if (connection.isMemory()) {
            log.warn("{} workflow(s) and {} activit(ies) found, but Dapr runs in memory mode: workflows need a "
                    + "sidecar and will not run", workflows.size(), activities.size());
            return;
        }
        WorkflowRuntimeBuilder builder = new WorkflowRuntimeBuilder(connection.sdkProperties());
        workflows.values().forEach(workflow -> builder.registerWorkflow(name(workflow), workflow, null, null));
        activities.values().forEach(activity -> builder.registerActivity(name(activity), activity));
        runtime = builder.build();
        runtime.start(false);
        log.info("Dapr workflow runtime started with {} workflow(s) and {} activit(ies)", workflows.size(),
                activities.size());
    }

    @Override
    public void stop() {
        running = false;
        WorkflowRuntime current = runtime;
        runtime = null;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException e) {
                log.warn("Stopping the workflow runtime failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** After the sidecar wait, before the web server. */
    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE - 1536;
    }

    static String name(Object bean) {
        return ClassUtils.getUserClass(bean).getCanonicalName();
    }
}
