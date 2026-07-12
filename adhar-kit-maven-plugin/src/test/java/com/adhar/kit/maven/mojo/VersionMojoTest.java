package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.plugin.MojoExecutionException;
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
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link VersionMojo}. Uses in-process temp Git repositories.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class VersionMojoTest {

    private MavenProject project(File pomFile) {
        MavenProject project = mock(MavenProject.class);
        lenient().when(project.getVersion()).thenReturn("1.2.3-SNAPSHOT");
        lenient().when(project.getFile()).thenReturn(pomFile);
        return project;
    }

    private VersionMojo newMojo(MavenProject project, String gitDir, String type) {
        VersionMojo mojo = new VersionMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "gitDirectory", gitDir);
        TestSupport.setField(mojo, "versionType", type);
        TestSupport.setField(mojo, "createTag", false);
        TestSupport.setField(mojo, "pushTag", false);
        TestSupport.setField(mojo, "tagPrefix", "v");
        TestSupport.setField(mojo, "updatePom", false);
        TestSupport.setField(mojo, "snapshotSuffix", "SNAPSHOT");
        return mojo;
    }

    @Test
    void explicitPatchBumpWithoutPomUpdate(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            VersionMojo mojo = newMojo(project(null), dir.toString(), "patch");
            mojo.execute(); // 1.2.3-SNAPSHOT -> 1.2.4 (no exception)
        }
    }

    @Test
    void autoBumpFromFeatureCommit(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "feat: a new feature");
            VersionMojo mojo = newMojo(project(null), dir.toString(), "auto");
            mojo.execute();
        }
    }

    @Test
    void updatesPomAndCreatesTag(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");

            File pomFile = dir.resolve("pom.xml").toFile();
            Files.writeString(pomFile.toPath(),
                    "<project><version>1.2.3-SNAPSHOT</version></project>");

            MavenProject project = project(pomFile);
            VersionMojo mojo = newMojo(project, dir.toString(), "minor");
            TestSupport.setField(mojo, "updatePom", true);
            TestSupport.setField(mojo, "createTag", true);

            mojo.execute();

            // pom version element updated to 1.3.0
            assertThat(Files.readString(pomFile.toPath())).contains("1.3.0");
            verify(project).setVersion("1.3.0");
            assertThat(git.tagList().call()).anyMatch(r -> r.getName().endsWith("v1.3.0"));
        }
    }

    @Test
    void pushTagWithoutRemoteWrappedAsExecutionException(@TempDir Path dir) throws Exception {
        try (Git git = TestSupport.initRepo(dir)) {
            TestSupport.commit(git, "chore: init");
            VersionMojo mojo = newMojo(project(null), dir.toString(), "patch");
            TestSupport.setField(mojo, "createTag", true);
            TestSupport.setField(mojo, "pushTag", true);

            // Tag is created locally, but the push has no 'origin' remote configured,
            // so JGit fails immediately (no network) and the mojo wraps it.
            assertThatThrownBy(mojo::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("Version management failed");
            // The local tag was still created before the push attempt.
            assertThat(git.tagList().call()).anyMatch(r -> r.getName().endsWith("v1.2.4"));
        }
    }

    @Test
    void nonGitDirectoryWrappedAsExecutionException(@TempDir Path dir) {
        // No .git here -> SemanticVersionManager construction fails
        File plain = new File(dir.toFile(), "plain");
        plain.mkdirs();
        VersionMojo mojo = newMojo(project(null), plain.getAbsolutePath(), "patch");
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Version management failed");
    }
}
