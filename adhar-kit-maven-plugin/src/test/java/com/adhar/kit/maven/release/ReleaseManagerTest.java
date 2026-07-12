package com.adhar.kit.maven.release;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ReleaseManager}. Uses in-process temp Git repositories created
 * via JGit. No external Maven processes are invoked.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class ReleaseManagerTest {

    private final Log log = mock(Log.class);

    private MavenProject projectFor(Path basedir) {
        MavenProject project = mock(MavenProject.class);
        lenient().when(project.getBasedir()).thenReturn(basedir.toFile());
        lenient().when(project.getName()).thenReturn("demo");
        lenient().when(project.getVersion()).thenReturn("1.2.3-SNAPSHOT");
        lenient().when(project.getArtifactId()).thenReturn("demo");
        return project;
    }

    // ---- Pure version helpers ----

    @Test
    void bumpVersionHandlesAllTypes() {
        ReleaseManager rm = new ReleaseManager(projectFor(Path.of(".")), log);
        assertThat(rm.bumpVersion("1.2.3-SNAPSHOT", "major")).isEqualTo("2.0.0");
        assertThat(rm.bumpVersion("1.2.3", "minor")).isEqualTo("1.3.0");
        assertThat(rm.bumpVersion("1.2.3", "patch")).isEqualTo("1.2.4");
    }

    @Test
    void bumpVersionRejectsInvalidType() {
        ReleaseManager rm = new ReleaseManager(projectFor(Path.of(".")), log);
        assertThatThrownBy(() -> rm.bumpVersion("1.2.3", "weird"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown bump type");
    }

    @Test
    void bumpVersionRejectsNonSemver() {
        ReleaseManager rm = new ReleaseManager(projectFor(Path.of(".")), log);
        assertThatThrownBy(() -> rm.bumpVersion("not-a-version", "patch"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("semantic versioning");
    }

    @Test
    void calculateReleaseVersionStripsSnapshot() {
        ReleaseManager rm = new ReleaseManager(projectFor(Path.of(".")), log);
        assertThat(rm.calculateReleaseVersion("1.2.3-SNAPSHOT")).isEqualTo("1.2.3");
    }

    // ---- Branch / working directory validation ----

    @Test
    void validateBranchPassesOnDefaultBranch(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            String branch = git.getRepository().getBranch();
            ReleaseManager rm = new ReleaseManager(projectFor(dir), log);
            // current branch equals expected -> no exception
            rm.validateBranch(branch);
        }
    }

    @Test
    void validateBranchWarnsWhenDifferentButAllowed(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            ReleaseManager rm = new ReleaseManager(projectFor(dir), log);
            // expected "develop" while we are on default (main/master) -> allowed, warns
            rm.validateBranch("develop");
        }
    }

    @Test
    void validateBranchFailsOnDisallowedBranch(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            git.checkout().setCreateBranch(true).setName("feature/x").call();
            ReleaseManager rm = new ReleaseManager(projectFor(dir), log);
            assertThatThrownBy(() -> rm.validateBranch("main"))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("not an allowed release branch");
        }
    }

    @Test
    void validateWorkingDirectoryPassesWhenClean(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            ReleaseManager rm = new ReleaseManager(projectFor(dir), log);
            rm.validateWorkingDirectory();
        }
    }

    @Test
    void validateWorkingDirectoryFailsWhenDirty(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            Files.writeString(dir.resolve("untracked.txt"), "x");
            ReleaseManager rm = new ReleaseManager(projectFor(dir), log);
            assertThatThrownBy(rm::validateWorkingDirectory)
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("not clean");
        }
    }

    @Test
    void validateWorkingDirectoryReportsModifiedAndStagedFiles(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            // Commit a tracked file, then modify it (-> modified/uncommitted)
            Files.writeString(dir.resolve("tracked.txt"), "v1");
            git.add().addFilepattern("tracked.txt").call();
            git.commit().setMessage("chore: add tracked")
                    .setAuthor("Test Author", "test@example.com")
                    .setCommitter("Test Author", "test@example.com").call();
            Files.writeString(dir.resolve("tracked.txt"), "v2");

            // Stage a brand-new file (-> added)
            Files.writeString(dir.resolve("staged.txt"), "new");
            git.add().addFilepattern("staged.txt").call();

            ReleaseManager rm = new ReleaseManager(projectFor(dir), log);
            assertThatThrownBy(rm::validateWorkingDirectory)
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("not clean")
                    .hasMessageContaining("staged.txt");
        }
    }

    // ---- Tags ----

    @Test
    void createTagCreatesAnnotatedTag(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            ReleaseManager rm = new ReleaseManager(projectFor(dir), log);
            rm.createTag("v9.9.9", "Release version 9.9.9");
            assertThat(git.tagList().call()).anyMatch(r -> r.getName().endsWith("v9.9.9"));
        }
    }

    // ---- Changelog / release notes ----

    @Test
    void generateChangelogCategorizesCommitsAndPrependsExisting(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            TestSupport.tag(git, "v1.0.0");
            TestSupport.commit(git, "feat: add login");
            TestSupport.commit(git, "fix: npe in parser");
            TestSupport.commit(git, "docs: update readme");
            TestSupport.commit(git, "refactor: tidy up");
            TestSupport.commit(git, "random commit");

            File changelog = dir.resolve("CHANGELOG.md").toFile();
            Files.writeString(changelog.toPath(),
                    "# Changelog\n\nAll notable changes to this project will be documented in this file.\n\n"
                            + "## [0.9.0] - 2020-01-01\n\n### Features\n\n- old\n\n");

            ReleaseManager rm = new ReleaseManager(projectFor(dir), log);
            rm.generateChangelog("2.0.0", changelog.getAbsolutePath());

            String content = Files.readString(changelog.toPath());
            assertThat(content).contains("## [2.0.0]");
            assertThat(content).contains("### Features");
            assertThat(content).contains("add login");
            assertThat(content).contains("### Bug Fixes");
            assertThat(content).contains("npe in parser");
            assertThat(content).contains("### Documentation");
            assertThat(content).contains("### Refactoring");
            assertThat(content).contains("### Other");
            // existing entry preserved
            assertThat(content).contains("[0.9.0]");
        }
    }

    @Test
    void generateChangelogWithoutTagsOrExistingFile(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "feat: first");
            ReleaseManager rm = new ReleaseManager(projectFor(dir), log);
            File changelog = dir.resolve("CHANGELOG.md").toFile();
            rm.generateChangelog("1.0.0", changelog.getAbsolutePath());
            assertThat(changelog).exists();
            assertThat(Files.readString(changelog.toPath())).contains("## [1.0.0]");
        }
    }

    @Test
    void generateReleaseNotesIncludesContributorsAndCommits(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            TestSupport.tag(git, "v1.0.0");
            TestSupport.commit(git, "feat: shiny");
            ReleaseManager rm = new ReleaseManager(projectFor(dir), log);
            File notes = dir.resolve("RELEASE_NOTES.md").toFile();
            rm.generateReleaseNotes("1.1.0", notes.getAbsolutePath());

            String content = Files.readString(notes.toPath());
            assertThat(content).contains("# Release Notes - v1.1.0");
            assertThat(content).contains("Previous Version:** v1.0.0");
            assertThat(content).contains("shiny");
            assertThat(content).contains("Contributors");
            assertThat(content).contains("Test Author");
        }
    }

    @Test
    void generateReleaseNotesWithoutTags(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "feat: only");
            ReleaseManager rm = new ReleaseManager(projectFor(dir), log);
            File notes = dir.resolve("RELEASE_NOTES.md").toFile();
            rm.generateReleaseNotes("0.1.0", notes.getAbsolutePath());
            assertThat(Files.readString(notes.toPath())).contains("Previous Version:** initial");
        }
    }

    // ---- Project version update (no Maven invocation path) ----

    @Test
    void updateProjectVersionRewritesPom(@TempDir Path dir) throws Exception {
        String pom = "<project>\n  <artifactId>demo</artifactId>\n  <version>1.2.3-SNAPSHOT</version>\n</project>\n";
        File pomFile = dir.resolve("pom.xml").toFile();
        Files.writeString(pomFile.toPath(), pom);

        MavenProject project = projectFor(dir);
        lenient().when(project.getFile()).thenReturn(pomFile);

        ReleaseManager rm = new ReleaseManager(project, log);
        rm.updateProjectVersion("1.2.3");

        assertThat(Files.readString(pomFile.toPath())).contains("<version>1.2.3</version>");
    }

    @Test
    void updateProjectVersionFailsWhenPomMissing(@TempDir Path dir) {
        MavenProject project = projectFor(dir);
        lenient().when(project.getFile()).thenReturn(null);
        ReleaseManager rm = new ReleaseManager(project, log);
        assertThatThrownBy(() -> rm.updateProjectVersion("1.2.3"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Cannot find pom.xml");
    }

    // ---- Placeholder operations (log only) ----

    @Test
    void signArtifactsAndGitHubReleaseAreSafePlaceholders(@TempDir Path dir) throws Exception {
        ReleaseManager rm = new ReleaseManager(projectFor(dir), log);
        rm.signArtifacts();
        rm.createGitHubRelease("1.0.0", "RELEASE_NOTES.md");
    }
}
