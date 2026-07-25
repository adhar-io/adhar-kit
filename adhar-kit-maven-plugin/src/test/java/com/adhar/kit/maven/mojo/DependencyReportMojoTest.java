package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DependencyReportMojo}. Uses a real {@link MavenProject} backed
 * by an in-memory {@link Model} (no artifact resolution, no network) so that
 * {@code project.getDependencies()} / {@code getDependencyManagement()} behave
 * exactly as they would for a real project's declared dependencies.
 */
class DependencyReportMojoTest {

    private Dependency dependency(String groupId, String artifactId, String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        return dependency;
    }

    private DependencyReportMojo newMojo(MavenProject project, File reportFile, boolean failOnError) {
        DependencyReportMojo mojo = new DependencyReportMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "reportFile", reportFile);
        TestSupport.setField(mojo, "failOnError", failOnError);
        TestSupport.setField(mojo, "generateReport", true);
        return mojo;
    }

    @Test
    void cleanProjectWritesReportAndDoesNotFail(@TempDir Path target) throws Exception {
        Model model = new Model();
        model.setDependencies(List.of(dependency("org.slf4j", "slf4j-api", null)));
        DependencyManagement dm = new DependencyManagement();
        dm.setDependencies(List.of(dependency("org.slf4j", "slf4j-api", "2.0.9")));
        model.setDependencyManagement(dm);
        MavenProject project = new MavenProject(model);
        project.setName("clean-service");
        project.setVersion("1.0.0");

        File report = new File(target.toFile(), "dep-report.txt");
        DependencyReportMojo mojo = newMojo(project, report, true);

        mojo.execute();

        assertThat(report).exists();
        assertThat(Files.readString(report.toPath())).contains("Total Issues: 0");
    }

    @Test
    void failOnErrorThrowsWhenDuplicatesPresent(@TempDir Path target) throws Exception {
        Model model = new Model();
        model.setDependencies(List.of(
                dependency("com.google.guava", "guava", "32.0.0-jre"),
                dependency("com.google.guava", "guava", "33.0.0-jre")));
        MavenProject project = new MavenProject(model);
        project.setName("dup-service");
        project.setVersion("1.0.0");

        File report = new File(target.toFile(), "dep-report.txt");
        DependencyReportMojo mojo = newMojo(project, report, true);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Dependency hygiene check failed");
    }

    @Test
    void doesNotFailWhenFailOnErrorIsFalse(@TempDir Path target) throws Exception {
        Model model = new Model();
        model.setDependencies(List.of(
                dependency("com.google.guava", "guava", "32.0.0-jre"),
                dependency("com.google.guava", "guava", "33.0.0-jre")));
        MavenProject project = new MavenProject(model);
        project.setName("dup-service");
        project.setVersion("1.0.0");

        File report = new File(target.toFile(), "dep-report.txt");
        DependencyReportMojo mojo = newMojo(project, report, false);

        mojo.execute(); // Should not throw.

        assertThat(Files.readString(report.toPath())).contains("com.google.guava:guava");
    }

    @Test
    void unmanagedDependencyAloneNeverFailsTheBuild(@TempDir Path target) throws Exception {
        Model model = new Model();
        model.setDependencies(List.of(dependency("org.apache.commons", "commons-lang3", "3.14.0")));
        MavenProject project = new MavenProject(model);
        project.setName("unmanaged-service");
        project.setVersion("1.0.0");

        File report = new File(target.toFile(), "dep-report.txt");
        DependencyReportMojo mojo = newMojo(project, report, true);

        mojo.execute(); // Unmanaged findings are informational only - must not fail.

        assertThat(Files.readString(report.toPath())).contains("org.apache.commons:commons-lang3");
    }
}
