package com.adhar.kit.rewrite.recipe.adhar;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class AdoptCloudEventsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AdoptCloudEvents())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void getDisplayName_isNotBlank() {
        assertThat(new AdoptCloudEvents().getDisplayName()).isEqualTo("Adopt CloudEvents Specification");
    }

    @Test
    void getDescription_mentionsCloudEventEnvelope() {
        assertThat(new AdoptCloudEvents().getDescription())
                .contains("AdharCloudEvent")
                .contains("cloudEvent(..)");
    }

    @Test
    void renamesBaseEventReferenceToCloudEventEnvelope() {
        rewriteRun(
                // BaseEvent lives in the (unresolvable-from-here) Adhar Kit messaging library;
                // parsing its declaration alongside the usage gives the AST proper type
                // attribution so ChangeType can match it by fully-qualified name. The library
                // class itself is left unchanged (ignoreDefinition=true); only the application
                // code that references it is rewritten.
                java("package com.adhar.kit.messaging.event; public class BaseEvent {}"),
                java(
                        """
                        package com.example;

                        import com.adhar.kit.messaging.event.BaseEvent;

                        class UserCreatedEvent extends BaseEvent {
                        }
                        """,
                        """
                        package com.example;

                        import com.adhar.kit.messaging.event.AdharCloudEvent;

                        class UserCreatedEvent extends AdharCloudEvent {
                        }
                        """
                )
        );
    }

    @Test
    void renamesDomainEventReferenceToCloudEventEnvelope() {
        rewriteRun(
                java("package com.adhar.kit.messaging.event; public class DomainEvent {}"),
                java(
                        """
                        package com.example;

                        import com.adhar.kit.messaging.event.DomainEvent;

                        class OrderPlacedEvent extends DomainEvent {
                        }
                        """,
                        """
                        package com.example;

                        import com.adhar.kit.messaging.event.AdharCloudEvent;

                        class OrderPlacedEvent extends AdharCloudEvent {
                        }
                        """
                )
        );
    }

    @Test
    void renamesCreateEventFactoryMethodToCloudEvent() {
        rewriteRun(
                java(
                        """
                        class Demo {
                            void run(EventFactory factory) {
                                factory.createEvent("payload");
                            }
                        }
                        """,
                        """
                        class Demo {
                            void run(EventFactory factory) {
                                factory.cloudEvent("payload");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void renamesNewEventFactoryMethodToCloudEvent() {
        rewriteRun(
                java(
                        """
                        class Demo {
                            void run(EventFactory factory) {
                                factory.newEvent("payload");
                            }
                        }
                        """,
                        """
                        class Demo {
                            void run(EventFactory factory) {
                                factory.cloudEvent("payload");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void leavesUnrelatedTypesAndMethodsUnchanged() {
        rewriteRun(
                java(
                        """
                        class Demo {
                            void run(EventFactory factory) {
                                factory.otherMethod("payload");
                            }
                        }
                        """
                )
        );
    }
}
