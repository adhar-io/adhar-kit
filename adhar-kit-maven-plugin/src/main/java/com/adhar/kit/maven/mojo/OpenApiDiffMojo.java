package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.openapi.ApiSpec;
import com.adhar.kit.maven.openapi.OpenApiCompatibility;
import com.adhar.kit.maven.openapi.SpecBaseline;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

/**
 * Fails the build when a spec breaks the clients of its major version.
 *
 * <p>Within {@code api/v<major>/openapi.yaml} every change must stay backward
 * compatible - removing an operation, adding a required request field or changing a
 * type breaks every client of {@code /api/v<major>}; such a change belongs in a new
 * major served alongside. The goal compares each of the service's specs with a
 * baseline - the spec at a git revision, or the one a released artifact shipped - and
 * writes every change (compatible ones too) to a Markdown report.</p>
 *
 * <p><b>Usage</b> (typically a CI step before release):</p>
 * <pre>{@code
 * mvn adhar:openapi-diff -Dadhar.openapi.diff.against=origin/main
 * mvn adhar:openapi-diff -Dadhar.openapi.diff.baseline=1.4.0
 * mvn adhar:openapi-diff -Dadhar.openapi.diff.baseline=com.example:orders:1.4.0
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Mojo(name = "openapi-diff", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class OpenApiDiffMojo extends AbstractOpenApiMojo {

    /**
     * Git revision to compare with: a branch ({@code origin/main}), tag or commit.
     */
    @Parameter(property = "adhar.openapi.diff.against")
    private String against;

    /**
     * Released artifact to compare with instead: {@code groupId:artifactId:version}, or
     * a version of this project.
     */
    @Parameter(property = "adhar.openapi.diff.baseline")
    private String baseline;

    /**
     * Whether a breaking change fails the build.
     */
    @Parameter(property = "adhar.openapi.diff.failOnBreaking", defaultValue = "true")
    private boolean failOnBreaking;

    /**
     * Whether a changed spec must also raise its {@code info.version}.
     */
    @Parameter(property = "adhar.openapi.diff.requireVersionBump", defaultValue = "false")
    private boolean requireVersionBump;

    /**
     * Where the report of every change is written.
     */
    @Parameter(property = "adhar.openapi.diff.report", defaultValue = "${project.build.directory}/openapi-diff.md")
    private File reportFile;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepositories;

    private final OpenApiCompatibility compatibility = new OpenApiCompatibility();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("OpenAPI diff skipped");
            return;
        }
        if (isBlank(against) && isBlank(baseline)) {
            getLog().info("OpenAPI diff has nothing to compare with: set adhar.openapi.diff.against (a git"
                    + " revision) or adhar.openapi.diff.baseline (a released version)");
            return;
        }
        List<ApiSpec> served = resolveSpecs().stream().filter(ApiSpec::shipped).toList();
        if (served.isEmpty()) {
            getLog().info("No OpenAPI spec of this service to compare");
            return;
        }

        int breaking = 0;
        int unbumped = 0;
        StringBuilder report = new StringBuilder();
        try (SpecBaseline source = openBaseline()) {
            for (ApiSpec spec : served) {
                String file = relative(spec.file());
                Optional<String> before = source.read(spec);
                if (before.isEmpty()) {
                    getLog().info(file + ": new since " + source.describe() + ", nothing to compare");
                    continue;
                }
                OpenApiCompatibility.Result result =
                        compatibility.compare(before.get(), Files.readString(spec.file(), StandardCharsets.UTF_8));
                report.append("# ").append(file).append(" against ").append(source.describe()).append("\n\n")
                        .append(result.report()).append("\n");

                if (result.breaking()) {
                    breaking++;
                    getLog().error(file + " breaks clients of /api/v" + spec.major() + " (built against "
                            + result.oldVersion() + " at " + source.describe() + "):");
                    result.breakingChanges().forEach(change -> getLog().error("  " + change));
                    getLog().error("  A breaking change needs a new major: api/v" + (spec.major() + 1)
                            + "/openapi.yaml, served alongside /api/v" + spec.major());
                } else if (result.changed() && !result.versionRaised()) {
                    unbumped++;
                    String message = file + " changed compatibly but info.version stayed "
                            + result.newVersion() + " (was " + result.oldVersion() + "); raise the minor"
                            + " for additions, the patch for fixes";
                    if (requireVersionBump) {
                        getLog().error(message);
                    } else {
                        getLog().warn(message);
                    }
                } else if (result.changed()) {
                    getLog().info(file + ": compatible with " + source.describe() + " ("
                            + result.oldVersion() + " -> " + result.newVersion() + ")");
                } else {
                    getLog().info(file + ": unchanged since " + source.describe());
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read the baseline specs: " + e.getMessage(), e);
        }

        writeReport(report);
        if (failOnBreaking && breaking > 0) {
            throw new MojoFailureException(breaking + " OpenAPI spec(s) break their clients; see the log above and "
                    + reportFile);
        }
        if (requireVersionBump && unbumped > 0) {
            throw new MojoFailureException(unbumped + " changed OpenAPI spec(s) kept their info.version");
        }
    }

    private SpecBaseline openBaseline() throws MojoExecutionException, IOException {
        if (!isBlank(against)) {
            return SpecBaseline.git(project.getBasedir().toPath(), against.trim());
        }
        String coordinates = baseline.contains(":")
                ? baseline.trim()
                : project.getGroupId() + ":" + project.getArtifactId() + ":" + baseline.trim();
        try {
            ArtifactRequest request = new ArtifactRequest(new DefaultArtifact(coordinates), remoteRepositories, null);
            File jar = repositorySystem.resolveArtifact(repositorySession, request).getArtifact().getFile();
            return SpecBaseline.jar(jar.toPath(), coordinates);
        } catch (ArtifactResolutionException | IllegalArgumentException e) {
            throw new MojoExecutionException("Cannot resolve the baseline artifact " + coordinates + ": "
                    + e.getMessage(), e);
        }
    }

    private void writeReport(StringBuilder report) throws MojoExecutionException {
        if (report.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(reportFile.toPath().getParent());
            Files.writeString(reportFile.toPath(), report, StandardCharsets.UTF_8);
            getLog().info("OpenAPI diff report: " + reportFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write " + reportFile + ": " + e.getMessage(), e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
