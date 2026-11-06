package com.adhar.kit.batch.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A Spring Boot application with the real auto-configuration (Spring Boot's and the kit's) and no
 * component scan: each test adds the job configurations it needs.
 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
public class BatchTestApplication {

    /**
     * adhar-kit-logging's beans need Jackson 2's mapper, which a service gets from adhar-kit-core and
     * which Spring Boot 4 no longer configures itself.
     *
     * @return the mapper
     */
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * @param sources configuration classes besides this one
     * @return a non-web application builder
     */
    public static SpringApplicationBuilder builder(Class<?>... sources) {
        Class<?>[] all = new Class<?>[sources.length + 1];
        all[0] = BatchTestApplication.class;
        System.arraycopy(sources, 0, all, 1, sources.length);
        return new SpringApplicationBuilder(all).web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF);
    }

    /**
     * @return properties of an isolated in-memory H2 database with the test schema
     */
    public static Map<String, Object> h2() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", "jdbc:h2:mem:batch-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        properties.put("spring.sql.init.mode", "embedded");
        properties.put("spring.sql.init.schema-locations", "classpath:adhar-batch-test/h2-schema.sql");
        properties.put("spring.batch.job.enabled", "false");
        return properties;
    }

    /**
     * @param url a PostgreSQL database of {@link PostgresDatabases}
     * @return properties of that database with the test's business tables
     */
    public static Map<String, Object> postgres(String url) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", url);
        properties.put("spring.datasource.username", PostgresDatabases.username());
        properties.put("spring.datasource.password", PostgresDatabases.password());
        properties.put("spring.sql.init.mode", "always");
        properties.put("spring.sql.init.schema-locations", "classpath:adhar-batch-test/business-schema.sql");
        properties.put("spring.batch.job.enabled", "false");
        return properties;
    }
}
