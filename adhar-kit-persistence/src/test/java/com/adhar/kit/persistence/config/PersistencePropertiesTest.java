package com.adhar.kit.persistence.config;

import com.adhar.kit.persistence.config.PersistenceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PersistenceProperties Tests")
class PersistencePropertiesTest {

    @Test
    @DisplayName("Should have default values")
    void testDefaultValues() {
        PersistenceProperties properties = new PersistenceProperties();

        assertTrue(properties.isEnabled());
        assertTrue(properties.isEnableAuditing());
        assertFalse(properties.isEnableMultiTenancy());
        assertEquals(PersistenceProperties.MultiTenancyStrategy.SCHEMA,
                     properties.getMultiTenancyStrategy());
        assertTrue(properties.isEnableSoftDelete());
        assertNotNull(properties.getMigration());
        assertNotNull(properties.getConnectionPool());
    }

    @Test
    @DisplayName("Should set and get all properties")
    void testSettersAndGetters() {
        PersistenceProperties properties = new PersistenceProperties();

        properties.setEnabled(false);
        properties.setEnableAuditing(false);
        properties.setEnableMultiTenancy(true);
        properties.setMultiTenancyStrategy(PersistenceProperties.MultiTenancyStrategy.DATABASE);
        properties.setEnableSoftDelete(false);

        assertFalse(properties.isEnabled());
        assertFalse(properties.isEnableAuditing());
        assertTrue(properties.isEnableMultiTenancy());
        assertEquals(PersistenceProperties.MultiTenancyStrategy.DATABASE,
                     properties.getMultiTenancyStrategy());
        assertFalse(properties.isEnableSoftDelete());
    }

    @Test
    @DisplayName("Should test Migration properties")
    void testMigrationProperties() {
        PersistenceProperties.Migration migration = new PersistenceProperties.Migration();

        assertTrue(migration.isEnabled());
        assertFalse(migration.isCleanOnValidationError());
        assertTrue(migration.isBaselineOnMigrate());
        assertEquals("classpath:db/migration", migration.getLocations());

        migration.setEnabled(false);
        migration.setCleanOnValidationError(true);
        migration.setBaselineOnMigrate(false);
        migration.setLocations("custom/location");

        assertFalse(migration.isEnabled());
        assertTrue(migration.isCleanOnValidationError());
        assertFalse(migration.isBaselineOnMigrate());
        assertEquals("custom/location", migration.getLocations());
    }

    @Test
    @DisplayName("Should test ConnectionPool properties")
    void testConnectionPoolProperties() {
        PersistenceProperties.ConnectionPool pool = new PersistenceProperties.ConnectionPool();

        assertEquals("AdharHikariPool", pool.getPoolName());
        assertEquals(10, pool.getMaximumPoolSize());
        assertEquals(5, pool.getMinimumIdle());
        assertEquals(30000, pool.getConnectionTimeout());
        assertEquals(600000, pool.getIdleTimeout());
        assertEquals(1800000, pool.getMaxLifetime());

        pool.setPoolName("CustomPool");
        pool.setMaximumPoolSize(20);
        pool.setMinimumIdle(10);
        pool.setConnectionTimeout(60000);
        pool.setIdleTimeout(1200000);
        pool.setMaxLifetime(3600000);

        assertEquals("CustomPool", pool.getPoolName());
        assertEquals(20, pool.getMaximumPoolSize());
        assertEquals(10, pool.getMinimumIdle());
        assertEquals(60000, pool.getConnectionTimeout());
        assertEquals(1200000, pool.getIdleTimeout());
        assertEquals(3600000, pool.getMaxLifetime());
    }

    @Test
    @DisplayName("Should default outbox relay + kafka topic")
    void testOutboxRelayDefaults() {
        PersistenceProperties.Outbox outbox = new PersistenceProperties.Outbox();

        assertEquals("application-event", outbox.getRelay());
        assertNotNull(outbox.getKafka());
        assertEquals("adhar.outbox.events", outbox.getKafka().getTopic());

        outbox.setRelay("kafka");
        outbox.getKafka().setTopic("orders.events");
        assertEquals("kafka", outbox.getRelay());
        assertEquals("orders.events", outbox.getKafka().getTopic());
    }

    @Test
    @DisplayName("Should default Envers enabled and Diagnostics N+1 disabled")
    void testEnversAndDiagnosticsDefaults() {
        PersistenceProperties properties = new PersistenceProperties();

        assertTrue(properties.getEnvers().isEnabled());
        assertFalse(properties.getDiagnostics().getNPlusOne().isEnabled());
        assertEquals(10, properties.getDiagnostics().getNPlusOne().getThreshold());

        properties.getEnvers().setEnabled(false);
        properties.getDiagnostics().getNPlusOne().setEnabled(true);
        properties.getDiagnostics().getNPlusOne().setThreshold(25);

        assertFalse(properties.getEnvers().isEnabled());
        assertTrue(properties.getDiagnostics().getNPlusOne().isEnabled());
        assertEquals(25, properties.getDiagnostics().getNPlusOne().getThreshold());
    }

    @Test
    @DisplayName("Should test MultiTenancyStrategy enum")
    void testMultiTenancyStrategy() {
        assertEquals(3, PersistenceProperties.MultiTenancyStrategy.values().length);

        assertNotNull(PersistenceProperties.MultiTenancyStrategy.SCHEMA);
        assertNotNull(PersistenceProperties.MultiTenancyStrategy.DATABASE);
        assertNotNull(PersistenceProperties.MultiTenancyStrategy.DISCRIMINATOR);

        assertEquals("SCHEMA", PersistenceProperties.MultiTenancyStrategy.SCHEMA.name());
        assertEquals("DATABASE", PersistenceProperties.MultiTenancyStrategy.DATABASE.name());
        assertEquals("DISCRIMINATOR", PersistenceProperties.MultiTenancyStrategy.DISCRIMINATOR.name());
    }
}

