package com.adhar.kit.batch.repository;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.resource.LoadableResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The migrations directly inside one classpath directory, and NOT in its subdirectories.
 *
 * <p>Flyway scans a classpath location recursively, and the kit's two migration streams nest: the
 * Quartz stream ({@code .../db/postgresql/quartz}, design 0025 §5.20.3) lives inside the main
 * stream's location ({@code .../db/postgresql}). Scanned recursively, the main stream would apply
 * {@code V2_5_2__quartz_tables.sql} to every service, including those on the engine {@code simple},
 * and record it in the wrong history. Each stream therefore lists its own directory only. A
 * resource's relative path is its file name, as Flyway's own scanner records a file directly in the
 * location, so the histories of existing schemas validate unchanged.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
final class DirectoryMigrations implements ResourceProvider {

    private static final String CLASSPATH = "classpath:";

    private final String directory;
    private final PathMatchingResourcePatternResolver resolver;

    /**
     * @param location    a Flyway location, {@code classpath:<directory>}
     * @param classLoader the class loader to read it from
     */
    DirectoryMigrations(String location, ClassLoader classLoader) {
        if (!location.startsWith(CLASSPATH)) {
            throw new IllegalArgumentException("not a classpath location: " + location);
        }
        this.directory = location.substring(CLASSPATH.length());
        this.resolver = new PathMatchingResourcePatternResolver(classLoader);
    }

    @Override
    public LoadableResource getResource(String name) {
        String filename = name.substring(name.lastIndexOf('/') + 1);
        return all().stream().filter(resource -> resource.getFilename().equals(filename)).findFirst().orElse(null);
    }

    @Override
    public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
        List<LoadableResource> matching = new ArrayList<>();
        for (DirectoryResource resource : all()) {
            String filename = resource.getFilename();
            if (!filename.startsWith(prefix)) {
                continue;
            }
            for (String suffix : suffixes) {
                if (filename.endsWith(suffix)) {
                    matching.add(resource);
                    break;
                }
            }
        }
        return matching;
    }

    private List<DirectoryResource> all() {
        Resource[] found;
        try {
            // One '*' matches inside the directory only; Spring's '**' would recurse.
            found = resolver.getResources("classpath*:" + directory + "/*");
        } catch (IOException ex) {
            throw new FlywayException("Could not list the migrations in " + directory + ": " + ex.getMessage(), ex);
        }
        List<DirectoryResource> resources = new ArrayList<>();
        for (Resource resource : found) {
            String filename = resource.getFilename();
            if (filename != null && resource.isReadable()) {
                resources.add(new DirectoryResource(directory, filename, resource));
            }
        }
        return resources;
    }

    /**
     * One migration file.
     */
    private static final class DirectoryResource extends LoadableResource {

        private final String directory;
        private final String filename;
        private final Resource resource;

        private DirectoryResource(String directory, String filename, Resource resource) {
            this.directory = directory;
            this.filename = filename;
            this.resource = resource;
        }

        @Override
        public Reader read() {
            try {
                return new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new UncheckedIOException("Could not read migration " + getAbsolutePath(), ex);
            }
        }

        @Override
        public String getAbsolutePath() {
            return directory + "/" + filename;
        }

        @Override
        public String getAbsolutePathOnDisk() {
            try {
                return resource.getURL().toString();
            } catch (IOException ex) {
                return getAbsolutePath();
            }
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public String getRelativePath() {
            return filename;
        }
    }
}
