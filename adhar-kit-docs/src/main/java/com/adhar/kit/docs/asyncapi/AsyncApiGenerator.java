package com.adhar.kit.docs.asyncapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

/**
 * Generates a basic <a href="https://www.asyncapi.com/docs/reference/specification/v3.0.0">AsyncAPI 3.0</a>
 * document from configured channel/topic metadata, reusing Jackson.
 *
 * <p>The generator is intentionally minimal: it produces {@code info}, {@code channels},
 * {@code operations}, and reusable {@code components/messages} from a list of
 * {@link ChannelDefinition}s. This is enough to describe event-driven endpoints
 * (message topics/queues) alongside the REST OpenAPI document.</p>
 *
 * <p><b>Example</b></p>
 * <pre>{@code
 * AsyncApiGenerator generator = new AsyncApiGenerator();
 * AsyncApiDocument doc = generator.generate(
 *     "Orders Events", "1.0.0", "Order lifecycle events",
 *     List.of(new AsyncApiGenerator.ChannelDefinition(
 *         "orderCreated", "orders.created", "Emitted when an order is created",
 *         AsyncApiGenerator.Action.SEND, "OrderCreated", "object")));
 * new AsyncApiSpecExporter().exportAll(doc, Path.of("target"));
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class AsyncApiGenerator {

    /** The AsyncAPI specification version emitted by this generator. */
    public static final String ASYNCAPI_VERSION = "3.0.0";

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * The direction of a channel operation.
     */
    public enum Action {
        /** The application sends (publishes) messages to the channel. */
        SEND,
        /** The application receives (consumes) messages from the channel. */
        RECEIVE,
        /** The application both sends and receives messages on the channel. */
        SEND_AND_RECEIVE;

        /**
         * Lenient parser for property-driven configuration (accepts hyphen/space separators,
         * any case). Defaults to {@link #SEND} for null/blank/unrecognized input.
         *
         * @param value the configured value
         * @return the matching action
         */
        public static Action from(String value) {
            if (value == null || value.isBlank()) {
                return SEND;
            }
            String normalized = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
            return switch (normalized) {
                case "RECEIVE", "SUBSCRIBE", "CONSUME" -> RECEIVE;
                case "SEND_AND_RECEIVE", "BOTH", "SEND_RECEIVE" -> SEND_AND_RECEIVE;
                default -> SEND;
            };
        }
    }

    /**
     * Metadata describing a single AsyncAPI channel and its operation(s).
     *
     * @param name        the channel identifier (used as the channel key and operation prefix)
     * @param address     the channel address (topic/queue name); defaults to {@code name} if blank
     * @param description a human-readable description
     * @param action      the operation direction
     * @param messageName the message name; defaults to {@code <name>Message} if blank
     * @param payloadType the JSON schema type of the message payload (e.g. {@code object})
     */
    public record ChannelDefinition(String name, String address, String description,
                                    Action action, String messageName, String payloadType) {
        public ChannelDefinition {
            Objects.requireNonNull(name, "name must not be null");
            if (action == null) {
                action = Action.SEND;
            }
        }
    }

    /**
     * Generates an AsyncAPI 3.0 document from the supplied metadata.
     *
     * @param title       the API title
     * @param version     the API version
     * @param description the API description (may be null)
     * @param channels    the channel definitions (may be empty)
     * @return the generated document
     */
    public AsyncApiDocument generate(String title, String version, String description,
                                     List<ChannelDefinition> channels) {
        Objects.requireNonNull(channels, "channels must not be null");
        log.info("Generating AsyncAPI {} document '{}' with {} channel(s)",
                ASYNCAPI_VERSION, title, channels.size());

        ObjectNode root = mapper.createObjectNode();
        root.put("asyncapi", ASYNCAPI_VERSION);

        ObjectNode info = root.putObject("info");
        info.put("title", title == null ? "AsyncAPI" : title);
        info.put("version", version == null ? "1.0.0" : version);
        if (description != null) {
            info.put("description", description);
        }

        ObjectNode channelsNode = root.putObject("channels");
        ObjectNode operationsNode = root.putObject("operations");
        ObjectNode messagesNode = root.putObject("components").putObject("messages");

        for (ChannelDefinition channel : channels) {
            addChannel(channelsNode, operationsNode, messagesNode, channel);
        }

        return new AsyncApiDocument(root);
    }

    private void addChannel(ObjectNode channelsNode, ObjectNode operationsNode,
                            ObjectNode messagesNode, ChannelDefinition channel) {
        String name = channel.name();
        String messageName = blankOr(channel.messageName(), name + "Message");
        String address = blankOr(channel.address(), name);

        // channels/<name>
        ObjectNode channelNode = channelsNode.putObject(name);
        channelNode.put("address", address);
        if (channel.description() != null && !channel.description().isBlank()) {
            channelNode.put("description", channel.description());
        }
        ObjectNode channelMessages = channelNode.putObject("messages");
        channelMessages.putObject(messageName)
                .put("$ref", "#/components/messages/" + messageName);

        // components/messages/<messageName>
        ObjectNode messageNode = messagesNode.putObject(messageName);
        messageNode.put("name", messageName);
        messageNode.putObject("payload")
                .put("type", blankOr(channel.payloadType(), "object"));

        // operations/<name><Direction>
        Action action = channel.action();
        if (action == Action.SEND || action == Action.SEND_AND_RECEIVE) {
            addOperation(operationsNode, name + "Send", "send", name, messageName);
        }
        if (action == Action.RECEIVE || action == Action.SEND_AND_RECEIVE) {
            addOperation(operationsNode, name + "Receive", "receive", name, messageName);
        }
    }

    private void addOperation(ObjectNode operationsNode, String operationName, String actionValue,
                              String channelName, String messageName) {
        ObjectNode operation = operationsNode.putObject(operationName);
        operation.put("action", actionValue);
        operation.putObject("channel").put("$ref", "#/channels/" + channelName);
        ArrayNode messages = operation.putArray("messages");
        messages.addObject().put("$ref",
                "#/channels/" + channelName + "/messages/" + messageName);
    }

    private String blankOr(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
