package com.adhar.kit.commons.event;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CloudEventPublisherTest {

    /** Records published events to verify default-method behaviour. */
    static class RecordingPublisher implements CloudEventPublisher {
        final List<CloudEvent<?>> published = new CopyOnWriteArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public <T> void publish(CloudEvent<T> event) {
            published.add(event);
            latch.countDown();
        }
    }

    static class SampleEvent extends DomainEvent {
        @Override
        public String getEventType() { return "com.adhar.sample.created"; }
        @Override
        public String getAggregateId() { return "agg-1"; }
    }

    @Test
    void publishStoresEvent() {
        RecordingPublisher p = new RecordingPublisher();
        CloudEvent<String> e = CloudEvent.<String>builder().data("x").build();
        p.publish(e);
        assertEquals(1, p.published.size());
        assertSame(e, p.published.get(0));
    }

    @Test
    void publishToTopicDelegatesToPublish() {
        RecordingPublisher p = new RecordingPublisher();
        CloudEvent<String> e = CloudEvent.<String>builder().data("x").build();
        p.publish("topic", e);
        assertEquals(1, p.published.size());
        assertSame(e, p.published.get(0));
    }

    @Test
    void publishAsyncEventuallyPublishes() throws InterruptedException {
        RecordingPublisher p = new RecordingPublisher();
        CloudEvent<String> e = CloudEvent.<String>builder().data("x").build();
        p.publishAsync(e);
        assertTrue(p.latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, p.published.size());
    }

    @Test
    void publishDomainEventConvertsToCloudEvent() {
        AtomicReference<CloudEvent<?>> captured = new AtomicReference<>();
        CloudEventPublisher p = new CloudEventPublisher() {
            @Override
            public <T> void publish(CloudEvent<T> event) {
                captured.set(event);
            }
        };
        p.publishDomainEvent(new SampleEvent(), URI.create("urn:src"));
        assertNotNull(captured.get());
        assertEquals("com.adhar.sample.created", captured.get().getType());
    }
}
