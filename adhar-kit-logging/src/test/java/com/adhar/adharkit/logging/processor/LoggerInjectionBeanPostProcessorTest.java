package com.adhar.adharkit.logging.processor;

import com.adhar.adharkit.logging.LoggingFacade;
import com.adhar.adharkit.logging.annotation.InjectLogger;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import com.adhar.adharkit.logging.util.AdharLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LoggerInjectionBeanPostProcessor}.
 */
class LoggerInjectionBeanPostProcessorTest {

    private AdharLogger adharLogger;
    private LoggerInjectionBeanPostProcessor processor;

    static class SampleBean {
        @InjectLogger
        private Logger log;

        @InjectLogger("custom.logger")
        private Logger namedLog;

        @InjectLogger
        private LoggingFacade facade;

        @InjectLogger
        private AdharLogger adharLogger;

        @InjectLogger
        private String unsupportedType;

        private Logger notAnnotated;
    }

    @BeforeEach
    void setUp() {
        adharLogger = new AdharLogger(new AdharLoggingProperties());
        @SuppressWarnings("unchecked")
        ObjectProvider<AdharLogger> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(adharLogger);
        processor = new LoggerInjectionBeanPostProcessor(provider);
    }

    @Test
    void injectsSlf4jLoggerNamedAfterDeclaringClass() {
        SampleBean bean = (SampleBean) processor.postProcessBeforeInitialization(new SampleBean(), "sampleBean");

        assertThat(bean.log).isNotNull();
        assertThat(bean.log.getName()).isEqualTo(SampleBean.class.getName());
    }

    @Test
    void injectsExplicitlyNamedLogger() {
        SampleBean bean = (SampleBean) processor.postProcessBeforeInitialization(new SampleBean(), "sampleBean");

        assertThat(bean.namedLog).isNotNull();
        assertThat(bean.namedLog.getName()).isEqualTo("custom.logger");
    }

    @Test
    void injectsLoggingFacadeAndAdharLogger() {
        SampleBean bean = (SampleBean) processor.postProcessBeforeInitialization(new SampleBean(), "sampleBean");

        assertThat(bean.facade).isNotNull();
        assertThat(bean.adharLogger).isSameAs(adharLogger);
    }

    @Test
    void ignoresUnsupportedTypesAndUnannotatedFields() {
        SampleBean bean = (SampleBean) processor.postProcessBeforeInitialization(new SampleBean(), "sampleBean");

        assertThat(bean.unsupportedType).isNull();
        assertThat(bean.notAnnotated).isNull();
    }

    @Test
    void beansWithoutAnnotatedFieldsPassThrough() {
        Object plain = new Object();
        assertThat(processor.postProcessBeforeInitialization(plain, "plain")).isSameAs(plain);
    }
}
