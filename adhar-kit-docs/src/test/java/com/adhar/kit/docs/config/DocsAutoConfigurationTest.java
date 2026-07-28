package com.adhar.kit.docs.config;

import com.adhar.kit.docs.asyncapi.AsyncApiDocument;
import com.adhar.kit.docs.asyncapi.AsyncApiGenerator;
import com.adhar.kit.docs.asyncapi.AsyncApiSpecExporter;
import com.adhar.kit.docs.diff.OpenApiDiffService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DocsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DocsAutoConfiguration.class));

    @Test
    void diffAndAsyncBeansAbsentByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(OpenApiDiffService.class);
            assertThat(context).doesNotHaveBean(AsyncApiGenerator.class);
            assertThat(context).doesNotHaveBean(AsyncApiSpecExporter.class);
            assertThat(context).doesNotHaveBean(AsyncApiDocument.class);
        });
    }

    @Test
    void diffServiceRegisteredWhenEnabled() {
        runner.withPropertyValues("adhar.docs.diff.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(OpenApiDiffService.class));
    }

    @Test
    void asyncApiBeansRegisteredWhenEnabled() {
        runner.withPropertyValues(
                        "adhar.docs.async-api.enabled=true",
                        "adhar.docs.async-api.title=Events API",
                        "adhar.docs.async-api.channels[0].name=orderCreated",
                        "adhar.docs.async-api.channels[0].address=orders.created",
                        "adhar.docs.async-api.channels[0].action=SEND_AND_RECEIVE",
                        "adhar.docs.async-api.channels[0].message-name=OrderCreated")
                .run(context -> {
                    assertThat(context).hasSingleBean(AsyncApiGenerator.class);
                    assertThat(context).hasSingleBean(AsyncApiSpecExporter.class);
                    assertThat(context).hasSingleBean(AsyncApiDocument.class);

                    AsyncApiDocument doc = context.getBean(AsyncApiDocument.class);
                    assertThat(doc.getRoot().path("info").path("title").asText())
                            .isEqualTo("Events API");
                    assertThat(doc.getRoot().path("channels").path("orderCreated")
                            .path("address").asText()).isEqualTo("orders.created");
                    assertThat(doc.getRoot().path("operations").has("orderCreatedSend")).isTrue();
                    assertThat(doc.getRoot().path("operations").has("orderCreatedReceive")).isTrue();
                });
    }

    @Test
    void docsDisabledSkipsAutoConfiguration() {
        runner.withPropertyValues("adhar.docs.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OpenApiDiffService.class));
    }
}
