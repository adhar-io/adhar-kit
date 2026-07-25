package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.dependency.DependencyReportAnalyzer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Maven Mojo for offline dependency hygiene checks.
 *
 * <p>Reports on:</p>
 * <ul>
 *   <li>Duplicate dependency declarations ({@code groupId:artifactId} declared
 *   more than once)</li>
 *   <li>Version convergence conflicts (a declared version that diverges from
 *   the version managed in {@code <dependencyManagement>}/BOM)</li>
 *   <li>Dependencies with hard-coded versions that are not managed by any
 *   BOM/parent POM</li>
 * </ul>
 *
 * <p>This goal performs <b>no network access</b>: it only reads the already
 * built Maven project model (declared dependencies and dependency management),
 * it does not resolve artifacts, query a repository, or download a CVE/OWASP
 * database.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * mvn adhar:deps
 * mvn adhar:deps -Ddeps.fail=true
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Mojo(name = "deps", defaultPhase = LifecyclePhase.VALIDATE)
public class DependencyReportMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Whether to fail the build when duplicate dependencies or version
     * convergence conflicts are found. Unmanaged (no-BOM) dependencies are
     * informational only and never fail the build.
     */
    @Parameter(property = "deps.fail", defaultValue = "false")
    private boolean failOnError;

    /**
     * Whether to write a dependency hygiene report file.
     */
    @Parameter(property = "deps.report", defaultValue = "true")
    private boolean generateReport;

    /**
     * Output file for the dependency hygiene report.
     */
    @Parameter(property = "deps.reportFile", defaultValue = "${project.build.directory}/adhar-dependency-report.txt")
    private File reportFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("====================================================");
        getLog().info("  Adhar Kit Dependency Hygiene Check");
        getLog().info("====================================================");

        try {
            DependencyReportAnalyzer analyzer = new DependencyReportAnalyzer(project, getLog());

            getLog().info("Checking for duplicate dependencies...");
            int duplicateCount = analyzer.findDuplicateDependencies();

            getLog().info("Checking for version convergence conflicts...");
            int convergenceCount = analyzer.findVersionConvergenceConflicts();

            getLog().info("Checking for dependencies not managed by a BOM...");
            int unmanagedCount = analyzer.findUnmanagedDependencies();

            if (generateReport) {
                analyzer.generateReport(reportFile);
                getLog().info("Dependency report generated: " + reportFile);
            }

            getLog().info("====================================================");
            getLog().info("  Dependency Hygiene Results");
            getLog().info("  Duplicate dependencies: " + duplicateCount);
            getLog().info("  Version convergence conflicts: " + convergenceCount);
            getLog().info("  Dependencies not managed by a BOM: " + unmanagedCount);
            getLog().info("====================================================");

            int failableIssues = duplicateCount + convergenceCount;
            if (failableIssues > 0 && failOnError) {
                throw new MojoFailureException(
                        "Dependency hygiene check failed with " + failableIssues
                                + " duplicate/convergence issue(s)");
            }

        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Dependency report failed: " + e.getMessage(), e);
        }
    }
}
