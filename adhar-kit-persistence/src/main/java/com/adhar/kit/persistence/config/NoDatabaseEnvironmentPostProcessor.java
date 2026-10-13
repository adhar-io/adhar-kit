package com.adhar.kit.persistence.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Switches JDBC/JPA auto-configuration off when the application has no database.
 * <p>
 * {@code adhar-kit-starter} includes this module, and with it Spring Data JPA, so
 * every service built on the starter has JPA on its classpath — and Spring Boot then
 * refuses to start one that has no database: "Failed to configure a DataSource:
 * 'url' attribute is not specified". A service without a database is normal (a
 * gateway, a cache-backed API, an event consumer), so this treats "no
 * {@code spring.datasource.url}, no JNDI DataSource and no embedded database driver"
 * as "this service has no database" and excludes the DataSource, JPA, Spring Data
 * JPA, Flyway and Adhar persistence auto-configurations. A log line says so.
 * <p>
 * Configuring a database (setting {@code spring.datasource.url}) turns all of it back
 * on with no other change. Set {@code adhar.persistence.disable-without-database=false}
 * to keep Spring Boot's behaviour of failing fast instead.
 */
public class NoDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "adharNoDatabase";

    static final List<String> EXCLUSIONS = List.of(
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.XADataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayEndpointAutoConfiguration",
            PersistenceAutoConfiguration.class.getName());

    private static final List<String> EMBEDDED_DRIVERS = List.of(
            "org.h2.Driver", "org.hsqldb.jdbc.JDBCDriver", "org.apache.derby.jdbc.EmbeddedDriver");

    private final Log log;

    /**
     * @param logFactory Spring Boot's deferred logging, replayed once logging is up
     */
    public NoDatabaseEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(NoDatabaseEnvironmentPostProcessor.class);
    }

    @Override
    public int getOrder() {
        // After application.properties/yaml and the environment have been loaded.
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("adhar.persistence.disable-without-database", Boolean.class, true)
                || hasDatabase(environment) || embeddedDriverPresent()) {
            return;
        }
        Set<String> exclude = new LinkedHashSet<>(Binder.get(environment)
                .bind("spring.autoconfigure.exclude", Bindable.listOf(String.class))
                .orElseGet(List::of));
        exclude.addAll(EXCLUSIONS);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME,
                Map.of("spring.autoconfigure.exclude", String.join(",", new ArrayList<>(exclude)))));
        log.info("No database configured (spring.datasource.url is not set): JDBC/JPA "
                + "auto-configuration disabled. Set spring.datasource.url to enable it.");
    }

    static boolean hasDatabase(ConfigurableEnvironment environment) {
        return StringUtils.hasText(environment.getProperty("spring.datasource.url"))
                || StringUtils.hasText(environment.getProperty("spring.datasource.jndi-name"));
    }

    /** An embedded database driver means Spring Boot can create a DataSource unaided. */
    boolean embeddedDriverPresent() {
        ClassLoader classLoader = NoDatabaseEnvironmentPostProcessor.class.getClassLoader();
        return EMBEDDED_DRIVERS.stream().anyMatch(driver -> ClassUtils.isPresent(driver, classLoader));
    }
}
