package com.adhar.kit.dapr.aspect;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprPublish;
import com.adhar.kit.dapr.pubsub.DispatchStatus;
import com.adhar.kit.dapr.support.MemoryDapr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DaprPublishAspect} on an in-memory facade, observing what subscribers of
 * the topic receive.
 */
@DisplayName("DaprPublishAspect Tests")
class DaprPublishAspectTest {

    private DaprFacade facade;
    private DaprPublishAspect aspect;
    private SampleService service;
    private final List<String> received = new ArrayList<>();

    static class SampleService {

        @DaprPublish(topic = "order-created")
        public String createOrder(String request) {
            return "order-" + request;
        }

        @DaprPublish(pubsubName = "pubsub", topic = "order-updated", publishReturnValue = false, parameterIndex = 1)
        public void updateOrder(String orderId, String event) {
            // the parameter is published
        }

        @DaprPublish(pubsubName = "pubsub", topic = "order-null")
        public String returnsNull() {
            return null;
        }

        @DaprPublish(pubsubName = "pubsub", topic = "bad-index", publishReturnValue = false, parameterIndex = 5)
        public void badIndex(String a) {
            // invalid index
        }
    }

    @BeforeEach
    void setUp() {
        facade = MemoryDapr.facade();
        aspect = new DaprPublishAspect(facade);
        service = new SampleService();
        for (String topic : List.of("order-created", "order-updated", "order-null")) {
            facade.subscribe(topic, String.class, event -> {
                received.add(event.topic() + ":" + event.data());
                return DispatchStatus.SUCCESS;
            });
        }
    }

    @Test
    void publishesReturnValueAfterExecutionToTheDefaultPubsub() throws Throwable {
        Object result = aspect.applyPublish(new TestJoinPoint(service, "createOrder", "req1"),
                annotation("createOrder", String.class));

        assertThat(result).isEqualTo("order-req1");
        assertThat(received).containsExactly("order-created:order-req1");
    }

    @Test
    void publishesSpecificParameterInsteadOfReturnValue() throws Throwable {
        aspect.applyPublish(new TestJoinPoint(service, "updateOrder", "o-1", "event-payload"),
                annotation("updateOrder", String.class, String.class));

        assertThat(received).containsExactly("order-updated:event-payload");
    }

    @Test
    void nullPayloadIsNotPublished() throws Throwable {
        aspect.applyPublish(new TestJoinPoint(service, "returnsNull"), annotation("returnsNull"));
        assertThat(received).isEmpty();
    }

    @Test
    void outOfBoundsParameterIndexIsRejected() throws NoSuchMethodException {
        assertThatThrownBy(() -> aspect.applyPublish(new TestJoinPoint(service, "badIndex", "a"),
                annotation("badIndex", String.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parameterIndex 5");
    }

    private static DaprPublish annotation(String method, Class<?>... types) throws NoSuchMethodException {
        return SampleService.class.getMethod(method, types).getAnnotation(DaprPublish.class);
    }
}
