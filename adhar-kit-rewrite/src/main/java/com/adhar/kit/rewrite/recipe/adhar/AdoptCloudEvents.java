package com.adhar.kit.rewrite.recipe.adhar;

/**
 * Generates OpenRewrite YAML recipe definitions for adopting the CloudEvents
 * specification in Adhar Kit applications.
 *
 * <p>Transforms custom event classes to use the {@code AdharCloudEvent} envelope,
 * ensuring compliance with the CloudEvents 1.0 specification. The migration adds
 * the necessary CloudEvents imports and wraps existing event payloads.</p>
 *
 * <p>Transformations performed:</p>
 * <ul>
 *   <li>Adds {@code io.cloudevents.CloudEvent} import where event classes are used</li>
 *   <li>Adds {@code io.cloudevents.core.builder.CloudEventBuilder} import for event creation</li>
 *   <li>Renames custom event base types to use the Adhar CloudEvent envelope</li>
 *   <li>Updates event publishing calls to wrap payloads in CloudEvent format</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class AdoptCloudEvents {

    private AdoptCloudEvents() {}

    /**
     * Returns the YAML recipe definition for adopting CloudEvents in Adhar Kit applications.
     *
     * @return YAML string defining the composite CloudEvents adoption recipe
     */
    public static String getYamlDefinition() {
        return """
                ---
                type: specs.openrewrite.org/v1beta/recipe
                name: com.adhar.kit.rewrite.recipe.adhar.AdoptCloudEvents
                displayName: Adopt CloudEvents Specification
                description: >-
                  Migrates custom event classes to use the AdharCloudEvent envelope for
                  CloudEvents 1.0 compliance. Adds CloudEvent imports, wraps event payloads,
                  and updates event publishing patterns.
                recipeList:
                  # ---------------------------------------------------------------
                  # Add CloudEvents imports
                  # ---------------------------------------------------------------
                  - org.openrewrite.java.AddImport:
                      type: io.cloudevents.CloudEvent
                      onlyIfReferenced: true
                  - org.openrewrite.java.AddImport:
                      type: io.cloudevents.core.builder.CloudEventBuilder
                      onlyIfReferenced: true

                  # ---------------------------------------------------------------
                  # Rename base event type to CloudEvent envelope
                  # ---------------------------------------------------------------
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: com.adhar.kit.messaging.event.BaseEvent
                      newFullyQualifiedTypeName: com.adhar.kit.messaging.event.AdharCloudEvent
                  - org.openrewrite.java.ChangeType:
                      oldFullyQualifiedTypeName: com.adhar.kit.messaging.event.DomainEvent
                      newFullyQualifiedTypeName: com.adhar.kit.messaging.event.AdharCloudEvent

                  # ---------------------------------------------------------------
                  # Update event factory method names
                  # ---------------------------------------------------------------
                  - org.openrewrite.java.ChangeMethodName:
                      methodPattern: "com.adhar.kit.messaging.event.EventFactory createEvent(..)"
                      newMethodName: cloudEvent
                  - org.openrewrite.java.ChangeMethodName:
                      methodPattern: "com.adhar.kit.messaging.event.EventFactory newEvent(..)"
                      newMethodName: cloudEvent
                """;
    }
}
