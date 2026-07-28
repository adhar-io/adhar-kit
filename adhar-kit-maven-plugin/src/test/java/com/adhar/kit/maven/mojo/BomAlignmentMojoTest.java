package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BomAlignmentMojo}, backed by an in-memory model with managed
 * versions and stubbed resolved artifacts.
 */
class BomAlignmentMojoTest {

    private Dependency dependency(String groupId, String artifactId, String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        return dependency;
    }

    private Artifact artifact(String groupId, String artifactId, String version) {
        Artifact artifact = mock(Artifact.class);
        when(artifact.getGroupId()).thenReturn(groupId);
        when(artifact.getArtifactId()).thenReturn(artifactId);
        when(artifact.getVersion()).thenReturn(version);
        return artifact;
    }

    private MavenProject project(String managedVersion, String resolvedVersion) {
        DependencyManagement dm = new DependencyManagement();
        dm.setDependencies(List.of(dependency("com.google.guava", "guava", managedVersion)));
        Model model = new Model();
        model.setDependencyManagement(dm);
        MavenProject project = new MavenProject(model);
        project.setName("bom-service");
        project.setVersion("1.0.0");
        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("com.google.guava", "guava", resolvedVersion));
        project.setArtifacts(artifacts);
        return project;
    }

    private BomAlignmentMojo newMojo(MavenProject project, File reportFile, boolean fail) {
        BomAlignmentMojo mojo = new BomAlignmentMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "reportFile", reportFile);
        TestSupport.setField(mojo, "failOnError", fail);
        TestSupport.setField(mojo, "generateReport", true);
        return mojo;
    }

    @Test
    void failsOnMisalignmentWhenFailEnabled(@TempDir Path target) {
        File report = new File(target.toFile(), "bom-report.txt");
        BomAlignmentMojo mojo = newMojo(project("33.0.0-jre", "32.0.0-jre"), report, true);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("BOM alignment check failed");
    }

    @Test
    void doesNotFailWhenAligned(@TempDir Path target) throws Exception {
        File report = new File(target.toFile(), "bom-report.txt");
        BomAlignmentMojo mojo = newMojo(project("33.0.0-jre", "33.0.0-jre"), report, true);

        mojo.execute();

        assertThat(Files.readString(report.toPath())).contains("Total Misalignments: 0");
    }

    @Test
    void doesNotFailWhenFailDisabledEvenWithMisalignment(@TempDir Path target) throws Exception {
        File report = new File(target.toFile(), "bom-report.txt");
        BomAlignmentMojo mojo = newMojo(project("33.0.0-jre", "32.0.0-jre"), report, false);

        mojo.execute();

        assertThat(Files.readString(report.toPath())).contains("com.google.guava:guava");
    }
}
