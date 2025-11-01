package com.adhar.adharkit.metrics.config;

import com.adhar.adharkit.metrics.properties.AdharMetricsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple tests for {@link AdharMetricsAutoConfiguration}.
 */
class AdharMetricsAutoConfigurationTest {

    @Test
    void testConfigurationClassExists() {
        // Verify the configuration class can be instantiated
        AdharMetricsProperties properties = new AdharMetricsProperties();
        Environment environment = Mockito.mock(Environment.class);
        AdharMetricsAutoConfiguration config = new AdharMetricsAutoConfiguration(properties, environment);
        assertThat(config).isNotNull();
    }

    @Test
    void testPropertiesConfiguration() {
        AdharMetricsProperties properties = new AdharMetricsProperties();
        properties.setEnabled(true);

        Environment environment = Mockito.mock(Environment.class);
        AdharMetricsAutoConfiguration config = new AdharMetricsAutoConfiguration(properties, environment);
        assertThat(config).isNotNull();
    }
}

