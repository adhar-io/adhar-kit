package com.adhar.kit.profiler.config;

import com.adhar.kit.profiler.aspect.ProfilingAspect;
import com.adhar.kit.profiler.endpoint.ProfilingActuatorEndpoint;
import com.adhar.kit.profiler.memory.MemoryProfiler;
import com.adhar.kit.profiler.registry.ProfilingRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Adhar Kit Performance Profiler.
 * Registers the profiling aspect, registry, memory profiler, and actuator endpoint.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "adhar.profiler", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PerfProfilerProperties.class)
public class PerfProfilerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ProfilingRegistry profilingRegistry() {
        return new ProfilingRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryProfiler memoryProfiler() {
        return new MemoryProfiler();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProfilingAspect profilingAspect(MeterRegistry meterRegistry, ProfilingRegistry profilingRegistry) {
        return new ProfilingAspect(meterRegistry, profilingRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(Endpoint.class)
    public ProfilingActuatorEndpoint profilingActuatorEndpoint(ProfilingRegistry profilingRegistry,
                                                               MemoryProfiler memoryProfiler) {
        return new ProfilingActuatorEndpoint(profilingRegistry, memoryProfiler);
    }
}
