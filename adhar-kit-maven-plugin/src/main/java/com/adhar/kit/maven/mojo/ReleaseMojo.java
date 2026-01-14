package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.release.ReleaseManager;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Maven Mojo for automated release management.
 *
 * <p>Automates the release process including:</p>
 * <ul>
 *   <li>Version bumping and validation</li>
 *   <li>Changelog generation from commits</li>
 *   <li>Git tag creation</li>
 *   <li>Artifact signing</li>
 *   <li>Deployment to repositories</li>
 *   <li>Release notes generation</li>
 *   <li>Rollback capabilities</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * mvn adhar:release
 * mvn adhar:release -Drelease.type=major
 * mvn adhar:release -Drelease.deploy=true
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Mojo(name = "release", defaultPhase = LifecyclePhase.DEPLOY)
public class ReleaseMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Release type: major, minor, patch, auto.
     */
    @Parameter(property = "release.type", defaultValue = "auto")
    private String releaseType;

    /**
     * Whether to deploy artifacts after release.
     */
    @Parameter(property = "release.deploy", defaultValue = "false")
    private boolean deploy;

    /**
     * Whether to sign artifacts.
     */
    @Parameter(property = "release.sign", defaultValue = "false")
    private boolean signArtifacts;

    /**
     * Whether to generate changelog.
     */
    @Parameter(property = "release.changelog", defaultValue = "true")
    private boolean generateChangelog;

    /**
     * Whether to generate release notes.
     */
    @Parameter(property = "release.notes", defaultValue = "true")
    private boolean generateReleaseNotes;

    /**
     * Whether to create GitHub/GitLab release.
     */
    @Parameter(property = "release.createRelease", defaultValue = "false")
    private boolean createRelease;

    /**
     * Release notes file location.
     */
    @Parameter(property = "release.notesFile", defaultValue = "${project.basedir}/RELEASE_NOTES.md")
    private String releaseNotesFile;

    /**
     * Changelog file location.
     */
    @Parameter(property = "release.changelogFile", defaultValue = "${project.basedir}/CHANGELOG.md")
    private String changelogFile;

    /**
     * Whether to perform dry run (no actual changes).
     */
    @Parameter(property = "release.dryRun", defaultValue = "false")
    private boolean dryRun;

    /**
     * Branch to release from.
     */
    @Parameter(property = "release.branch", defaultValue = "main")
    private String releaseBranch;

    /**
     * Whether to skip tests during release.
     */
    @Parameter(property = "release.skipTests", defaultValue = "false")
    private boolean skipTests;

    private ReleaseManager releaseManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("====================================================");
        getLog().info("  Adhar Kit Release Manager");
        getLog().info("====================================================");
        getLog().info("Project: " + project.getName());
        getLog().info("Current version: " + project.getVersion());
        getLog().info("Release type: " + releaseType);

        if (dryRun) {
            getLog().warn("DRY RUN MODE - No actual changes will be made");
        }

        getLog().info("====================================================");

        try {
            releaseManager = new ReleaseManager(project, getLog());

            // Validate preconditions
            validateRelease();

            // Determine release version
            String releaseVersion = determineReleaseVersion();
            getLog().info("Release version: " + releaseVersion);

            if (!dryRun) {
                // Update version
                updateVersion(releaseVersion);

                // Run tests
                if (!skipTests) {
                    runTests();
                }

                // Generate changelog
                if (generateChangelog) {
                    generateChangelog(releaseVersion);
                }

                // Generate release notes
                if (generateReleaseNotes) {
                    generateReleaseNotes(releaseVersion);
                }

                // Create Git tag
                createReleaseTag(releaseVersion);

                // Sign artifacts
                if (signArtifacts) {
                    signArtifacts();
                }

                // Deploy artifacts
                if (deploy) {
                    deployArtifacts();
                }

                // Create GitHub/GitLab release
                if (createRelease) {
                    createGitRelease(releaseVersion);
                }
            }

            getLog().info("====================================================");
            getLog().info("  Release completed successfully: v" + releaseVersion);
            getLog().info("====================================================");

        } catch (Exception e) {
            throw new MojoExecutionException("Release failed: " + e.getMessage(), e);
        }
    }

    private void validateRelease() throws Exception {
        getLog().info("Validating release preconditions...");
        releaseManager.validateBranch(releaseBranch);
        releaseManager.validateWorkingDirectory();
        releaseManager.validateTests();
    }

    private String determineReleaseVersion() throws Exception {
        if ("auto".equals(releaseType)) {
            return releaseManager.calculateReleaseVersion(project.getVersion());
        } else {
            return releaseManager.bumpVersion(project.getVersion(), releaseType);
        }
    }

    private void updateVersion(String version) throws Exception {
        getLog().info("Updating project version to: " + version);
        releaseManager.updateProjectVersion(version);
    }

    private void runTests() throws Exception {
        getLog().info("Running tests...");
        releaseManager.runTests();
    }

    private void generateChangelog(String version) throws Exception {
        getLog().info("Generating changelog...");
        releaseManager.generateChangelog(version, changelogFile);
    }

    private void generateReleaseNotes(String version) throws Exception {
        getLog().info("Generating release notes...");
        releaseManager.generateReleaseNotes(version, releaseNotesFile);
    }

    private void createReleaseTag(String version) throws Exception {
        getLog().info("Creating release tag: v" + version);
        releaseManager.createTag("v" + version, "Release version " + version);
    }

    private void signArtifacts() throws Exception {
        getLog().info("Signing artifacts...");
        releaseManager.signArtifacts();
    }

    private void deployArtifacts() throws Exception {
        getLog().info("Deploying artifacts...");
        releaseManager.deployArtifacts();
    }

    private void createGitRelease(String version) throws Exception {
        getLog().info("Creating Git release...");
        releaseManager.createGitHubRelease(version, releaseNotesFile);
    }
}

