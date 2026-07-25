package com.adhar.kit.dapr.aspect;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprPublish;
import io.dapr.client.DaprClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprPublishAspect} using a real annotated sample class exercised
 * through a {@link TestJoinPoint} double, and a {@link DaprFacade} backed by a mocked
 * {@link DaprClient}.
 */
@DisplayName("DaprPublishAspect Tests")
class DaprPublishAspectTest {

    private DaprClient client;
    private DaprFacade facade;
    private DaprPublishAspect aspect;
    private SampleService service;

    static class SampleService {

        @DaprPublish(pubsubName = "pubsub", topic = "order-created")
        public String createOrder(String request) {
            return "order-" + request;
        }

        @DaprPublish(pubsubName = "pubsub", topic = "order-updated", publishReturnValue = false, parameterIndex = 1)
        public void updateOrder(String orderId, String event) {
            // return value ignored; parameter at index 1 is published instead
        }

        @DaprPublish(pubsubName = "pubsub", topic = "order-null")
        public String returnsNull() {
            return null;
        }

        @DaprPublish(pubsubName = "pubsub", topic = "bad-index", publishReturnValue = false, parameterIndex = 5)
        public void badIndex(String a) {
        }
    }

    @BeforeEach
    void setUp() {
        client = mock(DaprClient.class);
        facade = new DaprFacade(client);
        aspect = new DaprPublishAspect(facade);
        service = new SampleService();
    }

    @Test
    @DisplayName("publishes the method's return value after it executes")
    void publishesReturnValueAfterExecution() throws Throwable {
        when(client.publishEvent(anyString(), anyString(), any(), anyMap())).thenReturn(Mono.empty());
        TestJoinPoint jp = new TestJoinPoint(service, "createOrder", "req1");

        Object result = aspect.applyPublish(jp, jp.method().getAnnotation(DaprPublish.class));

        assertThat(result).isEqualTo("order-req1");
        assertThat(jp.proceedCount.get()).isEqualTo(1);
        verify(client).publishEvent(eq("pubsub"), eq("order-created"), eq("order-req1"), anyMap());
    }

    @Test
    @DisplayName("publishes a specific parameter instead of the return value when configured")
    void publishesSpecificParameterInsteadOfReturnValue() throws Throwable {
        when(client.publishEvent(anyString(), anyString(), any(), anyMap())).thenReturn(Mono.empty());
        TestJoinPoint jp = new TestJoinPoint(service, "updateOrder", "order1", "event-payload");

        aspect.applyPublish(jp, jp.method().getAnnotation(DaprPublish.class));

        verify(client).publishEvent(eq("pubsub"), eq("order-updated"), eq("event-payload"), anyMap());
    }

    @Test
    @DisplayName("does not publish when the resolved payload is null")
    void skipsPublishWhenPayloadNull() throws Throwable {
        TestJoinPoint jp = new TestJoinPoint(service, "returnsNull");

        Object result = aspect.applyPublish(jp, jp.method().getAnnotation(DaprPublish.class));

        assertThat(result).isNull();
        verify(client, never()).publishEvent(anyString(), anyString(), any(), anyMap());
    }

    @Test
    @DisplayName("throws when parameterIndex is out of bounds")
    void throwsWhenParameterIndexOutOfBounds() {
        TestJoinPoint jp = new TestJoinPoint(service, "badIndex", "a1");

        assertThatThrownBy(() -> aspect.applyPublish(jp, jp.method().getAnnotation(DaprPublish.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jp.proceedCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("constructor rejects a null facade")
    void constructorRejectsNullFacade() {
        assertThatThrownBy(() -> new DaprPublishAspect(null)).isInstanceOf(NullPointerException.class);
    }
}
