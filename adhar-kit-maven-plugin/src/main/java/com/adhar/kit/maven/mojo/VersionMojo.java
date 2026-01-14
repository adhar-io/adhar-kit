package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.versioning.SemanticVersionManager;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Maven Mojo for automatic semantic versioning based on Git commits.
 *
 * <p>Analyzes commit messages following Conventional Commits specification
 * and automatically determines the next version number.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * mvn adhar:version
 * mvn adhar:version -Dversion.type=major
 * mvn adhar:version -Dversion.tag=true
 * }</pre>
 *
 * <p><b>Commit Message Conventions:</b></p>
 * <ul>
 *   <li><code>feat:</code> - New feature (minor version bump)</li>
 *   <li><code>fix:</code> - Bug fix (patch version bump)</li>
 *   <li><code>BREAKING CHANGE:</code> - Breaking change (major version bump)</li>
 *   <li><code>chore:</code>, <code>docs:</code> - No version bump</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Mojo(name = "version", defaultPhase = LifecyclePhase.VALIDATE)
public class VersionMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Version bump type: major, minor, patch, auto.
     * Auto analyzes commit messages to determine version bump.
     */
    @Parameter(property = "version.type", defaultValue = "auto")
    private String versionType;

    /**
     * Whether to create a Git tag for the new version.
     */
    @Parameter(property = "version.tag", defaultValue = "false")
    private boolean createTag;

    /**
     * Whether to push the tag to remote repository.
     */
    @Parameter(property = "version.push", defaultValue = "false")
    private boolean pushTag;

    /**
     * Prefix for Git tags (e.g., 'v' creates tags like v1.0.0).
     */
    @Parameter(property = "version.tagPrefix", defaultValue = "v")
    private String tagPrefix;

    /**
     * Whether to update pom.xml with the new version.
     */
    @Parameter(property = "version.updatePom", defaultValue = "true")
    private boolean updatePom;

    /**
     * Base directory for Git operations.
     */
    @Parameter(property = "version.gitDir", defaultValue = "${project.basedir}")
    private String gitDirectory;

    /**
     * Snapshot suffix for development versions.
     */
    @Parameter(property = "version.snapshotSuffix", defaultValue = "SNAPSHOT")
    private String snapshotSuffix;

    private SemanticVersionManager versionManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("====================================================");
        getLog().info("  Adhar Kit Semantic Versioning");
        getLog().info("====================================================");

        try {
            versionManager = new SemanticVersionManager(gitDirectory, getLog());

            String currentVersion = project.getVersion();
            getLog().info("Current version: " + currentVersion);

            // Determine next version
            String nextVersion = determineNextVersion(currentVersion);
            getLog().info("Next version: " + nextVersion);

            // Update pom.xml if requested
            if (updatePom) {
                updateProjectVersion(nextVersion);
            }

            // Create Git tag if requested
            if (createTag) {
                createGitTag(nextVersion);
            }

            // Push tag if requested
            if (pushTag && createTag) {
                pushGitTag(nextVersion);
            }

            getLog().info("====================================================");
            getLog().info("  Versioning completed successfully");
            getLog().info("====================================================");

        } catch (Exception e) {
            throw new MojoExecutionException("Version management failed: " + e.getMessage(), e);
        }
    }

    /**
     * Determines the next version based on commit history and configuration.
     */
    private String determineNextVersion(String currentVersion) throws Exception {
        if ("auto".equals(versionType)) {
            return versionManager.calculateNextVersion(currentVersion);
        } else {
            return versionManager.bumpVersion(currentVersion, versionType);
        }
    }

    /**
     * Updates the project version in pom.xml.
     */
    private void updateProjectVersion(String newVersion) throws Exception {
        getLog().info("Updating pom.xml with version: " + newVersion);
        versionManager.updatePomVersion(project.getFile(), newVersion);
        project.setVersion(newVersion);
    }

    /**
     * Creates a Git tag for the new version.
     */
    private void createGitTag(String version) throws Exception {
        String tagName = tagPrefix + version;
        getLog().info("Creating Git tag: " + tagName);
        versionManager.createTag(tagName, "Release version " + version);
    }

    /**
     * Pushes the Git tag to remote repository.
     */
    private void pushGitTag(String version) throws Exception {
        String tagName = tagPrefix + version;
        getLog().info("Pushing Git tag to remote: " + tagName);
        versionManager.pushTag(tagName);
    }
}

