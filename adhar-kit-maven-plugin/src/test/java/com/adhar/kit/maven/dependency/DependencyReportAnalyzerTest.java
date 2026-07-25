package com.adhar.kit.maven.dependency;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link DependencyReportAnalyzer}. Builds a synthetic {@link MavenProject}
 * backed by a real {@link Model} (no network access, no artifact resolution) and
 * asserts on the duplicate/convergence/unmanaged findings.
 */
class DependencyReportAnalyzerTest {

    private final Log log = mock(Log.class);

    private Dependency dependency(String groupId, String artifactId, String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        return dependency;
    }

    private MavenProject projectWith(List<Dependency> dependencies, List<Dependency> managedDependencies) {
        Model model = new Model();
        model.setDependencies(dependencies);
        if (managedDependencies != null) {
            DependencyManagement dm = new DependencyManagement();
            dm.setDependencies(managedDependencies);
            model.setDependencyManagement(dm);
        }
        MavenProject project = new MavenProject(model);
        project.setName("demo-service");
        project.setVersion("1.0.0");
        return project;
    }

    @Test
    void noIssuesForCleanBomManagedDependencies() {
        MavenProject project = projectWith(
                List.of(dependency("org.slf4j", "slf4j-api", null)),
                List.of(dependency("org.slf4j", "slf4j-api", "2.0.9")));

        DependencyReportAnalyzer analyzer = new DependencyReportAnalyzer(project, log);

        assertThat(analyzer.findDuplicateDependencies()).isZero();
        assertThat(analyzer.findVersionConvergenceConflicts()).isZero();
        assertThat(analyzer.findUnmanagedDependencies()).isZero();
    }

    @Test
    void flagsDuplicateDependencyDeclaredTwice() {
        MavenProject project = projectWith(
                List.of(
                        dependency("com.google.guava", "guava", "32.0.0-jre"),
                        dependency("com.google.guava", "guava", "33.0.0-jre")),
                null);

        DependencyReportAnalyzer analyzer = new DependencyReportAnalyzer(project, log);

        assertThat(analyzer.findDuplicateDependencies()).isEqualTo(1);
        assertThat(analyzer.getDuplicates()).anyMatch(m -> m.contains("com.google.guava:guava"));
    }

    @Test
    void flagsVersionConvergenceConflictAgainstBom() {
        MavenProject project = projectWith(
                List.of(dependency("com.fasterxml.jackson.core", "jackson-databind", "2.15.0")),
                List.of(dependency("com.fasterxml.jackson.core", "jackson-databind", "2.17.0")));

        DependencyReportAnalyzer analyzer = new DependencyReportAnalyzer(project, log);

        assertThat(analyzer.findVersionConvergenceConflicts()).isEqualTo(1);
        assertThat(analyzer.getConvergenceConflicts())
                .anyMatch(m -> m.contains("2.15.0") && m.contains("2.17.0"));
        // A dependency that merely overrides the managed version is not "unmanaged" -
        // it has a management entry, it just diverges from it.
        assertThat(analyzer.findUnmanagedDependencies()).isZero();
    }

    @Test
    void flagsDependencyNotManagedByAnyBom() {
        MavenProject project = projectWith(
                List.of(dependency("org.apache.commons", "commons-lang3", "3.14.0")),
                List.of(dependency("org.slf4j", "slf4j-api", "2.0.9")));

        DependencyReportAnalyzer analyzer = new DependencyReportAnalyzer(project, log);

        assertThat(analyzer.findUnmanagedDependencies()).isEqualTo(1);
        assertThat(analyzer.getUnmanagedDependencies())
                .anyMatch(m -> m.contains("org.apache.commons:commons-lang3"));
        assertThat(analyzer.findVersionConvergenceConflicts()).isZero();
    }

    @Test
    void dependencyWithoutExplicitVersionIsNeverFlaggedAsUnmanaged() {
        MavenProject project = projectWith(
                List.of(dependency("org.projectlombok", "lombok", null)),
                null);

        DependencyReportAnalyzer analyzer = new DependencyReportAnalyzer(project, log);

        assertThat(analyzer.findUnmanagedDependencies()).isZero();
        assertThat(analyzer.findVersionConvergenceConflicts()).isZero();
    }

    @Test
    void emptyProjectHasNoFindings() {
        MavenProject project = projectWith(List.of(), null);

        DependencyReportAnalyzer analyzer = new DependencyReportAnalyzer(project, log);

        assertThat(analyzer.findDuplicateDependencies()).isZero();
        assertThat(analyzer.findVersionConvergenceConflicts()).isZero();
        assertThat(analyzer.findUnmanagedDependencies()).isZero();
    }

    @Test
    void generateReportWritesAllSections(@TempDir Path dir) throws Exception {
        MavenProject project = projectWith(
                List.of(
                        dependency("com.google.guava", "guava", "32.0.0-jre"),
                        dependency("com.google.guava", "guava", "33.0.0-jre"),
                        dependency("org.apache.commons", "commons-lang3", "3.14.0")),
                List.of(dependency("com.fasterxml.jackson.core", "jackson-databind", "2.17.0")));

        DependencyReportAnalyzer analyzer = new DependencyReportAnalyzer(project, log);
        analyzer.findDuplicateDependencies();
        analyzer.findVersionConvergenceConflicts();
        analyzer.findUnmanagedDependencies();

        File report = new File(dir.toFile(), "sub/dep-report.txt");
        analyzer.generateReport(report);

        assertThat(report).exists();
        String content = Files.readString(report.toPath());
        assertThat(content).contains("Adhar Kit Dependency Hygiene Report");
        assertThat(content).contains("demo-service");
        assertThat(content).contains("DUPLICATE DEPENDENCIES");
        assertThat(content).contains("VERSION CONVERGENCE CONFLICTS");
        assertThat(content).contains("DEPENDENCIES NOT MANAGED BY A BOM");
        assertThat(content).contains("com.google.guava:guava");
        assertThat(content).contains("org.apache.commons:commons-lang3");
    }
}
