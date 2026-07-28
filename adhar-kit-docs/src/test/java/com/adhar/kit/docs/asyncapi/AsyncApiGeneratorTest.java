package com.adhar.kit.docs.asyncapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncApiGeneratorTest {

    private final AsyncApiGenerator generator = new AsyncApiGenerator();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    private AsyncApiGenerator.ChannelDefinition sendChannel() {
        return new AsyncApiGenerator.ChannelDefinition(
                "orderCreated", "orders.created", "Emitted when an order is created",
                AsyncApiGenerator.Action.SEND, "OrderCreated", "object");
    }

    @Test
    void generatesAsyncApi30Header() {
        JsonNode root = generator.generate("Orders", "1.0.0", "desc",
                List.of(sendChannel())).getRoot();
        assertThat(root.path("asyncapi").asText()).isEqualTo("3.0.0");
        assertThat(root.path("info").path("title").asText()).isEqualTo("Orders");
        assertThat(root.path("info").path("version").asText()).isEqualTo("1.0.0");
        assertThat(root.path("info").path("description").asText()).isEqualTo("desc");
    }

    @Test
    void generatesChannelWithAddressAndMessageRef() {
        JsonNode root = generator.generate("Orders", "1.0.0", null,
                List.of(sendChannel())).getRoot();
        JsonNode channel = root.path("channels").path("orderCreated");
        assertThat(channel.path("address").asText()).isEqualTo("orders.created");
        assertThat(channel.path("description").asText()).contains("order is created");
        assertThat(channel.path("messages").path("OrderCreated").path("$ref").asText())
                .isEqualTo("#/components/messages/OrderCreated");
    }

    @Test
    void generatesComponentMessageWithPayload() {
        JsonNode root = generator.generate("Orders", "1.0.0", null,
                List.of(sendChannel())).getRoot();
        JsonNode message = root.path("components").path("messages").path("OrderCreated");
        assertThat(message.path("name").asText()).isEqualTo("OrderCreated");
        assertThat(message.path("payload").path("type").asText()).isEqualTo("object");
    }

    @Test
    void sendActionProducesSendOperationOnly() {
        JsonNode ops = generator.generate("Orders", "1.0.0", null,
                List.of(sendChannel())).getRoot().path("operations");
        assertThat(ops.has("orderCreatedSend")).isTrue();
        assertThat(ops.has("orderCreatedReceive")).isFalse();
        assertThat(ops.path("orderCreatedSend").path("action").asText()).isEqualTo("send");
        assertThat(ops.path("orderCreatedSend").path("channel").path("$ref").asText())
                .isEqualTo("#/channels/orderCreated");
        assertThat(ops.path("orderCreatedSend").path("messages").get(0).path("$ref").asText())
                .isEqualTo("#/channels/orderCreated/messages/OrderCreated");
    }

    @Test
    void receiveActionProducesReceiveOperationOnly() {
        AsyncApiGenerator.ChannelDefinition ch = new AsyncApiGenerator.ChannelDefinition(
                "orderShipped", "orders.shipped", null,
                AsyncApiGenerator.Action.RECEIVE, null, null);
        JsonNode ops = generator.generate("Orders", "1.0.0", null, List.of(ch))
                .getRoot().path("operations");
        assertThat(ops.has("orderShippedReceive")).isTrue();
        assertThat(ops.has("orderShippedSend")).isFalse();
        assertThat(ops.path("orderShippedReceive").path("action").asText()).isEqualTo("receive");
    }

    @Test
    void sendAndReceiveActionProducesBothOperations() {
        AsyncApiGenerator.ChannelDefinition ch = new AsyncApiGenerator.ChannelDefinition(
                "orderUpdated", "orders.updated", null,
                AsyncApiGenerator.Action.SEND_AND_RECEIVE, null, null);
        JsonNode ops = generator.generate("Orders", "1.0.0", null, List.of(ch))
                .getRoot().path("operations");
        assertThat(ops.has("orderUpdatedSend")).isTrue();
        assertThat(ops.has("orderUpdatedReceive")).isTrue();
    }

    @Test
    void appliesDefaultsForBlankFields() {
        AsyncApiGenerator.ChannelDefinition ch = new AsyncApiGenerator.ChannelDefinition(
                "events", "", "", AsyncApiGenerator.Action.SEND, "", "");
        JsonNode root = generator.generate(null, null, null, List.of(ch)).getRoot();
        assertThat(root.path("info").path("title").asText()).isEqualTo("AsyncAPI");
        assertThat(root.path("info").path("version").asText()).isEqualTo("1.0.0");
        assertThat(root.path("info").has("description")).isFalse();
        JsonNode channel = root.path("channels").path("events");
        assertThat(channel.path("address").asText()).isEqualTo("events");
        assertThat(channel.has("description")).isFalse();
        assertThat(channel.path("messages").has("eventsMessage")).isTrue();
        assertThat(root.path("components").path("messages")
                .path("eventsMessage").path("payload").path("type").asText()).isEqualTo("object");
    }

    @Test
    void emptyChannelListProducesEmptyChannelsAndOperations() {
        JsonNode root = generator.generate("Empty", "1.0.0", null, List.of()).getRoot();
        assertThat(root.path("channels").isObject()).isTrue();
        assertThat(root.path("channels").isEmpty()).isTrue();
        assertThat(root.path("operations").isEmpty()).isTrue();
    }

    @Test
    void documentSerializesToParsableYaml() throws Exception {
        AsyncApiDocument doc = generator.generate("Orders", "1.0.0", "desc", List.of(sendChannel()));
        JsonNode fromYaml = yamlMapper.readTree(doc.toYaml());
        assertThat(fromYaml.path("asyncapi").asText()).isEqualTo("3.0.0");
        assertThat(doc.toString()).contains("asyncapi");
    }

    @Test
    void actionFromParsesLeniently() {
        assertThat(AsyncApiGenerator.Action.from(null)).isEqualTo(AsyncApiGenerator.Action.SEND);
        assertThat(AsyncApiGenerator.Action.from("")).isEqualTo(AsyncApiGenerator.Action.SEND);
        assertThat(AsyncApiGenerator.Action.from("receive")).isEqualTo(AsyncApiGenerator.Action.RECEIVE);
        assertThat(AsyncApiGenerator.Action.from("subscribe")).isEqualTo(AsyncApiGenerator.Action.RECEIVE);
        assertThat(AsyncApiGenerator.Action.from("send-and-receive"))
                .isEqualTo(AsyncApiGenerator.Action.SEND_AND_RECEIVE);
        assertThat(AsyncApiGenerator.Action.from("both"))
                .isEqualTo(AsyncApiGenerator.Action.SEND_AND_RECEIVE);
        assertThat(AsyncApiGenerator.Action.from("garbage")).isEqualTo(AsyncApiGenerator.Action.SEND);
    }

    @Test
    void channelDefinitionDefaultsNullActionToSend() {
        AsyncApiGenerator.ChannelDefinition ch = new AsyncApiGenerator.ChannelDefinition(
                "c", "a", "d", null, "m", "object");
        assertThat(ch.action()).isEqualTo(AsyncApiGenerator.Action.SEND);
    }

    @Test
    void channelDefinitionRejectsNullName() {
        assertThatThrownBy(() -> new AsyncApiGenerator.ChannelDefinition(
                null, "a", "d", AsyncApiGenerator.Action.SEND, "m", "object"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullChannelListRejected() {
        assertThatThrownBy(() -> generator.generate("t", "1", null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
