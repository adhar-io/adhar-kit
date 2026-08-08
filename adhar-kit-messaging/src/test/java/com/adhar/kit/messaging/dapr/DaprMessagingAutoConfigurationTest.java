package com.adhar.kit.messaging.dapr;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.messaging.config.MessagingAutoConfiguration;
import com.adhar.kit.messaging.core.MessagePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the Dapr-backed {@link MessagePublisher} wiring in
 * {@link MessagingAutoConfiguration}: opt-in via {@code adhar.dapr.enabled},
 * back-off when a publisher already exists, and off by default.
 */
class DaprMessagingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessagingAutoConfiguration.class));

    @Test
    void registersDaprPublisherWhenDaprEnabledAndFacadePresent() {
        runner.withPropertyValues("adhar.dapr.enabled=true",
                        "adhar.messaging.dapr.pubsub-name=my-pubsub")
                .withBean(DaprFacade.class, () -> mock(DaprFacade.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(MessagePublisher.class);
                    assertThat(context.getBean(MessagePublisher.class))
                            .isInstanceOf(DaprMessagePublisher.class);
                });
    }

    @Test
    void staysOffWithoutDaprEnabledProperty() {
        runner.withBean(DaprFacade.class, () -> mock(DaprFacade.class))
                .run(context -> assertThat(context).doesNotHaveBean(MessagePublisher.class));
    }

    @Test
    void staysOffWhenMessagingDaprDisabled() {
        runner.withPropertyValues("adhar.dapr.enabled=true", "adhar.messaging.dapr.enabled=false")
                .withBean(DaprFacade.class, () -> mock(DaprFacade.class))
                .run(context -> assertThat(context).doesNotHaveBean(MessagePublisher.class));
    }

    @Test
    void userSuppliedPublisherWins() {
        MessagePublisher custom = mock(MessagePublisher.class);
        runner.withPropertyValues("adhar.dapr.enabled=true")
                .withBean(DaprFacade.class, () -> mock(DaprFacade.class))
                .withBean("customPublisher", MessagePublisher.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(MessagePublisher.class);
                    assertThat(context.getBean(MessagePublisher.class)).isSameAs(custom);
                });
    }
}
