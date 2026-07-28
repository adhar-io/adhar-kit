package com.adhar.kit.maven.cve;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Core, network-free logic for the CVE check goal: turning resolved Maven
 * coordinates into {@code pkg:maven} package URLs (purls), parsing a Sonatype
 * OSS Index component-report JSON payload into {@link CveFinding}s, and applying
 * a CVSS severity threshold.
 *
 * <p>The actual HTTP call is delegated to an {@link OssIndexClient} so this
 * class (and its callers) can be unit-tested with a stub client or a captured
 * JSON fixture.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class CveAnalyzer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Builds a {@code pkg:maven} package URL for a single Maven coordinate.
     *
     * @param groupId    the group id
     * @param artifactId the artifact id
     * @param version    the resolved version
     * @return a purl of the form {@code pkg:maven/groupId/artifactId@version}
     */
    public static String toPurl(String groupId, String artifactId, String version) {
        return "pkg:maven/" + groupId + "/" + artifactId + "@" + version;
    }

    /**
     * Builds the de-duplicated, sorted set of purls for every resolved artifact
     * of the given project. Artifacts without a version are skipped.
     *
     * @param project the Maven project whose resolved artifacts to describe
     * @return the sorted list of {@code pkg:maven} purls
     */
    public List<String> buildPurls(MavenProject project) {
        Set<Artifact> artifacts = project.getArtifacts();
        return buildPurls(artifacts);
    }

    /**
     * Builds the de-duplicated, sorted set of purls for the given artifacts.
     *
     * @param artifacts the resolved artifacts to describe
     * @return the sorted list of {@code pkg:maven} purls
     */
    public List<String> buildPurls(Collection<Artifact> artifacts) {
        Set<String> purls = new TreeSet<>();
        if (artifacts != null) {
            for (Artifact artifact : artifacts) {
                if (artifact == null || artifact.getVersion() == null) {
                    continue;
                }
                purls.add(toPurl(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
            }
        }
        return new ArrayList<>(purls);
    }

    /**
     * Parses an OSS Index component-report JSON payload into a flat list of
     * findings. Components without vulnerabilities contribute nothing.
     *
     * @param json the raw JSON array returned by OSS Index
     * @return every vulnerability across every component in the payload
     * @throws IOException if the payload is not valid JSON
     */
    public List<CveFinding> parseReport(String json) throws IOException {
        List<CveFinding> findings = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return findings;
        }
        JsonNode root = objectMapper.readTree(json);
        if (!root.isArray()) {
            return findings;
        }
        for (JsonNode component : root) {
            String coordinates = component.path("coordinates").asText("");
            JsonNode vulnerabilities = component.path("vulnerabilities");
            if (!vulnerabilities.isArray()) {
                continue;
            }
            for (JsonNode vulnerability : vulnerabilities) {
                String id = firstNonBlank(
                        vulnerability.path("cve").asText(""),
                        vulnerability.path("id").asText(""));
                String title = vulnerability.path("title").asText("");
                double cvssScore = vulnerability.path("cvssScore").asDouble(0.0);
                String reference = vulnerability.path("reference").asText("");
                findings.add(new CveFinding(coordinates, id, title, cvssScore, reference));
            }
        }
        return findings;
    }

    /**
     * Filters findings down to those meeting or exceeding the CVSS threshold.
     *
     * @param findings  all parsed findings
     * @param threshold the minimum CVSS score considered a violation
     * @return the subset of findings that violate the threshold
     */
    public List<CveFinding> filterByThreshold(List<CveFinding> findings, double threshold) {
        List<CveFinding> violations = new ArrayList<>();
        for (CveFinding finding : findings) {
            if (finding.meetsThreshold(threshold)) {
                violations.add(finding);
            }
        }
        return violations;
    }

    /**
     * Convenience end-to-end analysis: fetch the report via the client, parse
     * it, and return the findings that violate the threshold.
     *
     * @param client    the (possibly stubbed) OSS Index client
     * @param purls     the coordinates to query
     * @param threshold the minimum CVSS score considered a violation
     * @return the violating findings
     * @throws IOException if the client fails or the response is unparseable
     */
    public List<CveFinding> analyze(OssIndexClient client, List<String> purls, double threshold)
            throws IOException {
        if (purls.isEmpty()) {
            return new ArrayList<>();
        }
        String json = client.componentReport(purls);
        List<CveFinding> findings = parseReport(json);
        return filterByThreshold(findings, threshold);
    }

    private static String firstNonBlank(String first, String second) {
        return (first != null && !first.isBlank()) ? first : second;
    }
}
