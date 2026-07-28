package com.adhar.kit.maven.bom;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares a project's <em>resolved</em> dependency versions against the
 * versions managed by the Adhar Kit BOM (surfaced through the project's
 * effective {@code <dependencyManagement>}) and reports any mismatches.
 *
 * <p>A mismatch means a dependency was resolved to a version that differs from
 * the one the BOM pins for the same {@code groupId:artifactId} - usually the
 * result of a local version override or a transitive dependency winning over
 * the managed version. Only artifacts that the BOM actually manages are
 * considered; unmanaged artifacts are ignored here.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class BomAlignmentAnalyzer {

    private final MavenProject project;
    private final Log log;

    private final List<String> misalignments = new ArrayList<>();

    public BomAlignmentAnalyzer(MavenProject project, Log log) {
        this.project = project;
        this.log = log;
    }

    /**
     * Managed versions keyed by {@code groupId:artifactId}, taken from the
     * project's effective {@code <dependencyManagement>} (which is where the
     * imported BOM's pins land).
     */
    public Map<String, String> managedVersions() {
        Map<String, String> managed = new LinkedHashMap<>();
        DependencyManagement dependencyManagement = project.getDependencyManagement();
        if (dependencyManagement != null && dependencyManagement.getDependencies() != null) {
            for (Dependency dependency : dependencyManagement.getDependencies()) {
                if (dependency.getVersion() != null) {
                    managed.put(gaKey(dependency.getGroupId(), dependency.getArtifactId()), dependency.getVersion());
                }
            }
        }
        return managed;
    }

    /**
     * Resolved versions keyed by {@code groupId:artifactId}, taken from the
     * project's resolved artifact set.
     */
    public Map<String, String> resolvedVersions() {
        Map<String, String> resolved = new LinkedHashMap<>();
        Set<Artifact> artifacts = project.getArtifacts();
        if (artifacts != null) {
            for (Artifact artifact : artifacts) {
                if (artifact != null && artifact.getVersion() != null) {
                    resolved.put(gaKey(artifact.getGroupId(), artifact.getArtifactId()), artifact.getVersion());
                }
            }
        }
        return resolved;
    }

    /**
     * Finds every dependency whose resolved version diverges from the version
     * the BOM manages for it.
     *
     * @return the number of misalignments found
     */
    public int findMisalignments() {
        return compare(managedVersions(), resolvedVersions());
    }

    /**
     * Pure comparison of managed versus resolved versions - the underlying
     * logic, exposed for direct unit testing.
     *
     * @param managed  managed versions keyed by {@code groupId:artifactId}
     * @param resolved resolved versions keyed by {@code groupId:artifactId}
     * @return the number of misalignments recorded
     */
    public int compare(Map<String, String> managed, Map<String, String> resolved) {
        for (Map.Entry<String, String> entry : resolved.entrySet()) {
            String ga = entry.getKey();
            String resolvedVersion = entry.getValue();
            String managedVersion = managed.get(ga);
            if (managedVersion != null && !managedVersion.equals(resolvedVersion)) {
                String message = "BOM misalignment for '" + ga + "': resolved version '"
                        + resolvedVersion + "' differs from BOM-managed version '" + managedVersion + "'";
                misalignments.add(message);
                log.warn(message);
            }
        }
        return misalignments.size();
    }

    public List<String> getMisalignments() {
        return Collections.unmodifiableList(misalignments);
    }

    /**
     * Writes a plain-text BOM alignment report.
     */
    public void generateReport(File reportFile) throws IOException {
        File parent = reportFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write("================================================================================\n");
            writer.write("  Adhar Kit BOM Alignment Report\n");
            writer.write("  Project: " + project.getName() + "\n");
            writer.write("  Version: " + project.getVersion() + "\n");
            writer.write("================================================================================\n\n");
            writer.write("BOM MISALIGNMENTS (" + misalignments.size() + "):\n");
            writer.write("--------------------------------------------------------------------------------\n");
            for (String misalignment : misalignments) {
                writer.write("  - " + misalignment + "\n");
            }
            writer.write("\n");
            writer.write("================================================================================\n");
            writer.write("  Total Misalignments: " + misalignments.size() + "\n");
            writer.write("================================================================================\n");
        }
    }

    private static String gaKey(String groupId, String artifactId) {
        return groupId + ":" + artifactId;
    }
}
