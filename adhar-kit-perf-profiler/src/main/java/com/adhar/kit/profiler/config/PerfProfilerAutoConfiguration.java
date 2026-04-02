package com.adhar.kit.profiler.config;

import com.adhar.kit.profiler.aspect.ProfilingAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Adhar Kit Performance Profiler.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "adhar.profiler", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PerfProfilerProperties.class)
public class PerfProfilerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ProfilingAspect profilingAspect(MeterRegistry meterRegistry) {
        return new ProfilingAspect(meterRegistry);
    }
}
