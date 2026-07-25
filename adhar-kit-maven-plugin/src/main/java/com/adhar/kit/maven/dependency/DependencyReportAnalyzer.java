package com.adhar.kit.maven.dependency;

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

/**
 * Offline dependency hygiene analyzer.
 *
 * <p>Reads only the already-built Maven project model ({@link
 * MavenProject#getDependencies()} / {@link MavenProject#getDependencyManagement()})
 * - it never resolves artifacts, contacts a repository, or downloads a CVE/OWASP
 * database. It flags three classes of issues:</p>
 * <ul>
 *   <li><b>Duplicate dependencies</b> - the same {@code groupId:artifactId}
 *   declared more than once in {@code <dependencies>}.</li>
 *   <li><b>Version convergence conflicts</b> - a dependency declares an explicit
 *   version that differs from the version already managed for it in {@code
 *   <dependencyManagement>} (including versions imported transitively via a BOM),
 *   which risks divergent versions across a multi-module reactor.</li>
 *   <li><b>Dependencies not managed by a BOM</b> - a dependency declares an
 *   explicit, hard-coded version with no corresponding entry in {@code
 *   <dependencyManagement>} at all, meaning its version is not centrally
 *   controlled.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class DependencyReportAnalyzer {

    private final MavenProject project;
    private final Log log;

    private final List<String> duplicates = new ArrayList<>();
    private final List<String> convergenceConflicts = new ArrayList<>();
    private final List<String> unmanagedDependencies = new ArrayList<>();

    public DependencyReportAnalyzer(MavenProject project, Log log) {
        this.project = project;
        this.log = log;
    }

    /**
     * Finds dependencies whose {@code groupId:artifactId} appears more than once
     * in the project's direct {@code <dependencies>} list.
     *
     * @return the number of distinct artifacts found duplicated
     */
    public int findDuplicateDependencies() {
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        for (Dependency dependency : safeDependencies()) {
            occurrences.merge(gaKey(dependency), 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : occurrences.entrySet()) {
            if (entry.getValue() > 1) {
                String message = "Duplicate dependency '" + entry.getKey() + "' declared "
                        + entry.getValue() + " times";
                duplicates.add(message);
                log.warn(message);
            }
        }
        return duplicates.size();
    }

    /**
     * Finds dependencies that declare an explicit version conflicting with the
     * version already managed for the same {@code groupId:artifactId} in
     * {@code <dependencyManagement>} (e.g. imported from the Adhar Kit BOM).
     *
     * @return the number of convergence conflicts found
     */
    public int findVersionConvergenceConflicts() {
        Map<String, String> managedVersions = managedVersionsByGa();

        for (Dependency dependency : uniqueDependenciesByGa()) {
            String declaredVersion = dependency.getVersion();
            if (declaredVersion == null) {
                continue;
            }
            String managedVersion = managedVersions.get(gaKey(dependency));
            if (managedVersion != null && !managedVersion.equals(declaredVersion)) {
                String message = "Version convergence conflict for '" + gaKey(dependency)
                        + "': declared version '" + declaredVersion
                        + "' differs from managed version '" + managedVersion + "'";
                convergenceConflicts.add(message);
                log.warn(message);
            }
        }
        return convergenceConflicts.size();
    }

    /**
     * Finds dependencies that hard-code an explicit version with no
     * corresponding entry in {@code <dependencyManagement>}, meaning the version
     * is not managed by any BOM or parent POM.
     *
     * @return the number of unmanaged dependencies found
     */
    public int findUnmanagedDependencies() {
        Map<String, String> managedVersions = managedVersionsByGa();

        for (Dependency dependency : uniqueDependenciesByGa()) {
            String declaredVersion = dependency.getVersion();
            if (declaredVersion == null) {
                continue; // Relies entirely on dependencyManagement - the desired state.
            }
            if (!managedVersions.containsKey(gaKey(dependency))) {
                String message = "Dependency '" + gaKey(dependency)
                        + "' is not managed by any BOM/dependencyManagement (hard-coded version '"
                        + declaredVersion + "')";
                unmanagedDependencies.add(message);
                log.warn(message);
            }
        }
        return unmanagedDependencies.size();
    }

    public List<String> getDuplicates() {
        return Collections.unmodifiableList(duplicates);
    }

    public List<String> getConvergenceConflicts() {
        return Collections.unmodifiableList(convergenceConflicts);
    }

    public List<String> getUnmanagedDependencies() {
        return Collections.unmodifiableList(unmanagedDependencies);
    }

    /**
     * Writes a plain-text hygiene report summarizing all findings.
     */
    public void generateReport(File reportFile) throws IOException {
        File parent = reportFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write("================================================================================\n");
            writer.write("  Adhar Kit Dependency Hygiene Report\n");
            writer.write("  Project: " + project.getName() + "\n");
            writer.write("  Version: " + project.getVersion() + "\n");
            writer.write("================================================================================\n\n");

            writeSection(writer, "DUPLICATE DEPENDENCIES", duplicates);
            writeSection(writer, "VERSION CONVERGENCE CONFLICTS", convergenceConflicts);
            writeSection(writer, "DEPENDENCIES NOT MANAGED BY A BOM", unmanagedDependencies);

            int total = duplicates.size() + convergenceConflicts.size() + unmanagedDependencies.size();
            writer.write("================================================================================\n");
            writer.write("  Total Issues: " + total + "\n");
            writer.write("================================================================================\n");
        }
    }

    private void writeSection(FileWriter writer, String title, List<String> items) throws IOException {
        writer.write(title + " (" + items.size() + "):\n");
        writer.write("--------------------------------------------------------------------------------\n");
        for (String item : items) {
            writer.write("  - " + item + "\n");
        }
        writer.write("\n");
    }

    private Map<String, String> managedVersionsByGa() {
        Map<String, String> managed = new LinkedHashMap<>();
        DependencyManagement dependencyManagement = project.getDependencyManagement();
        if (dependencyManagement != null && dependencyManagement.getDependencies() != null) {
            for (Dependency dependency : dependencyManagement.getDependencies()) {
                if (dependency.getVersion() != null) {
                    managed.put(gaKey(dependency), dependency.getVersion());
                }
            }
        }
        return managed;
    }

    /**
     * Returns the project's direct dependencies deduplicated by {@code
     * groupId:artifactId}, keeping the first declaration - used so that version
     * convergence/BOM-management checks don't emit repeated findings for a
     * dependency that is already reported once by {@link
     * #findDuplicateDependencies()}.
     */
    private List<Dependency> uniqueDependenciesByGa() {
        Map<String, Dependency> unique = new LinkedHashMap<>();
        for (Dependency dependency : safeDependencies()) {
            unique.putIfAbsent(gaKey(dependency), dependency);
        }
        return new ArrayList<>(unique.values());
    }

    private List<Dependency> safeDependencies() {
        List<Dependency> dependencies = project.getDependencies();
        return dependencies != null ? dependencies : Collections.emptyList();
    }

    private static String gaKey(Dependency dependency) {
        return dependency.getGroupId() + ":" + dependency.getArtifactId();
    }
}
