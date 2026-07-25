package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.generator.ServiceGenerator;
import com.adhar.kit.maven.generator.ControllerGenerator;
import com.adhar.kit.maven.generator.RepositoryGenerator;
import com.adhar.kit.maven.generator.DtoGenerator;
import com.adhar.kit.maven.generator.EntityGenerator;
import com.adhar.kit.maven.generator.IntegrationTestGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Maven Mojo for generating boilerplate code for enterprise microservices.
 *
 * <p>Generates standard classes following Adhar Kit patterns:</p>
 * <ul>
 *   <li>REST Controllers with OpenAPI annotations</li>
 *   <li>Service interfaces and implementations</li>
 *   <li>JPA Repositories with QueryDSL support</li>
 *   <li>DTOs with validation annotations</li>
 *   <li>Entity classes with auditing</li>
 *   <li>Exception handlers</li>
 *   <li>Integration tests</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * mvn adhar:generate -Dgenerate.entity=User
 * mvn adhar:generate -Dgenerate.type=service -Dgenerate.name=UserService
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Type of code to generate: entity, service, controller, repository, dto, all.
     */
    @Parameter(property = "generate.type", required = true)
    private String type;

    /**
     * Name of the class/entity to generate.
     */
    @Parameter(property = "generate.name", required = true)
    private String name;

    /**
     * Base package for generated classes.
     */
    @Parameter(property = "generate.package", defaultValue = "${project.groupId}")
    private String basePackage;

    /**
     * Output directory for generated sources.
     */
    @Parameter(property = "generate.outputDir", defaultValue = "${project.build.directory}/generated-sources/adhar")
    private File outputDirectory;

    /**
     * Whether to generate tests along with source code.
     */
    @Parameter(property = "generate.withTests", defaultValue = "true")
    private boolean generateTests;

    /**
     * Whether to use Lombok annotations.
     */
    @Parameter(property = "generate.useLombok", defaultValue = "true")
    private boolean useLombok;

    /**
     * Whether to generate OpenAPI documentation.
     */
    @Parameter(property = "generate.withOpenApi", defaultValue = "true")
    private boolean withOpenApi;

    /**
     * Database table name (for entity generation).
     */
    @Parameter(property = "generate.tableName")
    private String tableName;

    /**
     * Output directory for generated test sources (used when {@code generate.withTests}
     * is enabled, e.g. for the {@code all} generation type).
     */
    @Parameter(property = "generate.testOutputDir",
            defaultValue = "${project.build.directory}/generated-test-sources/adhar")
    private File testOutputDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("====================================================");
        getLog().info("  Adhar Kit Code Generator");
        getLog().info("====================================================");
        getLog().info("Type: " + type);
        getLog().info("Name: " + name);
        getLog().info("Package: " + basePackage);
        getLog().info("====================================================");

        try {
            // Ensure output directory exists
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            // Generate based on type
            switch (type.toLowerCase()) {
                case "entity":
                    generateEntity();
                    break;
                case "service":
                    generateService();
                    break;
                case "controller":
                    generateController();
                    break;
                case "repository":
                    generateRepository();
                    break;
                case "dto":
                    generateDto();
                    break;
                case "all":
                    generateAll();
                    break;
                default:
                    throw new MojoExecutionException("Unknown generation type: " + type);
            }

            // Add generated sources to project
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());

            getLog().info("====================================================");
            getLog().info("  Code generation completed successfully");
            getLog().info("  Generated files in: " + outputDirectory);
            getLog().info("====================================================");

        } catch (Exception e) {
            throw new MojoExecutionException("Code generation failed: " + e.getMessage(), e);
        }
    }

    private void generateEntity() throws Exception {
        getLog().info("Generating entity: " + name);
        EntityGenerator generator = new EntityGenerator(
                basePackage, name, outputDirectory, useLombok, tableName, getLog()
        );
        generator.generate();
    }

    private void generateService() throws Exception {
        getLog().info("Generating service: " + name);
        ServiceGenerator generator = new ServiceGenerator(
                basePackage, name, outputDirectory, useLombok, getLog()
        );
        generator.generate();
    }

    private void generateController() throws Exception {
        getLog().info("Generating controller: " + name);
        ControllerGenerator generator = new ControllerGenerator(
                basePackage, name, outputDirectory, withOpenApi, getLog()
        );
        generator.generate();
    }

    private void generateRepository() throws Exception {
        getLog().info("Generating repository: " + name);
        RepositoryGenerator generator = new RepositoryGenerator(
                basePackage, name, outputDirectory, getLog()
        );
        generator.generate();
    }

    private void generateDto() throws Exception {
        getLog().info("Generating DTO: " + name);
        DtoGenerator generator = new DtoGenerator(
                basePackage, name, outputDirectory, useLombok, getLog()
        );
        generator.generate();
    }

    private void generateAll() throws Exception {
        getLog().info("Generating all components for: " + name);
        generateEntity();
        generateRepository();
        generateService();
        generateController();
        generateDto();

        if (generateTests) {
            getLog().info("Generating tests...");
            if (!testOutputDirectory.exists()) {
                testOutputDirectory.mkdirs();
            }
            IntegrationTestGenerator testGenerator = new IntegrationTestGenerator(
                    basePackage, name, testOutputDirectory, getLog()
            );
            testGenerator.generate();
            project.addTestCompileSourceRoot(testOutputDirectory.getAbsolutePath());
        }
    }
}

