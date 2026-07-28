package com.adhar.kit.health.web;

import com.adhar.kit.health.event.HealthEventBroadcaster;
import com.adhar.kit.health.event.HealthTransition;
import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link HealthEventSseController}.
 */
class HealthEventSseControllerTest {

    private static HealthTransition transition() {
        return new HealthTransition("db", Health.Status.UP, Health.Status.DOWN, Instant.now());
    }

    /** Controller variant that yields a supplied emitter so lifecycle wiring can be inspected. */
    static class TestableController extends HealthEventSseController {
        private final SseEmitter emitter;

        TestableController(HealthEventBroadcaster broadcaster, SseEmitter emitter) {
            super(broadcaster, 0);
            this.emitter = emitter;
        }

        @Override
        protected SseEmitter createEmitter() {
            return emitter;
        }
    }

    @Test
    void stream_registersSubscriber_andForwardsTransitions() throws IOException {
        HealthEventBroadcaster broadcaster = new HealthEventBroadcaster();
        SseEmitter emitter = mock(SseEmitter.class);
        TestableController controller = new TestableController(broadcaster, emitter);

        SseEmitter returned = controller.stream();

        assertThat(returned).isSameAs(emitter);
        assertThat(broadcaster.subscriberCount()).isEqualTo(1);

        broadcaster.onTransition(transition());
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void stream_completionCallbackUnsubscribes() {
        HealthEventBroadcaster broadcaster = new HealthEventBroadcaster();
        SseEmitter emitter = mock(SseEmitter.class);
        TestableController controller = new TestableController(broadcaster, emitter);

        controller.stream();
        assertThat(broadcaster.subscriberCount()).isEqualTo(1);

        ArgumentCaptor<Runnable> onCompletion = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onCompletion(onCompletion.capture());
        onCompletion.getValue().run();

        assertThat(broadcaster.subscriberCount()).isZero();
    }

    @Test
    void stream_timeoutCallbackUnsubscribesAndCompletes() {
        HealthEventBroadcaster broadcaster = new HealthEventBroadcaster();
        SseEmitter emitter = mock(SseEmitter.class);
        TestableController controller = new TestableController(broadcaster, emitter);

        controller.stream();

        ArgumentCaptor<Runnable> onTimeout = ArgumentCaptor.forClass(Runnable.class);
        verify(emitter).onTimeout(onTimeout.capture());
        onTimeout.getValue().run();

        assertThat(broadcaster.subscriberCount()).isZero();
        verify(emitter).complete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void stream_errorCallbackUnsubscribes() {
        HealthEventBroadcaster broadcaster = new HealthEventBroadcaster();
        SseEmitter emitter = mock(SseEmitter.class);
        TestableController controller = new TestableController(broadcaster, emitter);

        controller.stream();

        ArgumentCaptor<java.util.function.Consumer<Throwable>> onError =
                ArgumentCaptor.forClass(java.util.function.Consumer.class);
        verify(emitter).onError(onError.capture());
        onError.getValue().accept(new IllegalStateException("client gone"));

        assertThat(broadcaster.subscriberCount()).isZero();
    }

    @Test
    void sendTransition_success_sendsEvent() throws IOException {
        HealthEventBroadcaster broadcaster = new HealthEventBroadcaster();
        HealthEventSseController controller = new HealthEventSseController(broadcaster, 0);
        SseEmitter emitter = mock(SseEmitter.class);

        controller.sendTransition(emitter, transition());

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void sendTransition_ioException_completesWithError() throws IOException {
        HealthEventBroadcaster broadcaster = new HealthEventBroadcaster();
        HealthEventSseController controller = new HealthEventSseController(broadcaster, 0);
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("closed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        controller.sendTransition(emitter, transition());

        verify(emitter).completeWithError(any(IOException.class));
    }
}
