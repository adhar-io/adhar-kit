package com.adhar.kit.grpc.spring;

import com.adhar.kit.grpc.annotation.GrpcClient;
import com.adhar.kit.grpc.client.AdharGrpcClientFactory;
import com.adhar.kit.grpc.config.GrpcProperties;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GrpcClientBeanPostProcessor}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class GrpcClientBeanPostProcessorTest {

    private AdharGrpcClientFactory factory;
    private GrpcClientBeanPostProcessor processor;

    @BeforeEach
    void setUp() {
        factory = new AdharGrpcClientFactory(new GrpcProperties());
        processor = new GrpcClientBeanPostProcessor(factory);
    }

    @AfterEach
    void tearDown() {
        factory.shutdown();
    }

    @Test
    void injectsManagedChannel_intoAnnotatedField() {
        BeanWithChannelField bean = new BeanWithChannelField();

        Object result = processor.postProcessBeforeInitialization(bean, "beanWithChannelField");

        assertThat(result).isSameAs(bean);
        assertThat(bean.channel).isNotNull();
        assertThat(bean.channel).isSameAs(factory.getChannel("order-service"));
    }

    @Test
    void skipsUnsupportedFieldType_withoutThrowing() {
        BeanWithUnsupportedField bean = new BeanWithUnsupportedField();

        Object result = processor.postProcessBeforeInitialization(bean, "beanWithUnsupportedField");

        assertThat(result).isSameAs(bean);
        assertThat(bean.stub).isNull();
    }

    @Test
    void ignoresFieldsWithoutGrpcClientAnnotation() {
        BeanWithPlainField bean = new BeanWithPlainField();

        processor.postProcessBeforeInitialization(bean, "beanWithPlainField");

        assertThat(bean.notAnnotated).isNull();
        assertThat(factory.getChannelCount()).isEqualTo(0);
    }

    private static class BeanWithChannelField {
        @GrpcClient("order-service")
        private ManagedChannel channel;
    }

    private static class BeanWithUnsupportedField {
        @GrpcClient("order-service")
        private String stub;
    }

    private static class BeanWithPlainField {
        private String notAnnotated;
    }
}
