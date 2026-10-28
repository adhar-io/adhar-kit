package com.adhar.kit.maven.openapi;

import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.openapitools.codegen.config.GlobalSettings;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs openapi-generator for one {@link ApiSpec} with its preset's options.
 *
 * <p>Only APIs and models are generated: no supporting files, docs, tests or
 * {@code .openapi-generator} metadata, so the output directory holds nothing but the
 * sources the build compiles.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class OpenApiCodeGenerator {

    /**
     * openapi-generator keeps per-thread global settings and static caches; a parallel
     * build ({@code mvn -T}) must not run two generations at once.
     */
    private static final Object LOCK = new Object();

    /**
     * Generates the interfaces and models of {@code spec} into {@code outputDirectory}.
     *
     * @param spec            the spec and how to generate it
     * @param outputDirectory the source root to generate into
     * @param overrides       generator options that replace the preset's (an escape hatch,
     *                        e.g. {@code useOptional=true})
     * @return the files written
     */
    public List<File> generate(ApiSpec spec, File outputDirectory, Map<String, String> overrides) {
        Map<String, Object> options = new TreeMap<>(spec.preset().options());
        if (overrides != null) {
            options.putAll(overrides);
        }

        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(spec.preset().generatorName())
                .setLibrary(spec.preset().library())
                .setInputSpec(spec.file().toAbsolutePath().toString())
                .setOutputDir(outputDirectory.getAbsolutePath())
                .setApiPackage(spec.packageName())
                .setModelPackage(spec.modelPackage())
                .setInvokerPackage(spec.packageName())
                .setValidateSpec(true)
                .setAdditionalProperties(options);

        synchronized (LOCK) {
            try {
                ClientOptInput input = configurator.toClientOptInput();
                DefaultGenerator generator = new DefaultGenerator();
                generator.setGenerateMetadata(false);
                generator.setGeneratorPropertyDefault(CodegenConstants.APIS, "true");
                generator.setGeneratorPropertyDefault(CodegenConstants.MODELS, "true");
                generator.setGeneratorPropertyDefault(CodegenConstants.SUPPORTING_FILES, "false");
                generator.setGeneratorPropertyDefault(CodegenConstants.API_TESTS, "false");
                generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_TESTS, "false");
                generator.setGeneratorPropertyDefault(CodegenConstants.API_DOCS, "false");
                generator.setGeneratorPropertyDefault(CodegenConstants.MODEL_DOCS, "false");
                return generator.opts(input).generate();
            } finally {
                GlobalSettings.reset();
            }
        }
    }
}
