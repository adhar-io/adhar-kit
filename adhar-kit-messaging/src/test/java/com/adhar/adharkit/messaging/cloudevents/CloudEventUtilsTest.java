package com.adhar.adharkit.messaging.cloudevents;

import com.adhar.kit.messaging.cloudevents.CloudEventUtils;
import com.adhar.kit.messaging.core.Message;
import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CloudEventUtils} and its fluent {@code Builder}.
 */
class CloudEventUtilsTest {

    @Test
    void builderCreatesFullyPopulatedEvent() {
        Instant time = Instant.parse("2024-01-01T00:00:00Z");
        CloudEvent event = CloudEventUtils.builder()
                .id("evt-1")
                .source("https://adhar.example.com/orders")
                .type("com.adhar.order.created")
                .subject("order-42")
                .dataContentType("application/json")
                .dataSchema("https://adhar.example.com/schema/order.json")
                .data("{\"id\":42}")
                .time(time)
                .extension("tenant", "acme")
                .extensions(Map.of("region", "us-east"))
                .build();

        assertThat(event.getId()).isEqualTo("evt-1");
        assertThat(event.getSource()).isEqualTo(URI.create("https://adhar.example.com/orders"));
        assertThat(event.getType()).isEqualTo("com.adhar.order.created");
        assertThat(event.getSubject()).isEqualTo("order-42");
        assertThat(event.getDataContentType()).isEqualTo("application/json");
        assertThat(event.getDataSchema()).isEqualTo(URI.create("https://adhar.example.com/schema/order.json"));
        assertThat(event.getData()).isNotNull();
        assertThat(event.getExtension("tenant")).isEqualTo("acme");
        assertThat(event.getExtension("region")).isEqualTo("us-east");
    }

    @Test
    void builderGeneratesIdAndTimeByDefault() {
        CloudEvent event = CloudEventUtils.builder()
                .source(URI.create("urn:test"))
                .type("t")
                .build();

        assertThat(event.getId()).isNotBlank();
        assertThat(event.getTime()).isNotNull();
    }

    @Test
    void builderAcceptsUriOverloads() {
        CloudEvent event = CloudEventUtils.builder()
                .source(URI.create("urn:src"))
                .type("t")
                .dataSchema(URI.create("urn:schema"))
                .build();

        assertThat(event.getSource()).isEqualTo(URI.create("urn:src"));
        assertThat(event.getDataSchema()).isEqualTo(URI.create("urn:schema"));
    }

    @Test
    void buildRequiresSource() {
        CloudEventUtils.Builder builder = CloudEventUtils.builder().type("t");
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source is required");
    }

    @Test
    void buildRequiresType() {
        CloudEventUtils.Builder builder = CloudEventUtils.builder().source("urn:src");
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("type is required");
    }

    @Test
    void nullExtensionValueIsSkipped() {
        CloudEvent event = CloudEventUtils.builder()
                .source("urn:src")
                .type("t")
                .extension("present", "yes")
                .extension("absent", null)
                .build();

        assertThat(event.getExtension("present")).isEqualTo("yes");
        assertThat(event.getExtension("absent")).isNull();
    }

    @Test
    void isTypeMatchesAndGuardsNull() {
        CloudEvent event = CloudEventUtils.builder().source("urn:src").type("the.type").build();
        assertThat(CloudEventUtils.isType(event, "the.type")).isTrue();
        assertThat(CloudEventUtils.isType(event, "other")).isFalse();
        assertThat(CloudEventUtils.isType(null, "the.type")).isFalse();
    }

    @Test
    void isSourceMatchesAndGuardsNull() {
        URI source = URI.create("urn:the-source");
        CloudEvent event = CloudEventUtils.builder().source(source).type("t").build();
        assertThat(CloudEventUtils.isSource(event, source)).isTrue();
        assertThat(CloudEventUtils.isSource(event, URI.create("urn:other"))).isFalse();
        assertThat(CloudEventUtils.isSource(null, source)).isFalse();
    }

    @Test
    void fromMessageAndToMessageDelegateToAdapter() {
        Message<String> message = Message.<String>builder()
                .payload("hello")
                .destination("orders")
                .header("header1", "value1")
                .build();

        CloudEvent event = CloudEventUtils.fromMessage(message);
        assertThat(event).isNotNull();

        Message<String> roundTripped = CloudEventUtils.toMessage(event, String.class);
        assertThat(roundTripped).isNotNull();
    }
}
