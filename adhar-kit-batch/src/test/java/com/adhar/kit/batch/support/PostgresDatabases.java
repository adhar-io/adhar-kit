package com.adhar.kit.batch.support;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One PostgreSQL 18.4 container for the whole test JVM (the platform's CNPG version), with a fresh
 * database per test. Started on first use; Testcontainers' Ryuk removes it when the JVM exits.
 * Tests using it are named {@code *TestContainerTest}, which the platform's release pipeline
 * skips (it has no Docker), and carry {@code @Testcontainers(disabledWithoutDocker = true)}.
 */
public final class PostgresDatabases {

    private static final String IMAGE = "postgres:18.4";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static PostgreSQLContainer container;

    private PostgresDatabases() {
    }

    private static synchronized PostgreSQLContainer container() {
        if (container == null) {
            PostgreSQLContainer started = new PostgreSQLContainer(IMAGE);
            started.start();
            container = started;
        }
        return container;
    }

    /**
     * @param name a readable prefix
     * @return the JDBC URL of a new, empty database
     */
    public static String createDatabase(String name) {
        PostgreSQLContainer postgres = container();
        String database = (name + "_" + SEQUENCE.incrementAndGet()).toLowerCase();
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not create database " + database, ex);
        }
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + database;
    }

    /**
     * @param url a database of {@link #createDatabase(String)}
     * @return a simple DataSource on it
     */
    public static DataSource dataSource(String url) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username(), password());
        dataSource.setDriverClassName("org.postgresql.Driver");
        return dataSource;
    }

    /**
     * @return the container's user
     */
    public static String username() {
        return container().getUsername();
    }

    /**
     * @return the container's password
     */
    public static String password() {
        return container().getPassword();
    }
}
