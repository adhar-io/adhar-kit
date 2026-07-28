package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.cve.CveAnalyzer;
import com.adhar.kit.maven.cve.CveFinding;
import com.adhar.kit.maven.cve.HttpOssIndexClient;
import com.adhar.kit.maven.cve.OssIndexClient;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Maven Mojo that checks the project's resolved dependencies for known
 * vulnerabilities using the public Sonatype OSS Index REST API.
 *
 * <p>Every resolved artifact is described as a {@code pkg:maven} package URL
 * and submitted to {@code POST
 * https://ossindex.sonatype.org/api/v3/component-report} (no authentication is
 * required for modest volumes). Findings whose CVSS score meets or exceeds a
 * configurable threshold are reported; the build fails only when {@code
 * cve.fail} is set.</p>
 *
 * <p>Responses are cached under {@code target/} to avoid re-querying, and the
 * goal degrades gracefully when the service is offline or unreachable - it logs
 * a warning and skips rather than failing the build.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * mvn adhar:cve
 * mvn adhar:cve -Dcve.threshold=9.0 -Dcve.fail=true
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Mojo(name = "cve", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.TEST)
public class CveCheckMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Minimum CVSS score (0.0-10.0) at which a vulnerability is reported as a
     * violation.
     */
    @Parameter(property = "cve.threshold", defaultValue = "7.0")
    private double cvssThreshold;

    /**
     * Whether to fail the build when one or more violations at/above the
     * threshold are found. When {@code false}, violations are logged as
     * warnings only.
     */
    @Parameter(property = "cve.fail", defaultValue = "false")
    private boolean failOnError;

    /**
     * Whether to skip the CVE check entirely.
     */
    @Parameter(property = "cve.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Directory under which OSS Index responses are cached.
     */
    @Parameter(property = "cve.cacheDir", defaultValue = "${project.build.directory}/adhar-cve-cache")
    private File cacheDir;

    /**
     * Output file for the CVE report.
     */
    @Parameter(property = "cve.reportFile", defaultValue = "${project.build.directory}/adhar-cve-report.txt")
    private File reportFile;

    /** Injected for testing; defaults to the real HTTP OSS Index client. */
    private OssIndexClient ossIndexClient;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Adhar Kit CVE check skipped (cve.skip=true)");
            return;
        }

        getLog().info("====================================================");
        getLog().info("  Adhar Kit CVE Check (Sonatype OSS Index)");
        getLog().info("====================================================");

        CveAnalyzer analyzer = new CveAnalyzer();
        List<String> purls = analyzer.buildPurls(project);
        getLog().info("Resolved dependencies to scan: " + purls.size());

        if (purls.isEmpty()) {
            getLog().info("No resolved dependencies to scan.");
            return;
        }

        OssIndexClient client = ossIndexClient != null
                ? ossIndexClient
                : new HttpOssIndexClient(cacheDir, getLog());

        List<CveFinding> violations;
        try {
            violations = analyzer.analyze(client, purls, cvssThreshold);
        } catch (IOException e) {
            // Offline / unreachable / transient service error: warn, do not fail.
            getLog().warn("OSS Index unreachable, skipping CVE check: " + e.getMessage());
            return;
        }

        for (CveFinding violation : violations) {
            getLog().warn("VULNERABLE: " + violation);
        }

        writeReport(purls.size(), violations);

        getLog().info("====================================================");
        getLog().info("  CVE Check Results");
        getLog().info("  Threshold (CVSS >=): " + cvssThreshold);
        getLog().info("  Violations: " + violations.size());
        getLog().info("====================================================");

        if (!violations.isEmpty() && failOnError) {
            throw new MojoFailureException(
                    "CVE check failed with " + violations.size()
                            + " vulnerability(ies) at or above CVSS " + cvssThreshold);
        }
    }

    private void writeReport(int scanned, List<CveFinding> violations) throws MojoExecutionException {
        if (reportFile == null) {
            return;
        }
        File parent = reportFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write("================================================================================\n");
            writer.write("  Adhar Kit CVE Report (Sonatype OSS Index)\n");
            writer.write("  Project: " + project.getName() + "\n");
            writer.write("  Dependencies scanned: " + scanned + "\n");
            writer.write("  CVSS threshold: " + cvssThreshold + "\n");
            writer.write("================================================================================\n\n");
            writer.write("VIOLATIONS (" + violations.size() + "):\n");
            writer.write("--------------------------------------------------------------------------------\n");
            for (CveFinding violation : violations) {
                writer.write("  - " + violation + "\n");
                if (violation.getReference() != null && !violation.getReference().isBlank()) {
                    writer.write("      " + violation.getReference() + "\n");
                }
            }
            writer.write("\n");
        } catch (IOException e) {
            throw new MojoExecutionException("Could not write CVE report: " + e.getMessage(), e);
        }
    }

    /** Package-visible setter used by tests to inject a stubbed client. */
    void setOssIndexClient(OssIndexClient ossIndexClient) {
        this.ossIndexClient = ossIndexClient;
    }
}
