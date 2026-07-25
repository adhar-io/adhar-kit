package com.adhar.kit.maven.validation;

import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Validates layered-architecture dependency rules for a source tree.
 *
 * <p>Enforces the standard {@code controller -> service -> repository} dependency
 * direction: a class in a lower layer (e.g. {@code repository}) must never depend
 * on a class in a higher layer (e.g. {@code service} or {@code controller}).</p>
 *
 * <p>Unlike {@link CodeStandardsValidator}'s naming/annotation checks, this
 * validator determines a class's layer from its actual {@code package}
 * declaration and inspects its {@code import} statements to discover cross-layer
 * type dependencies - it does not rely on substring-matching file paths, so it is
 * immune to both false positives (e.g. a package literally named
 * {@code com.example.servicecontroller}) and OS path-separator differences.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class ArchitectureRuleValidator {

    /**
     * The layers participating in the layering rule, ordered from lowest (data
     * access) to highest (presentation). A class may depend on a class in an
     * equal-or-lower-ranked layer, but never on a higher-ranked one.
     */
    public enum Layer {
        REPOSITORY(1),
        SERVICE(2),
        CONTROLLER(3);

        private final int rank;

        Layer(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;", Pattern.MULTILINE);

    private final File sourceDirectory;
    private final Log log;
    private final List<String> violations = new ArrayList<>();

    public ArchitectureRuleValidator(File sourceDirectory, Log log) {
        this.sourceDirectory = sourceDirectory;
        this.log = log;
    }

    /**
     * Scans all {@code .java} files under the source directory and records any
     * reverse-layer dependency violations.
     *
     * @return the number of violations found
     */
    public int validateLayering() throws IOException {
        Path srcPath = sourceDirectory.toPath();
        if (!Files.exists(srcPath)) {
            return 0;
        }

        try (Stream<Path> paths = Files.walk(srcPath)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            for (Path file : javaFiles) {
                analyzeFile(file);
            }
        }

        violations.forEach(log::warn);
        return violations.size();
    }

    /**
     * Returns an unmodifiable view of the violations found by the last call to
     * {@link #validateLayering()}.
     */
    public List<String> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    private void analyzeFile(Path file) throws IOException {
        String content = Files.readString(file);

        String packageName = extractPackageName(content);
        if (packageName == null) {
            return;
        }

        Layer ownLayer = layerOf(packageName);
        if (ownLayer == null) {
            return; // Neutral package (dto, model, config, ...): not subject to layering rules.
        }

        for (String imported : extractImports(content)) {
            String importedPackage = packageOf(imported);
            Layer importedLayer = layerOf(importedPackage);

            if (importedLayer == null || importedLayer == ownLayer) {
                continue;
            }

            if (ownLayer.rank() < importedLayer.rank()) {
                violations.add(String.format(
                        "Reverse dependency: %s (package '%s', layer %s) must not depend on '%s' (layer %s)",
                        file.getFileName(), packageName, ownLayer, imported, importedLayer));
            }
        }
    }

    /**
     * Determines the architectural layer for a package name by looking for a
     * dedicated {@code controller}/{@code service}/{@code repository} segment
     * among its dot-separated components (so {@code com.example.service.impl}
     * is still recognized as the SERVICE layer).
     */
    static Layer layerOf(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        for (String segment : packageName.split("\\.")) {
            switch (segment) {
                case "controller":
                    return Layer.CONTROLLER;
                case "service":
                    return Layer.SERVICE;
                case "repository":
                    return Layer.REPOSITORY;
                default:
                    // keep scanning remaining segments
            }
        }
        return null;
    }

    private static String extractPackageName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<String> extractImports(String content) {
        List<String> imports = new ArrayList<>();
        Matcher matcher = IMPORT_PATTERN.matcher(content);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    /**
     * Derives the package of an imported type/reference, e.g.
     * {@code com.example.service.UserService} -&gt; {@code com.example.service},
     * {@code com.example.service.*} -&gt; {@code com.example.service}.
     */
    private static String packageOf(String importedType) {
        // A wildcard import ("com.example.service.*") already names the package
        // itself once the ".*" suffix is removed - unlike a normal import
        // ("com.example.service.UserService"), it must NOT have its last segment
        // stripped again (that would incorrectly yield "com.example").
        if (importedType.endsWith(".*")) {
            return importedType.substring(0, importedType.length() - 2);
        }
        int lastDot = importedType.lastIndexOf('.');
        return lastDot >= 0 ? importedType.substring(0, lastDot) : importedType;
    }
}
