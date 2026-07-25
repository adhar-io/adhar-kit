package com.adhar.kit.maven.versioning;

import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.Semver.SemverType;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Manager for semantic versioning operations.
 *
 * <p>Implements Semantic Versioning 2.0.0 specification and analyzes
 * commit messages following Conventional Commits format.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class SemanticVersionManager {

    private static final Pattern BREAKING_CHANGE = Pattern.compile("BREAKING[\\s-]CHANGE", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEAT_PATTERN = Pattern.compile("^feat(\\(.+\\))?:", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIX_PATTERN = Pattern.compile("^fix(\\(.+\\))?:", Pattern.CASE_INSENSITIVE);

    private final String gitDirectory;
    private final Log log;
    private Git git;
    private Repository repository;

    /**
     * Creates a new semantic version manager.
     *
     * @param gitDirectory the Git repository directory
     * @param log Maven logger
     */
    public SemanticVersionManager(String gitDirectory, Log log) throws Exception {
        this.gitDirectory = gitDirectory;
        this.log = log;
        initializeGitRepository();
    }

    /**
     * Initializes the Git repository.
     */
    private void initializeGitRepository() throws Exception {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        repository = builder
                .setGitDir(new File(gitDirectory, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
        // FileRepositoryBuilder.build() succeeds even when no repository exists;
        // fail fast so callers get a clear error for non-Git directories.
        if (repository.getObjectDatabase() == null || !repository.getObjectDatabase().exists()) {
            throw new IllegalStateException("Not a Git repository: " + gitDirectory);
        }
        git = new Git(repository);
    }

    /**
     * Calculates the next version by analyzing commit messages since last tag.
     *
     * @param currentVersion the current version
     * @return the next version
     */
    public String calculateNextVersion(String currentVersion) throws Exception {
        log.info("Analyzing commit history for version bump...");

        // Get commits since last tag
        List<RevCommit> commits = getCommitsSinceLastTag();
        log.info("Found " + commits.size() + " commits since last tag");

        if (commits.isEmpty()) {
            log.info("No new commits, keeping current version");
            return currentVersion;
        }

        // Analyze commits
        boolean hasBreakingChange = false;
        boolean hasFeature = false;
        boolean hasFix = false;

        for (RevCommit commit : commits) {
            String message = commit.getFullMessage();

            if (BREAKING_CHANGE.matcher(message).find()) {
                hasBreakingChange = true;
                log.info("Found BREAKING CHANGE in: " + commit.getShortMessage());
            } else if (FEAT_PATTERN.matcher(message).find()) {
                hasFeature = true;
                log.info("Found feature in: " + commit.getShortMessage());
            } else if (FIX_PATTERN.matcher(message).find()) {
                hasFix = true;
                log.info("Found fix in: " + commit.getShortMessage());
            }
        }

        // Determine version bump
        String bumpType;
        if (hasBreakingChange) {
            bumpType = "major";
        } else if (hasFeature) {
            bumpType = "minor";
        } else if (hasFix) {
            bumpType = "patch";
        } else {
            log.info("No version-affecting changes found");
            return currentVersion;
        }

        return bumpVersion(currentVersion, bumpType);
    }

    /**
     * Bumps the version according to the specified type.
     *
     * @param currentVersion the current version
     * @param bumpType major, minor, or patch
     * @return the bumped version
     */
    public String bumpVersion(String currentVersion, String bumpType) {
        // Remove SNAPSHOT suffix if present
        String cleanVersion = currentVersion.replace("-SNAPSHOT", "");

        Semver version = new Semver(cleanVersion, SemverType.LOOSE);
        Semver nextVersion;

        switch (bumpType.toLowerCase()) {
            case "major":
                nextVersion = version.nextMajor();
                log.info("Bumping MAJOR version");
                break;
            case "minor":
                nextVersion = version.nextMinor();
                log.info("Bumping MINOR version");
                break;
            case "patch":
                nextVersion = version.nextPatch();
                log.info("Bumping PATCH version");
                break;
            default:
                throw new IllegalArgumentException("Invalid bump type: " + bumpType);
        }

        return nextVersion.toString();
    }

    /**
     * Gets all commits since the last tag.
     */
    private List<RevCommit> getCommitsSinceLastTag() throws Exception {
        List<RevCommit> commits = new ArrayList<>();
        Iterable<RevCommit> log = git.log().call();

        // Find last tag
        String lastTag = getLastTag();

        for (RevCommit commit : log) {
            if (lastTag != null && commit.getName().startsWith(lastTag)) {
                break;
            }
            commits.add(commit);
        }

        return commits;
    }

    /**
     * Gets the last (highest) Git tag, sorted using semantic-version comparison
     * rather than assuming {@code git.tagList()} returns tags in version order
     * (it returns them in lexicographic ref-name order, which is wrong for
     * versions like "v1.9.0" vs "v1.10.0").
     */
    String getLastTag() throws Exception {
        List<String> tagNames = new ArrayList<>();
        git.tagList().call().forEach(ref -> tagNames.add(ref.getName().replace("refs/tags/", "")));

        if (tagNames.isEmpty()) {
            return null;
        }

        String highestTagName = null;
        Semver highestVersion = null;

        for (String tagName : tagNames) {
            Semver candidate = parseSemver(tagName);
            if (candidate == null) {
                continue;
            }
            if (highestVersion == null || candidate.isGreaterThan(highestVersion)) {
                highestVersion = candidate;
                highestTagName = tagName;
            }
        }

        if (highestTagName != null) {
            return highestTagName;
        }

        // None of the tags parsed as semver (e.g. non-version tags); fall back to
        // a stable lexicographic ordering rather than raw ref-list order.
        List<String> sorted = new ArrayList<>(tagNames);
        Collections.sort(sorted);
        return sorted.get(sorted.size() - 1);
    }

    /**
     * Parses a tag name as a semantic version, tolerating a leading "v"/"V" prefix.
     * Returns {@code null} if the tag does not look like a semantic version.
     */
    private static Semver parseSemver(String tagName) {
        String candidate = tagName;
        if (!candidate.isEmpty() && (candidate.charAt(0) == 'v' || candidate.charAt(0) == 'V')) {
            candidate = candidate.substring(1);
        }
        try {
            return new Semver(candidate, SemverType.LOOSE);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Updates the version in pom.xml.
     *
     * @param pomFile the pom.xml file
     * @param newVersion the new version
     */
    public void updatePomVersion(File pomFile, String newVersion) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomFile);

        // Find and update version element
        NodeList versionNodes = doc.getElementsByTagName("version");
        if (versionNodes.getLength() > 0) {
            Element versionElement = (Element) versionNodes.item(0);
            versionElement.setTextContent(newVersion);
        }

        // Write back to file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(pomFile);
        transformer.transform(source, result);

        log.info("Updated pom.xml version to: " + newVersion);
    }

    /**
     * Creates a Git tag.
     *
     * @param tagName the tag name
     * @param message the tag message
     */
    public void createTag(String tagName, String message) throws Exception {
        git.tag()
                .setName(tagName)
                .setMessage(message)
                .call();
        log.info("Created tag: " + tagName);
    }

    /**
     * Pushes a Git tag to remote.
     *
     * @param tagName the tag name
     */
    public void pushTag(String tagName) throws Exception {
        git.push()
                .add(tagName)
                .call();
        log.info("Pushed tag to remote: " + tagName);
    }

    /**
     * Closes the Git repository.
     */
    public void close() {
        if (git != null) {
            git.close();
        }
        if (repository != null) {
            repository.close();
        }
    }
}

