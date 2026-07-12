package com.adhar.kit.messaging.cloudevents;

import com.adhar.kit.messaging.core.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * Adapter for converting between {@link Message} and {@link CloudEvent}.
 * <p>
 * This class provides methods for converting a Message to a CloudEvent and vice versa.
 * It implements the CloudEvent specification as defined by the CloudEvents project.
 * <p>
 * CloudEvents is a specification for describing event data in a common way.
 * See https://cloudevents.io/ for more information.
 */
public class CloudEventAdapter {

    /** Serializes POJO payloads to bytes when a CloudEvent is written to the wire. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Converts a Message to a CloudEvent.
     *
     * @param message the message to convert
     * @param <T>     the type of the message payload
     * @return a CloudEvent representing the message
     */
    public <T> CloudEvent toCloudEvent(Message<T> message) {
        CloudEventBuilder builder = CloudEventBuilder.v1()
                .withId(message.getId())
                .withSource(message.getSource())
                .withType(message.getType())
                .withTime(message.getTimestamp() != null ? 
                        OffsetDateTime.ofInstant(message.getTimestamp(), ZoneOffset.UTC) : null);

        if (message.getDataContentType() != null) {
            builder.withDataContentType(message.getDataContentType());
        }

        if (message.getDataSchema() != null) {
            builder.withDataSchema(message.getDataSchema());
        }

        if (message.getSubject() != null) {
            builder.withSubject(message.getSubject());
        }

        if (message.getPayload() != null) {
            builder.withData(PojoCloudEventData.wrap(message.getPayload(), OBJECT_MAPPER::writeValueAsBytes));
        }

        // Add any additional headers as extensions. CloudEvents 1.0 (strictly
        // enforced since cloudevents-sdk 3.x/4.x) requires extension names to be
        // lowercase alphanumeric, so header keys are normalized accordingly.
        message.getHeaders().forEach((key, value) -> {
            if (value != null) {
                String extensionName = sanitizeExtensionName(key);
                if (!extensionName.isEmpty()) {
                    builder.withExtension(extensionName, value.toString());
                }
            }
        });

        return builder.build();
    }

    /**
     * Normalizes an arbitrary header key into a CloudEvents-compliant extension
     * name (lowercase {@code a-z0-9} only), as mandated by the CloudEvents spec.
     *
     * @param key the original header key
     * @return a spec-compliant extension name (possibly empty if no valid characters)
     */
    private String sanitizeExtensionName(String key) {
        return key == null ? "" : key.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Converts a CloudEvent to a Message.
     *
     * @param cloudEvent the CloudEvent to convert
     * @param <T>        the type of the message payload
     * @return a Message representing the CloudEvent
     */
    @SuppressWarnings("unchecked")
    public <T> Message<T> fromCloudEvent(CloudEvent cloudEvent) {
        T payload = null;
        if (cloudEvent.getData() != null) {
            CloudEventData data = cloudEvent.getData();
            if (data instanceof PojoCloudEventData) {
                payload = (T) ((PojoCloudEventData<?>) data).getValue();
            } else {
                // CloudEvents 4.x wraps raw bytes in BytesCloudEventData (and other
                // CloudEventData implementations); expose the underlying byte[] payload.
                payload = (T) data.toBytes();
            }
        }

        @SuppressWarnings("unchecked")
        Message.Builder<T> builder = (Message.Builder<T>) Message.builder()
                .payload(payload)
                .destination(cloudEvent.getSubject());

        // Add CloudEvent attributes as headers
        builder.header("ce-id", cloudEvent.getId());
        builder.header("ce-source", cloudEvent.getSource().toString());
        builder.header("ce-type", cloudEvent.getType());
        // getSpecVersion() returns the SpecVersion enum; store its String form ("1.0").
        builder.header("ce-specversion", cloudEvent.getSpecVersion().toString());

        if (cloudEvent.getDataContentType() != null) {
            builder.header("ce-datacontenttype", cloudEvent.getDataContentType());
        }

        if (cloudEvent.getDataSchema() != null) {
            builder.header("ce-dataschema", cloudEvent.getDataSchema().toString());
        }

        if (cloudEvent.getSubject() != null) {
            builder.header("ce-subject", cloudEvent.getSubject());
            builder.routingKey(cloudEvent.getSubject());
        }

        if (cloudEvent.getTime() != null) {
            builder.header("ce-time", cloudEvent.getTime().toString());
        }

        // Add any extensions as headers. In CloudEvents 4.x extensions are exposed
        // via getExtensionNames()/getExtension() - separate from the context
        // attributes returned by getAttributeNames().
        Set<String> extensionNames = cloudEvent.getExtensionNames();
        for (String name : extensionNames) {
            Object value = cloudEvent.getExtension(name);
            if (value != null) {
                builder.header(name, value);
            }
        }

        return builder.build();
    }
}
