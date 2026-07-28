package com.adhar.kit.config.source.impl;

import com.adhar.kit.config.source.ConfigSource;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Kubernetes ConfigMap configuration source backed by a mounted volume.
 *
 * <p>When a ConfigMap is mounted as a volume, Kubernetes projects each ConfigMap
 * key as a file in the mount directory whose contents are the value. This source
 * reads that standard layout directly from the filesystem: no Kubernetes API
 * client is required, which keeps the dependency footprint minimal and works
 * identically in-cluster and in local tests.</p>
 *
 * <p>Hidden entries created by the atomic-update mechanism (the {@code ..data}
 * symlink and {@code ..2024_...} timestamped directories) are skipped. Each
 * remaining regular file becomes one property: file name is the key, trimmed
 * file content is the value.</p>
 *
 * <p>When {@code watch} is enabled a daemon thread registers a
 * {@link WatchService} on the directory and reloads the property map whenever the
 * mounted content changes, so updates are reflected without waiting for the
 * scheduled refresh.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * ConfigSource cm = new ConfigMapConfigSource("/etc/config", 130, true);
 * String level = (String) cm.getProperty("log.level").orElse("INFO");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ConfigMapConfigSource implements ConfigSource, Closeable {

    private final Path directory;
    private final int priority;
    private final boolean watch;

    private volatile Map<String, Object> config = new HashMap<>();
    private volatile boolean watching;
    private WatchService watchService;
    private Thread watchThread;

    /**
     * Creates a ConfigMap source without file-watching (default priority 130).
     *
     * @param directory mounted ConfigMap directory (e.g. {@code /etc/config})
     */
    public ConfigMapConfigSource(String directory) {
        this(directory, 130, false);
    }

    /**
     * Creates a ConfigMap source.
     *
     * @param directory mounted ConfigMap directory (e.g. {@code /etc/config})
     * @param priority source priority (higher overrides lower)
     * @param watch whether to watch the directory for changes and auto-reload
     */
    public ConfigMapConfigSource(String directory, int priority, boolean watch) {
        this.directory = Paths.get(directory);
        this.priority = priority;
        this.watch = watch;
        loadFromDirectory();
        if (watch) {
            startWatching();
        }
    }

    @Override
    public String getType() {
        return "configmap";
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isEnabled() {
        return Files.isDirectory(directory);
    }

    @Override
    public Map<String, Object> loadConfig() {
        return new HashMap<>(config);
    }

    @Override
    public Optional<Object> getProperty(String key) {
        return Optional.ofNullable(config.get(key));
    }

    @Override
    public boolean supportsRefresh() {
        return true;
    }

    @Override
    public boolean refresh() {
        return loadFromDirectory();
    }

    /**
     * Reads all mounted key files into the property map.
     *
     * @return {@code true} when the directory was read successfully
     */
    private boolean loadFromDirectory() {
        if (!Files.isDirectory(directory)) {
            log.warn("ConfigMap directory does not exist: {}", directory);
            return false;
        }
        Map<String, Object> loaded = new HashMap<>();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.forEach(entry -> {
                Path fileName = entry.getFileName();
                if (fileName == null) {
                    return;
                }
                String name = fileName.toString();
                // Skip Kubernetes atomic-update artifacts (..data, ..2024_..)
                if (name.startsWith("..")) {
                    return;
                }
                if (!Files.isRegularFile(entry)) {
                    return;
                }
                try {
                    String value = Files.readString(entry, StandardCharsets.UTF_8).trim();
                    loaded.put(name, value);
                } catch (IOException e) {
                    log.warn("Failed to read ConfigMap key file {}", entry, e);
                }
            });
        } catch (IOException e) {
            log.error("Failed to list ConfigMap directory {}", directory, e);
            return false;
        }
        this.config = loaded;
        log.info("Loaded {} properties from ConfigMap directory {}", loaded.size(), directory);
        return true;
    }

    /**
     * Registers a {@link WatchService} on the directory and reloads on change.
     */
    private void startWatching() {
        try {
            this.watchService = directory.getFileSystem().newWatchService();
            directory.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            this.watching = true;
            this.watchThread = new Thread(this::watchLoop, "adhar-configmap-watch");
            watchThread.setDaemon(true);
            watchThread.start();
            log.info("Watching ConfigMap directory {} for changes", directory);
        } catch (IOException e) {
            log.error("Failed to start ConfigMap directory watch on {}", directory, e);
        }
    }

    /**
     * Watch loop; reloads configuration on any filesystem event.
     */
    private void watchLoop() {
        while (watching) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                return;
            }
            if (!key.pollEvents().isEmpty()) {
                log.debug("ConfigMap directory {} changed - reloading", directory);
                loadFromDirectory();
            }
            if (!key.reset()) {
                log.warn("ConfigMap watch key for {} is no longer valid", directory);
                return;
            }
        }
    }

    /**
     * Stops watching and releases the {@link WatchService}.
     */
    @Override
    public void close() {
        this.watching = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.debug("Error closing ConfigMap watch service", e);
            }
        }
    }
}
