package com.adhar.kit.maven.generator;

import org.apache.maven.plugin.logging.Log;
import java.io.File;

public class RepositoryGenerator {
    private final String basePackage;
    private final String entityName;
    private final File outputDirectory;
    private final Log log;

    public RepositoryGenerator(String basePackage, String entityName, File outputDirectory, Log log) {
        this.basePackage = basePackage;
        this.entityName = entityName;
        this.outputDirectory = outputDirectory;
        this.log = log;
    }

    public void generate() {
        log.info("Generating repository for: " + entityName);
        // TODO: Implement repository generation
    }
}

