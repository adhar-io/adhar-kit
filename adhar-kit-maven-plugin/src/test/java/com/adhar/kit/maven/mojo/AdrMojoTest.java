package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AdrMojo}.
 */
class AdrMojoTest {

    private AdrMojo newMojo(File adrDir, String title, String status) {
        MavenProject project = new MavenProject(new Model());
        project.setName("host");
        AdrMojo mojo = new AdrMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "adrDirectory", adrDir);
        TestSupport.setField(mojo, "title", title);
        TestSupport.setField(mojo, "status", status);
        return mojo;
    }

    @Test
    void createsAdrFileUnderConfiguredDirectory(@TempDir Path base) throws Exception {
        File adrDir = new File(base.toFile(), "docs/adr");
        AdrMojo mojo = newMojo(adrDir, "Use PostgreSQL for persistence", "Accepted");

        mojo.execute();

        File expected = new File(adrDir, "0001-use-postgresql-for-persistence.md");
        assertThat(expected).exists();
        assertThat(Files.readString(expected.toPath())).contains("Accepted");
    }

    @Test
    void wrapsFailuresInMojoExecutionException(@TempDir Path base) {
        // A blank title still slugs to "adr"; force failure by pointing at a
        // path whose parent is a regular file so the directory cannot be made.
        File notADir = new File(base.toFile(), "afile");
        try {
            Files.writeString(notADir.toPath(), "x");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        AdrMojo mojo = newMojo(new File(notADir, "adr"), "Title", "Proposed");

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class);
    }
}
