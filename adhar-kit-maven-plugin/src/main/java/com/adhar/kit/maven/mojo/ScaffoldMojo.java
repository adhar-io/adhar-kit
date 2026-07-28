package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.generator.ServiceScaffoldGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Maven Mojo that scaffolds a runnable Spring Boot microservice skeleton built
 * on {@code adhar-kit-starter}.
 *
 * <p>Generates a standard Maven project (pom, application class, {@code
 * application.yml}, and a sample controller/service/test) under a target
 * directory, ready to build and run.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * mvn adhar:scaffold -Dscaffold.name=OrderService -Dscaffold.package=com.acme.orders
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Mojo(name = "scaffold", defaultPhase = LifecyclePhase.NONE)
public class ScaffoldMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Name of the service (used for the application class and project name),
     * e.g. {@code OrderService}.
     */
    @Parameter(property = "scaffold.name", required = true)
    private String name;

    /**
     * Base package for the generated sources.
     */
    @Parameter(property = "scaffold.package", defaultValue = "${project.groupId}")
    private String basePackage;

    /**
     * Maven artifactId for the generated service pom.
     */
    @Parameter(property = "scaffold.artifactId")
    private String artifactId;

    /**
     * Root directory into which the service skeleton is generated.
     */
    @Parameter(property = "scaffold.outputDir", defaultValue = "${project.build.directory}/generated-service")
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("====================================================");
        getLog().info("  Adhar Kit Microservice Scaffold");
        getLog().info("====================================================");

        String effectiveArtifactId = (artifactId != null && !artifactId.isBlank())
                ? artifactId
                : toArtifactId(name);

        getLog().info("Service: " + name);
        getLog().info("Package: " + basePackage);
        getLog().info("ArtifactId: " + effectiveArtifactId);
        getLog().info("Output: " + outputDirectory);
        getLog().info("====================================================");

        try {
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }
            ServiceScaffoldGenerator generator = new ServiceScaffoldGenerator(
                    basePackage, name, effectiveArtifactId, outputDirectory, getLog());
            generator.generate();

            getLog().info("====================================================");
            getLog().info("  Microservice scaffold completed successfully");
            getLog().info("  Generated project in: " + outputDirectory);
            getLog().info("====================================================");
        } catch (Exception e) {
            throw new MojoExecutionException("Microservice scaffold failed: " + e.getMessage(), e);
        }
    }

    /**
     * Derives a kebab-case artifactId from a CamelCase service name, e.g.
     * {@code OrderService -> order-service}.
     */
    static String toArtifactId(String name) {
        String kebab = name
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .toLowerCase();
        return kebab.replaceAll("(^-+)|(-+$)", "");
    }
}
