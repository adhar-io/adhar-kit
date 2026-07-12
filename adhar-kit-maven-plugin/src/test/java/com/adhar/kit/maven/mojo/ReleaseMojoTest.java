package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ReleaseMojo}.
 *
 * <p>The release flow validates the branch and working directory <em>before</em> it
 * would shell out to Maven for {@code validateTests()}. These tests deliberately make
 * the pre-conditions fail so the validation logic is exercised end-to-end without ever
 * invoking an external Maven/Git process.</p>
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class ReleaseMojoTest {

    private MavenProject projectFor(Path basedir) {
        MavenProject project = mock(MavenProject.class);
        lenient().when(project.getBasedir()).thenReturn(basedir.toFile());
        lenient().when(project.getName()).thenReturn("demo");
        lenient().when(project.getVersion()).thenReturn("1.2.3-SNAPSHOT");
        lenient().when(project.getArtifactId()).thenReturn("demo");
        return project;
    }

    private ReleaseMojo newMojo(MavenProject project, String releaseType, String releaseBranch, boolean dryRun) {
        ReleaseMojo mojo = new ReleaseMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "releaseType", releaseType);
        TestSupport.setField(mojo, "releaseBranch", releaseBranch);
        TestSupport.setField(mojo, "dryRun", dryRun);
        TestSupport.setField(mojo, "deploy", false);
        TestSupport.setField(mojo, "signArtifacts", false);
        TestSupport.setField(mojo, "generateChangelog", true);
        TestSupport.setField(mojo, "generateReleaseNotes", true);
        TestSupport.setField(mojo, "createRelease", false);
        TestSupport.setField(mojo, "skipTests", false);
        return mojo;
    }

    @Test
    void failsWhenOnDisallowedBranch(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            git.checkout().setCreateBranch(true).setName("feature/x").call();

            ReleaseMojo mojo = newMojo(projectFor(dir), "auto", "main", false);
            // validateBranch() rejects the feature branch before any Maven invocation.
            assertThatThrownBy(mojo::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("Release failed");
        }
    }

    @Test
    void dryRunStillValidatesAndFailsOnDirtyWorkingDirectory(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            // Dirty the working tree so validateWorkingDirectory() fails before validateTests().
            Files.writeString(dir.resolve("untracked.txt"), "x");

            // dryRun=true exercises the dry-run warning branch; we are on the default branch,
            // so validateBranch() passes and validateWorkingDirectory() is the failing step.
            ReleaseMojo mojo = newMojo(projectFor(dir), "auto", git.getRepository().getBranch(), true);
            assertThatThrownBy(mojo::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("Release failed");
        }
    }
}
