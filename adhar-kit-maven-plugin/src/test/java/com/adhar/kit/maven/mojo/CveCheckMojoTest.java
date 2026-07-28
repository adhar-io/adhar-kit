package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
import com.adhar.kit.maven.cve.OssIndexClient;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CveCheckMojo}. A stubbed {@link OssIndexClient} feeds a
 * captured JSON fixture (or simulates an offline failure) so no real network
 * call is ever made.
 */
class CveCheckMojoTest {

    private String fixture() throws IOException {
        try (var in = getClass().getResourceAsStream("/cve/ossindex-component-report.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Artifact artifact(String groupId, String artifactId, String version) {
        Artifact artifact = mock(Artifact.class);
        when(artifact.getGroupId()).thenReturn(groupId);
        when(artifact.getArtifactId()).thenReturn(artifactId);
        when(artifact.getVersion()).thenReturn(version);
        return artifact;
    }

    private MavenProject projectWithArtifacts() {
        MavenProject project = new MavenProject(new Model());
        project.setName("cve-service");
        project.setVersion("1.0.0");
        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("org.apache.logging.log4j", "log4j-core", "2.14.1"));
        project.setArtifacts(artifacts);
        return project;
    }

    private CveCheckMojo newMojo(MavenProject project, File reportFile, double threshold, boolean fail) {
        CveCheckMojo mojo = new CveCheckMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "reportFile", reportFile);
        TestSupport.setField(mojo, "cvssThreshold", threshold);
        TestSupport.setField(mojo, "failOnError", fail);
        TestSupport.setField(mojo, "skip", false);
        return mojo;
    }

    @Test
    void failsWhenViolationAtOrAboveThresholdAndFailEnabled(@TempDir Path target) throws Exception {
        File report = new File(target.toFile(), "cve-report.txt");
        CveCheckMojo mojo = newMojo(projectWithArtifacts(), report, 7.0, true);
        mojo.setOssIndexClient(purls -> fixture());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("CVE check failed");

        assertThat(Files.readString(report.toPath())).contains("CVE-2021-44228");
    }

    @Test
    void warnsButDoesNotFailWhenFailDisabled(@TempDir Path target) throws Exception {
        File report = new File(target.toFile(), "cve-report.txt");
        CveCheckMojo mojo = newMojo(projectWithArtifacts(), report, 7.0, false);
        mojo.setOssIndexClient(purls -> fixture());

        mojo.execute(); // must not throw

        assertThat(Files.readString(report.toPath())).contains("VIOLATIONS (1)");
    }

    @Test
    void passesWhenThresholdAboveAllScores(@TempDir Path target) throws Exception {
        File report = new File(target.toFile(), "cve-report.txt");
        CveCheckMojo mojo = newMojo(projectWithArtifacts(), report, 10.1, true);
        mojo.setOssIndexClient(purls -> fixture());

        mojo.execute();

        assertThat(Files.readString(report.toPath())).contains("VIOLATIONS (0)");
    }

    @Test
    void offlineClientWarnsAndSkipsWithoutFailing(@TempDir Path target) throws Exception {
        File report = new File(target.toFile(), "cve-report.txt");
        CveCheckMojo mojo = newMojo(projectWithArtifacts(), report, 7.0, true);
        mojo.setOssIndexClient(purls -> {
            throw new IOException("Connection refused");
        });

        mojo.execute(); // offline must never fail the build

        assertThat(report).doesNotExist();
    }

    @Test
    void skipShortCircuits(@TempDir Path target) throws Exception {
        File report = new File(target.toFile(), "cve-report.txt");
        CveCheckMojo mojo = newMojo(projectWithArtifacts(), report, 7.0, true);
        TestSupport.setField(mojo, "skip", true);
        mojo.setOssIndexClient(purls -> {
            throw new AssertionError("client must not be called when skipped");
        });

        mojo.execute();

        assertThat(report).doesNotExist();
    }

    @Test
    void noDependenciesShortCircuits(@TempDir Path target) throws Exception {
        MavenProject project = new MavenProject(new Model());
        project.setName("empty-service");
        project.setArtifacts(new LinkedHashSet<>());
        File report = new File(target.toFile(), "cve-report.txt");
        CveCheckMojo mojo = newMojo(project, report, 7.0, true);
        mojo.setOssIndexClient(purls -> {
            throw new AssertionError("client must not be called with no dependencies");
        });

        mojo.execute();

        assertThat(report).doesNotExist();
    }
}
