package com.adhar.kit.maven.cve;

import org.apache.maven.artifact.Artifact;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the network-free CVE logic: purl building, OSS Index response
 * parsing (against a captured JSON fixture), and CVSS threshold filtering. No
 * real HTTP is performed - the {@link OssIndexClient} is stubbed.
 */
class CveAnalyzerTest {

    private final CveAnalyzer analyzer = new CveAnalyzer();

    private String fixture() throws IOException {
        try (var in = getClass().getResourceAsStream("/cve/ossindex-component-report.json")) {
            assertThat(in).isNotNull();
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

    @Test
    void toPurlBuildsMavenPackageUrl() {
        assertThat(CveAnalyzer.toPurl("org.slf4j", "slf4j-api", "2.0.9"))
                .isEqualTo("pkg:maven/org.slf4j/slf4j-api@2.0.9");
    }

    @Test
    void buildPurlsSortsDeduplicatesAndSkipsVersionless() {
        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("org.slf4j", "slf4j-api", "2.0.9"));
        artifacts.add(artifact("com.google.guava", "guava", "33.0.0-jre"));
        artifacts.add(artifact("com.google.guava", "guava", "33.0.0-jre")); // duplicate
        artifacts.add(artifact("org.example", "no-version", null));         // skipped

        List<String> purls = analyzer.buildPurls(artifacts);

        assertThat(purls).containsExactly(
                "pkg:maven/com.google.guava/guava@33.0.0-jre",
                "pkg:maven/org.slf4j/slf4j-api@2.0.9");
    }

    @Test
    void buildPurlsHandlesNullCollection() {
        assertThat(analyzer.buildPurls((java.util.Collection<Artifact>) null)).isEmpty();
    }

    @Test
    void parseReportExtractsEveryVulnerability() throws IOException {
        List<CveFinding> findings = analyzer.parseReport(fixture());

        assertThat(findings).hasSize(2);
        CveFinding critical = findings.get(0);
        assertThat(critical.getId()).isEqualTo("CVE-2021-44228");
        assertThat(critical.getCvssScore()).isEqualTo(10.0);
        assertThat(critical.getCoordinates()).contains("log4j-core");
        assertThat(critical.getReference()).contains("CVE-2021-44228");
        assertThat(critical.getTitle()).contains("Log4Shell");
    }

    @Test
    void parseReportHandlesEmptyAndNonArrayInput() throws IOException {
        assertThat(analyzer.parseReport(null)).isEmpty();
        assertThat(analyzer.parseReport("  ")).isEmpty();
        assertThat(analyzer.parseReport("{\"not\":\"an array\"}")).isEmpty();
        assertThat(analyzer.parseReport("[]")).isEmpty();
    }

    @Test
    void parseReportRejectsInvalidJson() {
        assertThatThrownBy(() -> analyzer.parseReport("{not json"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void filterByThresholdKeepsOnlyScoresAtOrAboveThreshold() throws IOException {
        List<CveFinding> findings = analyzer.parseReport(fixture());

        assertThat(analyzer.filterByThreshold(findings, 7.0))
                .extracting(CveFinding::getId)
                .containsExactly("CVE-2021-44228");
        assertThat(analyzer.filterByThreshold(findings, 5.0)).hasSize(2);
        assertThat(analyzer.filterByThreshold(findings, 10.1)).isEmpty();
    }

    @Test
    void analyzeUsesStubbedClientAndReturnsViolations() throws IOException {
        OssIndexClient stub = purls -> fixture();
        List<String> purls = List.of("pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1");

        List<CveFinding> violations = analyzer.analyze(stub, purls, 9.0);

        assertThat(violations).extracting(CveFinding::getId).containsExactly("CVE-2021-44228");
    }

    @Test
    void analyzeShortCircuitsWhenNoPurls() throws IOException {
        OssIndexClient failing = purls -> {
            throw new AssertionError("client must not be called for empty purls");
        };
        assertThat(analyzer.analyze(failing, List.of(), 1.0)).isEmpty();
    }

    @Test
    void findingMeetsThresholdBoundary() {
        CveFinding finding = new CveFinding("pkg:maven/g/a@1", "CVE-X", "t", 7.0, "ref");
        assertThat(finding.meetsThreshold(7.0)).isTrue();
        assertThat(finding.meetsThreshold(7.1)).isFalse();
        assertThat(finding.toString()).contains("CVE-X");
    }
}
