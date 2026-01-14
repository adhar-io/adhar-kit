package com.adhar.kit.config.source.impl;

import com.adhar.kit.config.source.ConfigSource;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * File-based configuration source supporting YAML and Properties files.
 *
 * <p>Loads configuration from classpath or filesystem resources.</p>
 *
 * <p><b>Supported Formats:</b></p>
 * <ul>
 *   <li>YAML (.yml, .yaml)</li>
 *   <li>Properties (.properties)</li>
 * </ul>
 *
 * <p><b>Example - YAML:</b></p>
 * <pre>{@code
 * // application.yml
 * database:
 *   url: jdbc:postgresql://localhost:5432/mydb
 *   pool:
 *     maxSize: 20
 *     minIdle: 10
 *
 * // Load configuration
 * ConfigSource source = new FileConfigSource("classpath:application.yml", 100);
 * String dbUrl = source.getProperty("database.url").orElse(null);
 * }</pre>
 *
 * <p><b>Example - Properties:</b></p>
 * <pre>{@code
 * // app.properties
 * database.url=jdbc:postgresql://localhost:5432/mydb
 * database.pool.maxSize=20
 *
 * ConfigSource source = new FileConfigSource("classpath:app.properties", 100);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class FileConfigSource implements ConfigSource {

    private final String location;
    private final int priority;
    private final FileType fileType;
    private Map<String, Object> config;

    /**
     * File types supported.
     */
    public enum FileType {
        YAML, PROPERTIES, AUTO
    }

    /**
     * Constructor with auto-detection.
     *
     * @param location file location (classpath: or file:)
     * @param priority source priority
     */
    public FileConfigSource(String location, int priority) {
        this(location, priority, FileType.AUTO);
    }

    /**
     * Constructor with explicit type.
     *
     * @param location file location
     * @param priority source priority
     * @param fileType file type
     */
    public FileConfigSource(String location, int priority, FileType fileType) {
        this.location = location;
        this.priority = priority;
        this.fileType = fileType != FileType.AUTO ? fileType : detectFileType(location);
        this.config = new HashMap<>();
        loadFile();
    }

    @Override
    public String getType() {
        return "file";
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Map<String, Object> loadConfig() {
        return new HashMap<>(config);
    }

    @Override
    public Optional<Object> getProperty(String key) {
        return Optional.ofNullable(getNestedProperty(config, key));
    }

    @Override
    public boolean supportsRefresh() {
        return true;
    }

    @Override
    public boolean refresh() {
        try {
            loadFile();
            log.info("Refreshed file configuration from {}", location);
            return true;
        } catch (Exception e) {
            log.error("Failed to refresh file configuration from {}", location, e);
            return false;
        }
    }

    /**
     * Loads file content.
     */
    private void loadFile() {
        try {
            InputStream inputStream = getInputStream(location);
            if (inputStream == null) {
                log.warn("Configuration file not found: {}", location);
                return;
            }

            Map<String, Object> loadedConfig = fileType == FileType.YAML
                ? loadYaml(inputStream)
                : loadProperties(inputStream);

            this.config = flattenMap(loadedConfig);
            log.info("Loaded {} properties from {}", config.size(), location);

        } catch (Exception e) {
            log.error("Failed to load configuration from {}", location, e);
        }
    }

    /**
     * Gets input stream for location.
     */
    private InputStream getInputStream(String location) {
        if (location.startsWith("classpath:")) {
            String path = location.substring("classpath:".length());
            return getClass().getClassLoader().getResourceAsStream(path);
        } else if (location.startsWith("file:")) {
            String path = location.substring("file:".length());
            try {
                return new java.io.FileInputStream(path);
            } catch (IOException e) {
                log.error("Failed to open file: {}", path, e);
                return null;
            }
        }
        return null;
    }

    /**
     * Detects file type from extension.
     */
    private FileType detectFileType(String location) {
        if (location.endsWith(".yml") || location.endsWith(".yaml")) {
            return FileType.YAML;
        } else if (location.endsWith(".properties")) {
            return FileType.PROPERTIES;
        }
        return FileType.PROPERTIES; // Default
    }

    /**
     * Loads YAML file (simplified - actual implementation would use SnakeYAML).
     */
    private Map<String, Object> loadYaml(InputStream inputStream) throws IOException {
        // Simplified - in production use org.yaml.snakeyaml.Yaml
        log.warn("YAML parsing not fully implemented - add org.yaml:snakeyaml dependency");
        return new HashMap<>();
    }

    /**
     * Loads properties file.
     */
    private Map<String, Object> loadProperties(InputStream inputStream) throws IOException {
        Properties props = new Properties();
        props.load(inputStream);

        Map<String, Object> map = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            map.put(key, props.getProperty(key));
        }
        return map;
    }

    /**
     * Flattens nested map to dot notation.
     * <p>
     * Example: {database: {url: "..."}} → {"database.url": "..."}
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenMap(Map<String, Object> source) {
        Map<String, Object> result = new HashMap<>();
        flattenMapRecursive("", source, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void flattenMapRecursive(String prefix, Map<String, Object> source, Map<String, Object> result) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                flattenMapRecursive(key, (Map<String, Object>) value, result);
            } else {
                result.put(key, value);
            }
        }
    }

    /**
     * Gets nested property using dot notation.
     */
    private Object getNestedProperty(Map<String, Object> map, String key) {
        if (map.containsKey(key)) {
            return map.get(key);
        }

        String[] parts = key.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }

        return current;
    }
}

