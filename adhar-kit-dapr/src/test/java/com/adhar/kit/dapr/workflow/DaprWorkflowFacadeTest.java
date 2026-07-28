package com.adhar.kit.dapr.workflow;

import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowStub;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.WorkflowInstanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprWorkflowFacade} backed by a mocked {@link DaprWorkflowClient}.
 */
class DaprWorkflowFacadeTest {

    /** A minimal workflow type - never instantiated, only referenced by class. */
    static class SampleWorkflow implements Workflow {
        @Override
        public WorkflowStub create() {
            return null;
        }
    }

    private DaprWorkflowClient client;
    private DaprWorkflowFacade facade;

    @BeforeEach
    void setUp() {
        client = mock(DaprWorkflowClient.class);
        facade = new DaprWorkflowFacade(client);
    }

    @Test
    void startByClassDelegates() {
        Object input = "payload";
        when(client.scheduleNewWorkflow(eq(SampleWorkflow.class), eq(input))).thenReturn("wf-1");

        assertThat(facade.start(SampleWorkflow.class, input)).isEqualTo("wf-1");
    }

    @Test
    void startByNameDelegates() {
        Object input = "payload";
        when(client.scheduleNewWorkflow(eq("MyWorkflow"), eq(input))).thenReturn("wf-2");

        assertThat(facade.start("MyWorkflow", input)).isEqualTo("wf-2");
    }

    @Test
    void startByNameRejectsBlank() {
        assertThatThrownBy(() -> facade.start("  ", "x"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminateDelegates() {
        facade.terminate("wf-1", "output");

        verify(client).terminateWorkflow("wf-1", "output");
    }

    @Test
    void terminateRejectsBlankInstanceId() {
        assertThatThrownBy(() -> facade.terminate("", "x"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusDelegates() {
        WorkflowInstanceStatus status = mock(WorkflowInstanceStatus.class);
        when(client.getInstanceState("wf-1", true)).thenReturn(status);

        assertThat(facade.status("wf-1")).isSameAs(status);
    }

    @Test
    void raiseEventDelegates() {
        facade.raiseEvent("wf-1", "approve", "data");

        verify(client).raiseEvent("wf-1", "approve", "data");
    }

    @Test
    void raiseEventRejectsBlankEventName() {
        assertThatThrownBy(() -> facade.raiseEvent("wf-1", " ", "data"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void purgeDelegates() {
        when(client.purgeInstance("wf-1")).thenReturn(true);

        assertThat(facade.purge("wf-1")).isTrue();
    }

    @Test
    void nullClientRejected() {
        assertThatThrownBy(() -> new DaprWorkflowFacade((DaprWorkflowClient) null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void closeDelegates() throws InterruptedException {
        facade.close();

        verify(client).close();
    }
}
