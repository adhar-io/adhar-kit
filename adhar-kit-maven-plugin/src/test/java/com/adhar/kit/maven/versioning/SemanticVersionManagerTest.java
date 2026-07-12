package com.adhar.kit.maven.versioning;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SemanticVersionManager}. Uses in-process temp Git repositories
 * created via JGit. No external Maven/Git processes are invoked.
 */
class SemanticVersionManagerTest {

    private final Log log = mock(Log.class);

    /**
     * Creates a manager backed by a real (empty) in-process Git repository.
     */
    private SemanticVersionManager managerOn(Path dir) throws Exception {
        TestSupport.initRepo(dir).close();
        return new SemanticVersionManager(dir.toString(), log);
    }

    // ---- bumpVersion (pure semver math) ----

    @Test
    void testBumpMajorVersion(@TempDir Path dir) throws Exception {
        SemanticVersionManager manager = managerOn(dir);
        assertThat(manager.bumpVersion("1.2.3", "major")).isEqualTo("2.0.0");
    }

    @Test
    void testBumpMinorVersion(@TempDir Path dir) throws Exception {
        SemanticVersionManager manager = managerOn(dir);
        assertThat(manager.bumpVersion("1.2.3", "minor")).isEqualTo("1.3.0");
    }

    @Test
    void testBumpPatchVersion(@TempDir Path dir) throws Exception {
        SemanticVersionManager manager = managerOn(dir);
        assertThat(manager.bumpVersion("1.2.3", "patch")).isEqualTo("1.2.4");
    }

    @Test
    void testBumpVersionWithSnapshot(@TempDir Path dir) throws Exception {
        SemanticVersionManager manager = managerOn(dir);
        assertThat(manager.bumpVersion("1.2.3-SNAPSHOT", "minor")).isEqualTo("1.3.0");
    }

    @Test
    void testInvalidBumpType(@TempDir Path dir) throws Exception {
        SemanticVersionManager manager = managerOn(dir);
        assertThatThrownBy(() -> manager.bumpVersion("1.2.3", "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid bump type");
    }

    // ---- Constructor / repository discovery ----

    @Test
    void testConstructorRejectsNonGitDirectory(@TempDir Path dir) {
        File plain = new File(dir.toFile(), "plain");
        plain.mkdirs();
        assertThatThrownBy(() -> new SemanticVersionManager(plain.getAbsolutePath(), log))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not a Git repository");
    }

    // ---- calculateNextVersion (commit analysis) ----

    @Test
    void calculateNextVersionDetectsBreakingChange(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "feat: add api\n\nBREAKING CHANGE: removed old endpoint");
        }
        SemanticVersionManager manager = new SemanticVersionManager(dir.toString(), log);
        assertThat(manager.calculateNextVersion("1.2.3")).isEqualTo("2.0.0");
    }

    @Test
    void calculateNextVersionDetectsFeature(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "feat: brand new thing");
        }
        SemanticVersionManager manager = new SemanticVersionManager(dir.toString(), log);
        assertThat(manager.calculateNextVersion("1.2.3")).isEqualTo("1.3.0");
    }

    @Test
    void calculateNextVersionDetectsFix(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "fix: correct calculation");
        }
        SemanticVersionManager manager = new SemanticVersionManager(dir.toString(), log);
        assertThat(manager.calculateNextVersion("1.2.3")).isEqualTo("1.2.4");
    }

    @Test
    void calculateNextVersionKeepsVersionWhenNoConventionalCommits(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: tidy up build");
        }
        SemanticVersionManager manager = new SemanticVersionManager(dir.toString(), log);
        assertThat(manager.calculateNextVersion("1.2.3")).isEqualTo("1.2.3");
    }

    @Test
    void calculateNextVersionWalksCommitsWhenTagPresent(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            TestSupport.tag(git, "v1.0.0");
            TestSupport.commit(git, "feat: shiny");
        }
        // Exercises getLastTag()/getCommitsSinceLastTag() with a non-empty tag list.
        SemanticVersionManager manager = new SemanticVersionManager(dir.toString(), log);
        assertThat(manager.calculateNextVersion("1.0.0")).isEqualTo("1.1.0");
    }

    // ---- Pom update + tagging + lifecycle ----

    @Test
    void updatePomVersionRewritesFirstVersionElement(@TempDir Path dir) throws Exception {
        SemanticVersionManager manager = managerOn(dir);
        File pom = dir.resolve("pom.xml").toFile();
        Files.writeString(pom.toPath(),
                "<project><version>1.0.0-SNAPSHOT</version></project>");
        manager.updatePomVersion(pom, "2.5.0");
        assertThat(Files.readString(pom.toPath())).contains("2.5.0");
    }

    @Test
    void createTagCreatesGitTag(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            SemanticVersionManager manager = new SemanticVersionManager(dir.toString(), log);
            manager.createTag("v3.0.0", "Release version 3.0.0");
            assertThat(git.tagList().call()).anyMatch(r -> r.getName().endsWith("v3.0.0"));
            manager.close();
        }
    }

    @Test
    void pushTagWithoutRemoteFailsFast(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            SemanticVersionManager manager = new SemanticVersionManager(dir.toString(), log);
            manager.createTag("v4.0.0", "Release version 4.0.0");
            // No 'origin' remote configured -> JGit fails immediately, no network.
            assertThatThrownBy(() -> manager.pushTag("v4.0.0"))
                    .isInstanceOf(Exception.class);
            manager.close();
        }
    }

    @Test
    void closeIsIdempotentAndSafe(@TempDir Path dir) throws Exception {
        SemanticVersionManager manager = managerOn(dir);
        manager.close();
        // Second close must not throw.
        manager.close();
    }
}
