package com.adhar.kit.messaging.cloudevents;

import com.adhar.kit.messaging.core.Message;
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
            builder.withData(PojoCloudEventData.wrap(message.getPayload(), null));
        }

        // Add any additional headers as extensions
        message.getHeaders().forEach((key, value) -> {
            if (value != null) {
                builder.withExtension(key, value.toString());
            }
        });

        return builder.build();
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
                payload = (T) data;
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
        builder.header("ce-specversion", cloudEvent.getSpecVersion());

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

        // Add any extensions as headers
        Set<String> attributeNames = cloudEvent.getAttributeNames();
        for (String name : attributeNames) {
            if (!isStandardAttribute(name)) {
                Object value = cloudEvent.getAttribute(name);
                if (value != null) {
                    builder.header(name, value);
                }
            }
        }

        return builder.build();
    }

    /**
     * Checks if an attribute name is a standard CloudEvent attribute.
     *
     * @param name the attribute name
     * @return true if the attribute is a standard CloudEvent attribute, false otherwise
     */
    private boolean isStandardAttribute(String name) {
        return name.equals("id") || name.equals("source") || name.equals("type") || 
               name.equals("specversion") || name.equals("datacontenttype") || 
               name.equals("dataschema") || name.equals("subject") || name.equals("time");
    }
}
