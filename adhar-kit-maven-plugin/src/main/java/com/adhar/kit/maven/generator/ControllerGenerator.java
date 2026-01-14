package com.adhar.kit.maven.generator;

import org.apache.maven.plugin.logging.Log;
import java.io.File;

public class ControllerGenerator {
    private final String basePackage;
    private final String entityName;
    private final File outputDirectory;
    private final boolean withOpenApi;
    private final Log log;

    public ControllerGenerator(String basePackage, String entityName, File outputDirectory,
                              boolean withOpenApi, Log log) {
        this.basePackage = basePackage;
        this.entityName = entityName;
        this.outputDirectory = outputDirectory;
        this.withOpenApi = withOpenApi;
        this.log = log;
    }

    public void generate() {
        log.info("Generating REST controller for: " + entityName);
        // TODO: Implement controller generation
    }
}

