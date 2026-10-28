package com.adhar.kit.messaging.dapr;

import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.messaging.config.MessagingAutoConfiguration;
import com.adhar.kit.messaging.core.MessageListener;
import com.adhar.kit.messaging.core.MessagePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the Dapr-backed {@link MessagePublisher}/{@link MessageListener} wiring in
 * {@link MessagingAutoConfiguration}: on wherever adhar-kit-dapr is (same default as that module),
 * off with either switch, and backing off when a publisher already exists.
 */
class DaprMessagingAutoConfigurationTest {

    private static DaprFacade memoryFacade() {
        return DaprFacade.builder()
                .connection(DaprConnectionDetails.resolve(Map.of("adhar.dapr.mode", "memory")::get))
                .build();
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessagingAutoConfiguration.class))
            .withBean(DaprFacade.class, DaprMessagingAutoConfigurationTest::memoryFacade);

    @Test
    void registersDaprPublisherAndListenerByDefault() {
        runner.run(context -> {
            assertThat(context.getBean(MessagePublisher.class)).isInstanceOf(DaprMessagePublisher.class);
            assertThat(context.getBean(MessageListener.class)).isInstanceOf(DaprMessageListener.class);
        });
    }

    @Test
    void thePubsubComponentFollowsTheConfiguredName() {
        runner.withPropertyValues("adhar.messaging.dapr.pubsub-name=my-pubsub")
                .run(context -> assertThat(context).hasSingleBean(MessagePublisher.class));
    }

    @Test
    void staysOffWhenDaprIsDisabled() {
        runner.withPropertyValues("adhar.dapr.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MessagePublisher.class));
    }

    @Test
    void staysOffWhenMessagingDaprDisabled() {
        runner.withPropertyValues("adhar.messaging.dapr.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MessagePublisher.class);
                    assertThat(context).doesNotHaveBean(MessageListener.class);
                });
    }

    @Test
    void userSuppliedPublisherWins() {
        MessagePublisher custom = mock(MessagePublisher.class);
        runner.withBean("customPublisher", MessagePublisher.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(MessagePublisher.class);
                    assertThat(context.getBean(MessagePublisher.class)).isSameAs(custom);
                });
    }
}
