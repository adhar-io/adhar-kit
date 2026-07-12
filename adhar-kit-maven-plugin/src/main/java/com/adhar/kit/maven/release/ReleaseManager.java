package com.adhar.kit.maven.release;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manager for release operations.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class ReleaseManager {

    private final MavenProject project;
    private final Log log;

    public ReleaseManager(MavenProject project, Log log) {
        this.project = project;
        this.log = log;
    }

    /**
     * Validates that the current Git branch matches the expected release branch.
     *
     * @param expectedBranch the branch name to validate against (e.g., main, master, develop)
     * @throws Exception if the current branch does not match or git repo cannot be opened
     */
    public void validateBranch(String expectedBranch) throws Exception {
        log.info("Validating branch: " + expectedBranch);
        try (Repository repository = openRepository()) {
            String currentBranch = repository.getBranch();
            log.info("Current branch: " + currentBranch);

            Set<String> allowedBranches = new HashSet<>(Arrays.asList("main", "master", "develop"));
            allowedBranches.add(expectedBranch);

            if (!allowedBranches.contains(currentBranch)) {
                throw new Exception("Current branch '" + currentBranch
                        + "' is not an allowed release branch. Allowed: " + allowedBranches);
            }

            if (!currentBranch.equals(expectedBranch)) {
                log.warn("Current branch '" + currentBranch + "' differs from expected '"
                        + expectedBranch + "', but is in the allowed set.");
            }
        }
    }

    /**
     * Validates that the working directory has no uncommitted changes.
     *
     * @throws Exception if there are uncommitted changes or the repo cannot be opened
     */
    public void validateWorkingDirectory() throws Exception {
        log.info("Validating working directory is clean...");
        try (Repository repository = openRepository();
             Git git = new Git(repository)) {

            Status status = git.status().call();

            if (!status.isClean()) {
                List<String> issues = new ArrayList<>();
                if (!status.getUncommittedChanges().isEmpty()) {
                    issues.add("Uncommitted changes: " + status.getUncommittedChanges());
                }
                if (!status.getUntracked().isEmpty()) {
                    issues.add("Untracked files: " + status.getUntracked());
                }
                if (!status.getModified().isEmpty()) {
                    issues.add("Modified files: " + status.getModified());
                }
                if (!status.getAdded().isEmpty()) {
                    issues.add("Staged files: " + status.getAdded());
                }
                throw new Exception("Working directory is not clean:\n  " + String.join("\n  ", issues));
            }
            log.info("Working directory is clean.");
        }
    }

    /**
     * Validates that all tests pass by executing Maven test phase.
     *
     * @throws Exception if the tests fail or Maven cannot be executed
     */
    public void validateTests() throws Exception {
        log.info("Validating tests pass...");
        int exitCode = executeMavenCommand("test", "-q");
        if (exitCode != 0) {
            throw new Exception("Tests failed with exit code: " + exitCode
                    + ". Fix test failures before releasing.");
        }
        log.info("All tests passed.");
    }

    public String calculateReleaseVersion(String currentVersion) {
        // Remove SNAPSHOT and determine next version
        return currentVersion.replace("-SNAPSHOT", "");
    }

    /**
     * Bumps the version according to semantic versioning rules.
     *
     * @param currentVersion the current version string (e.g., 1.2.3-SNAPSHOT)
     * @param bumpType       the type of bump: major, minor, or patch
     * @return the bumped version string
     */
    public String bumpVersion(String currentVersion, String bumpType) {
        log.info("Bumping version: " + bumpType);

        String baseVersion = currentVersion.replace("-SNAPSHOT", "");
        Pattern semverPattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(.*)");
        Matcher matcher = semverPattern.matcher(baseVersion);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Version '" + currentVersion
                    + "' does not follow semantic versioning (major.minor.patch)");
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));

        switch (bumpType.toLowerCase()) {
            case "major":
                major++;
                minor = 0;
                patch = 0;
                break;
            case "minor":
                minor++;
                patch = 0;
                break;
            case "patch":
                patch++;
                break;
            default:
                throw new IllegalArgumentException("Unknown bump type: " + bumpType
                        + ". Must be one of: major, minor, patch");
        }

        String newVersion = major + "." + minor + "." + patch;
        log.info("Version bumped: " + currentVersion + " -> " + newVersion);
        return newVersion;
    }

    /**
     * Updates the project version in pom.xml.
     *
     * @param version the new version to set
     * @throws Exception if the pom.xml cannot be read or written
     */
    public void updateProjectVersion(String version) throws Exception {
        log.info("Updating project version: " + version);

        File pomFile = project.getFile();
        if (pomFile == null || !pomFile.exists()) {
            throw new Exception("Cannot find pom.xml for project: " + project.getName());
        }

        String content = new String(Files.readAllBytes(pomFile.toPath()));

        // Replace the version in the <version> tag that is a direct child of <project>
        // We need to be careful not to replace parent version or dependency versions
        // Strategy: replace the first <version> after <artifactId> that matches current version
        String currentVersion = project.getVersion();
        String regex = "(<artifactId>" + Pattern.quote(project.getArtifactId())
                + "</artifactId>\\s*<version>)" + Pattern.quote(currentVersion) + "(</version>)";
        String updatedContent = content.replaceFirst(regex, "$1" + version + "$2");

        if (updatedContent.equals(content)) {
            // Fallback: use mvn versions:set
            log.info("Direct pom.xml update did not match, using versions:set plugin...");
            int exitCode = executeMavenCommand("versions:set", "-DnewVersion=" + version,
                    "-DgenerateBackupPoms=false", "-q");
            if (exitCode != 0) {
                throw new Exception("Failed to update version using versions:set plugin");
            }
        } else {
            Files.write(pomFile.toPath(), updatedContent.getBytes());
            log.info("Updated pom.xml version to: " + version);
        }
    }

    /**
     * Runs Maven tests via ProcessBuilder.
     *
     * @throws Exception if the tests fail
     */
    public void runTests() throws Exception {
        log.info("Running tests...");
        int exitCode = executeMavenCommand("test");
        if (exitCode != 0) {
            throw new Exception("Test execution failed with exit code: " + exitCode);
        }
        log.info("All tests passed successfully.");
    }

    /**
     * Generates a changelog by inspecting Git commits since the last tag.
     *
     * @param version the version being released
     * @param file    the changelog file path
     * @throws Exception if the changelog cannot be generated
     */
    public void generateChangelog(String version, String file) throws Exception {
        log.info("Generating changelog for version: " + version);

        try (Repository repository = openRepository();
             Git git = new Git(repository)) {

            // Find the latest tag to determine the range of commits
            List<Ref> tags = git.tagList().call();
            ObjectId sinceCommit = null;

            if (!tags.isEmpty()) {
                Ref latestTag = tags.get(tags.size() - 1);
                sinceCommit = peelToCommit(repository, latestTag);
                log.info("Generating changelog since tag: " + latestTag.getName());
            }

            // Collect commits
            Iterable<RevCommit> commits;
            if (sinceCommit != null) {
                ObjectId head = repository.resolve("HEAD");
                commits = git.log().addRange(sinceCommit, head).call();
            } else {
                commits = git.log().call();
            }

            // Categorize commits by conventional commit type
            Map<String, List<String>> categorized = new LinkedHashMap<>();
            categorized.put("Features", new ArrayList<>());
            categorized.put("Bug Fixes", new ArrayList<>());
            categorized.put("Documentation", new ArrayList<>());
            categorized.put("Refactoring", new ArrayList<>());
            categorized.put("Other", new ArrayList<>());

            for (RevCommit commit : commits) {
                String msg = commit.getShortMessage();
                if (msg.startsWith("feat:") || msg.startsWith("feat(")) {
                    categorized.get("Features").add(cleanCommitMessage(msg));
                } else if (msg.startsWith("fix:") || msg.startsWith("fix(")) {
                    categorized.get("Bug Fixes").add(cleanCommitMessage(msg));
                } else if (msg.startsWith("docs:") || msg.startsWith("doc:")) {
                    categorized.get("Documentation").add(cleanCommitMessage(msg));
                } else if (msg.startsWith("refactor:") || msg.startsWith("refactor(")) {
                    categorized.get("Refactoring").add(cleanCommitMessage(msg));
                } else {
                    categorized.get("Other").add(msg);
                }
            }

            // Write changelog
            StringBuilder changelog = new StringBuilder();
            String date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now());
            changelog.append("## [").append(version).append("] - ").append(date).append("\n\n");

            for (Map.Entry<String, List<String>> entry : categorized.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    changelog.append("### ").append(entry.getKey()).append("\n\n");
                    for (String msg : entry.getValue()) {
                        changelog.append("- ").append(msg).append("\n");
                    }
                    changelog.append("\n");
                }
            }

            Path changelogPath = Path.of(file);
            String existingContent = "";
            if (Files.exists(changelogPath)) {
                existingContent = new String(Files.readAllBytes(changelogPath));
            }

            // Prepend new changelog entry after the header
            String header = "# Changelog\n\nAll notable changes to this project will be documented in this file.\n\n";
            String body = existingContent.startsWith("# Changelog")
                    ? existingContent.substring(existingContent.indexOf("\n\n") + 2)
                    : existingContent;

            Files.writeString(changelogPath, header + changelog + body);
            log.info("Changelog written to: " + file);
        }
    }

    /**
     * Generates release notes in markdown format from the changelog entries.
     *
     * @param version the version being released
     * @param file    the release notes file path
     * @throws Exception if the release notes cannot be generated
     */
    public void generateReleaseNotes(String version, String file) throws Exception {
        log.info("Generating release notes for version: " + version);

        try (Repository repository = openRepository();
             Git git = new Git(repository)) {

            // Collect commits since last tag
            List<Ref> tags = git.tagList().call();
            ObjectId sinceCommit = null;
            String previousTag = "initial";

            if (!tags.isEmpty()) {
                Ref latestTag = tags.get(tags.size() - 1);
                sinceCommit = peelToCommit(repository, latestTag);
                previousTag = latestTag.getName().replace("refs/tags/", "");
            }

            Iterable<RevCommit> commits;
            if (sinceCommit != null) {
                ObjectId head = repository.resolve("HEAD");
                commits = git.log().addRange(sinceCommit, head).call();
            } else {
                commits = git.log().setMaxCount(50).call();
            }

            List<String> commitMessages = new ArrayList<>();
            Set<String> contributors = new LinkedHashSet<>();
            for (RevCommit commit : commits) {
                commitMessages.add(commit.getShortMessage());
                contributors.add(commit.getAuthorIdent().getName());
            }

            // Build release notes
            String date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now());

            StringBuilder notes = new StringBuilder();
            notes.append("# Release Notes - v").append(version).append("\n\n");
            notes.append("**Release Date:** ").append(date).append("\n");
            notes.append("**Previous Version:** ").append(previousTag).append("\n\n");
            notes.append("## What's Changed\n\n");

            for (String msg : commitMessages) {
                notes.append("- ").append(msg).append("\n");
            }

            if (!contributors.isEmpty()) {
                notes.append("\n## Contributors\n\n");
                for (String contributor : contributors) {
                    notes.append("- ").append(contributor).append("\n");
                }
            }

            notes.append("\n---\n");
            notes.append("*Generated by Adhar Kit Maven Plugin*\n");

            Files.writeString(Path.of(file), notes.toString());
            log.info("Release notes written to: " + file);
        }
    }

    /**
     * Creates an annotated Git tag for the release.
     *
     * @param tagName the tag name (e.g., v1.2.3)
     * @param message the tag message
     * @throws Exception if the tag cannot be created
     */
    public void createTag(String tagName, String message) throws Exception {
        log.info("Creating tag: " + tagName);
        try (Repository repository = openRepository();
             Git git = new Git(repository)) {

            git.tag()
                    .setName(tagName)
                    .setMessage(message)
                    .setAnnotated(true)
                    .call();

            log.info("Created annotated tag: " + tagName + " with message: " + message);
        }
    }

    /**
     * Signs release artifacts. This is a placeholder since actual GPG signing
     * requires GPG keys and configuration that varies by environment.
     *
     * @throws Exception if signing fails
     */
    public void signArtifacts() throws Exception {
        log.info("Signing artifacts...");
        log.warn("GPG artifact signing is not yet implemented.");
        log.warn("To sign artifacts, configure the maven-gpg-plugin in your pom.xml:");
        log.warn("  <plugin>");
        log.warn("    <groupId>org.apache.maven.plugins</groupId>");
        log.warn("    <artifactId>maven-gpg-plugin</artifactId>");
        log.warn("    <executions><execution><goals><goal>sign</goal></goals></execution></executions>");
        log.warn("  </plugin>");
        log.info("Skipping artifact signing.");
    }

    /**
     * Deploys artifacts by executing Maven deploy phase.
     *
     * @throws Exception if deployment fails
     */
    public void deployArtifacts() throws Exception {
        log.info("Deploying artifacts...");
        int exitCode = executeMavenCommand("deploy", "-DskipTests");
        if (exitCode != 0) {
            throw new Exception("Artifact deployment failed with exit code: " + exitCode);
        }
        log.info("Artifacts deployed successfully.");
    }

    /**
     * Creates a GitHub release. This is a placeholder since it requires
     * a GitHub API token and the GitHub CLI or REST API.
     *
     * @param version   the release version
     * @param notesFile the path to the release notes file
     * @throws Exception if release creation fails
     */
    public void createGitHubRelease(String version, String notesFile) throws Exception {
        log.info("Creating GitHub release for version: " + version);
        log.warn("GitHub release creation requires the GitHub CLI (gh) or a GitHub API token.");
        log.warn("To create a release manually, run:");
        log.warn("  gh release create v" + version + " --title 'v" + version + "' --notes-file " + notesFile);
        log.info("Skipping GitHub release creation.");
    }

    // ---- Helper methods ----

    /**
     * Resolves a tag ref to the commit it points at. Annotated tags resolve to a
     * tag object rather than a commit, so the ref must be peeled before it can be
     * used as a commit boundary in a {@code git log} range.
     *
     * @param repository the repository the ref belongs to
     * @param tag        the tag ref to resolve
     * @return the commit {@link ObjectId} the tag ultimately points to
     */
    private ObjectId peelToCommit(Repository repository, Ref tag) throws IOException {
        Ref peeled = repository.getRefDatabase().peel(tag);
        return peeled.getPeeledObjectId() != null
                ? peeled.getPeeledObjectId()
                : peeled.getObjectId();
    }

    private Repository openRepository() throws IOException {
        File baseDir = project.getBasedir();
        return new FileRepositoryBuilder()
                .readEnvironment()
                .findGitDir(baseDir)
                .setMustExist(true)
                .build();
    }

    private int executeMavenCommand(String... args) throws Exception {
        String mvnCommand = System.getProperty("os.name").toLowerCase().contains("win")
                ? "mvn.cmd" : "mvn";

        List<String> command = new ArrayList<>();
        command.add(mvnCommand);
        Collections.addAll(command, args);

        log.info("Executing: " + String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(project.getBasedir())
                .redirectErrorStream(true);

        Process process = processBuilder.start();

        // Read output in a separate thread to avoid blocking
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug(line);
            }
        }

        boolean finished = process.waitFor(10, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Maven command timed out after 10 minutes");
        }

        return process.exitValue();
    }

    private String cleanCommitMessage(String message) {
        // Remove conventional commit prefix like "feat: " or "feat(scope): "
        return message.replaceFirst("^\\w+(?:\\([^)]*\\))?:\\s*", "");
    }
}
