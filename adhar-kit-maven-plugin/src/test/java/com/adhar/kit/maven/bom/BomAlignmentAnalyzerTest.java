package com.adhar.kit.maven.bom;

import org.apache.maven.artifact.Artifact;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BomAlignmentAnalyzer}. Managed versions come from an
 * in-memory {@link Model} and resolved versions from stubbed {@link Artifact}s;
 * no artifact resolution or network is involved.
 */
class BomAlignmentAnalyzerTest {

    private final Log log = mock(Log.class);

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

    private MavenProject project(DependencyManagement dm, Set<Artifact> artifacts) {
        Model model = new Model();
        model.setDependencyManagement(dm);
        MavenProject project = new MavenProject(model);
        project.setName("bom-service");
        project.setVersion("1.0.0");
        if (artifacts != null) {
            project.setArtifacts(artifacts);
        }
        return project;
    }

    @Test
    void managedVersionsAreReadFromDependencyManagement() {
        DependencyManagement dm = new DependencyManagement();
        dm.setDependencies(List.of(
                dependency("org.slf4j", "slf4j-api", "2.0.9"),
                dependency("com.google.guava", "guava", "33.0.0-jre")));
        BomAlignmentAnalyzer analyzer = new BomAlignmentAnalyzer(project(dm, null), log);

        assertThat(analyzer.managedVersions())
                .containsEntry("org.slf4j:slf4j-api", "2.0.9")
                .containsEntry("com.google.guava:guava", "33.0.0-jre");
    }

    @Test
    void resolvedVersionsAreReadFromArtifacts() {
        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("org.slf4j", "slf4j-api", "2.0.9"));
        BomAlignmentAnalyzer analyzer =
                new BomAlignmentAnalyzer(project(new DependencyManagement(), artifacts), log);

        assertThat(analyzer.resolvedVersions()).containsEntry("org.slf4j:slf4j-api", "2.0.9");
    }

    @Test
    void compareFlagsOnlyDivergentManagedArtifacts() {
        BomAlignmentAnalyzer analyzer =
                new BomAlignmentAnalyzer(project(new DependencyManagement(), null), log);

        Map<String, String> managed = Map.of(
                "org.slf4j:slf4j-api", "2.0.9",
                "com.google.guava:guava", "33.0.0-jre");
        Map<String, String> resolved = Map.of(
                "org.slf4j:slf4j-api", "2.0.9",          // aligned
                "com.google.guava:guava", "32.0.0-jre",  // misaligned
                "org.unmanaged:lib", "1.0.0");           // not managed -> ignored

        int count = analyzer.compare(managed, resolved);

        assertThat(count).isEqualTo(1);
        assertThat(analyzer.getMisalignments()).hasSize(1);
        assertThat(analyzer.getMisalignments().get(0)).contains("com.google.guava:guava");
    }

    @Test
    void findMisalignmentsEndToEndAndReport(@TempDir Path target) throws Exception {
        DependencyManagement dm = new DependencyManagement();
        dm.setDependencies(List.of(dependency("com.google.guava", "guava", "33.0.0-jre")));
        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("com.google.guava", "guava", "32.0.0-jre"));
        BomAlignmentAnalyzer analyzer = new BomAlignmentAnalyzer(project(dm, artifacts), log);

        assertThat(analyzer.findMisalignments()).isEqualTo(1);

        File report = new File(target.toFile(), "bom-report.txt");
        analyzer.generateReport(report);
        String content = Files.readString(report.toPath());
        assertThat(content).contains("Total Misalignments: 1");
        assertThat(content).contains("com.google.guava:guava");
    }

    @Test
    void cleanProjectHasNoMisalignments(@TempDir Path target) throws Exception {
        DependencyManagement dm = new DependencyManagement();
        dm.setDependencies(List.of(dependency("com.google.guava", "guava", "33.0.0-jre")));
        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("com.google.guava", "guava", "33.0.0-jre"));
        BomAlignmentAnalyzer analyzer = new BomAlignmentAnalyzer(project(dm, artifacts), log);

        assertThat(analyzer.findMisalignments()).isZero();
    }
}
