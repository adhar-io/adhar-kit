package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.validation.ArchitectureRuleValidator;
import com.adhar.kit.maven.validation.CodeStandardsValidator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Maven Mojo for validating code standards and best practices.
 *
 * <p>Validates enterprise microservices standards:</p>
 * <ul>
 *   <li>Package structure conventions</li>
 *   <li>Naming conventions</li>
 *   <li>Required annotations presence</li>
 *   <li>API documentation completeness</li>
 *   <li>Exception handling patterns</li>
 *   <li>Logging usage</li>
 *   <li>Test coverage requirements</li>
 *   <li>Security annotations</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * mvn adhar:validate
 * mvn adhar:validate -Dvalidate.fail=true
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Mojo(name = "validate", defaultPhase = LifecyclePhase.VALIDATE)
public class ValidateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Source directory to validate.
     */
    @Parameter(property = "validate.sourceDir", defaultValue = "${project.build.sourceDirectory}")
    private File sourceDirectory;

    /**
     * Whether to fail the build on validation errors.
     */
    @Parameter(property = "validate.fail", defaultValue = "false")
    private boolean failOnError;

    /**
     * Whether to validate package structure.
     */
    @Parameter(property = "validate.packageStructure", defaultValue = "true")
    private boolean validatePackageStructure;

    /**
     * Whether to validate naming conventions.
     */
    @Parameter(property = "validate.naming", defaultValue = "true")
    private boolean validateNaming;

    /**
     * Whether to validate annotations.
     */
    @Parameter(property = "validate.annotations", defaultValue = "true")
    private boolean validateAnnotations;

    /**
     * Whether to validate API documentation.
     */
    @Parameter(property = "validate.documentation", defaultValue = "true")
    private boolean validateDocumentation;

    /**
     * Minimum test coverage percentage.
     */
    @Parameter(property = "validate.minCoverage", defaultValue = "80")
    private int minCoverage;

    /**
     * Whether to validate exception handling.
     */
    @Parameter(property = "validate.exceptions", defaultValue = "true")
    private boolean validateExceptions;

    /**
     * Whether to validate logging usage.
     */
    @Parameter(property = "validate.logging", defaultValue = "true")
    private boolean validateLogging;

    /**
     * Whether to validate layered-architecture dependency direction
     * (controller -&gt; service -&gt; repository, no reverse dependencies).
     */
    @Parameter(property = "validate.architecture", defaultValue = "true")
    private boolean validateArchitecture;

    /**
     * Whether to generate validation report.
     */
    @Parameter(property = "validate.report", defaultValue = "true")
    private boolean generateReport;

    /**
     * Output file for validation report.
     */
    @Parameter(property = "validate.reportFile", defaultValue = "${project.build.directory}/adhar-validation-report.txt")
    private File reportFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("====================================================");
        getLog().info("  Adhar Kit Code Standards Validator");
        getLog().info("====================================================");

        try {
            CodeStandardsValidator validator = new CodeStandardsValidator(
                    sourceDirectory, project, getLog()
            );

            int errorCount = 0;
            int warningCount = 0;

            // Validate package structure
            if (validatePackageStructure) {
                getLog().info("Validating package structure...");
                errorCount += validator.validatePackageStructure();
            }

            // Validate naming conventions
            if (validateNaming) {
                getLog().info("Validating naming conventions...");
                errorCount += validator.validateNamingConventions();
            }

            // Validate annotations
            if (validateAnnotations) {
                getLog().info("Validating annotations...");
                errorCount += validator.validateAnnotations();
            }

            // Validate documentation
            if (validateDocumentation) {
                getLog().info("Validating API documentation...");
                warningCount += validator.validateDocumentation();
            }

            // Validate exception handling
            if (validateExceptions) {
                getLog().info("Validating exception handling...");
                errorCount += validator.validateExceptionHandling();
            }

            // Validate logging
            if (validateLogging) {
                getLog().info("Validating logging usage...");
                warningCount += validator.validateLogging();
            }

            // Validate layered-architecture dependency direction
            if (validateArchitecture) {
                getLog().info("Validating architecture layering (controller -> service -> repository)...");
                ArchitectureRuleValidator architectureValidator =
                        new ArchitectureRuleValidator(sourceDirectory, getLog());
                errorCount += architectureValidator.validateLayering();
            }

            // Generate report
            if (generateReport) {
                validator.generateReport(reportFile);
                getLog().info("Validation report generated: " + reportFile);
            }

            getLog().info("====================================================");
            getLog().info("  Validation Results");
            getLog().info("  Errors: " + errorCount);
            getLog().info("  Warnings: " + warningCount);
            getLog().info("====================================================");

            if (errorCount > 0 && failOnError) {
                throw new MojoFailureException(
                        "Code validation failed with " + errorCount + " errors"
                );
            }

        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Validation failed: " + e.getMessage(), e);
        }
    }
}

