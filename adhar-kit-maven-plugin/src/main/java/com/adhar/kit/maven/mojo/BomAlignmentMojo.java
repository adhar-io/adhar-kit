package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.bom.BomAlignmentAnalyzer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Maven Mojo that verifies a project's resolved dependency versions are aligned
 * with the versions managed by the Adhar Kit BOM.
 *
 * <p>It compares each resolved artifact against the effective
 * {@code <dependencyManagement>} (where the imported BOM's version pins land)
 * and reports any dependency that resolved to a different version than the BOM
 * dictates. The build fails only when {@code bom.fail} is set.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * mvn adhar:bom
 * mvn adhar:bom -Dbom.fail=true
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Mojo(name = "bom", defaultPhase = LifecyclePhase.VALIDATE, requiresDependencyResolution = ResolutionScope.TEST)
public class BomAlignmentMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Whether to fail the build when one or more BOM misalignments are found.
     */
    @Parameter(property = "bom.fail", defaultValue = "false")
    private boolean failOnError;

    /**
     * Whether to write a BOM alignment report file.
     */
    @Parameter(property = "bom.report", defaultValue = "true")
    private boolean generateReport;

    /**
     * Output file for the BOM alignment report.
     */
    @Parameter(property = "bom.reportFile", defaultValue = "${project.build.directory}/adhar-bom-alignment-report.txt")
    private File reportFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("====================================================");
        getLog().info("  Adhar Kit BOM Alignment Check");
        getLog().info("====================================================");

        try {
            BomAlignmentAnalyzer analyzer = new BomAlignmentAnalyzer(project, getLog());

            getLog().info("Comparing resolved versions against BOM-managed versions...");
            int misalignmentCount = analyzer.findMisalignments();

            if (generateReport) {
                analyzer.generateReport(reportFile);
                getLog().info("BOM alignment report generated: " + reportFile);
            }

            getLog().info("====================================================");
            getLog().info("  BOM Alignment Results");
            getLog().info("  Misalignments: " + misalignmentCount);
            getLog().info("====================================================");

            if (misalignmentCount > 0 && failOnError) {
                throw new MojoFailureException(
                        "BOM alignment check failed with " + misalignmentCount + " misalignment(s)");
            }

        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("BOM alignment check failed: " + e.getMessage(), e);
        }
    }
}
