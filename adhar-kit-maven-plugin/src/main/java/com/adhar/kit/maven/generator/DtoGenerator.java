package com.adhar.kit.maven.generator;

import org.apache.maven.plugin.logging.Log;
import java.io.File;

public class DtoGenerator {
    private final String basePackage;
    private final String entityName;
    private final File outputDirectory;
    private final boolean useLombok;
    private final Log log;

    public DtoGenerator(String basePackage, String entityName, File outputDirectory,
                       boolean useLombok, Log log) {
        this.basePackage = basePackage;
        this.entityName = entityName;
        this.outputDirectory = outputDirectory;
        this.useLombok = useLombok;
        this.log = log;
    }

    public void generate() {
        log.info("Generating DTOs for: " + entityName);
        // TODO: Implement DTO generation
    }
}

