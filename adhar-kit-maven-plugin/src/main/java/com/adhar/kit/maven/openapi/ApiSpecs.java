package com.adhar.kit.maven.openapi;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds and reads the OpenAPI documents a service declares by convention: one
 * {@code api/v<major>/openapi.yaml} per major version at the repository root, so the
 * major in the directory, in every path ({@code /api/v<major>/...}) and in
 * {@code info.version} can be checked against each other.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class ApiSpecs {

    /** The file name of a spec inside its {@code v<major>} directory. */
    public static final String SPEC_FILE_NAME = "openapi.yaml";

    private static final Pattern MAJOR_DIRECTORY = Pattern.compile("v(0|[1-9]\\d*)");

    /**
     * Semantic Versioning 2.0.0, the expression semver.org publishes.
     */
    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
                    + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

    // JSON is valid YAML, so one reader serves both. A duplicated key (two entries for
    // the same path, say) is an error rather than a silent last-one-wins.
    private static final ObjectMapper READER = YAMLMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private ApiSpecs() {
    }

    /**
     * Lists every {@code v<major>/openapi.yaml} directly under {@code apiDirectory},
     * lowest major first. A missing directory yields an empty list.
     *
     * @param apiDirectory the {@code api} directory of the project
     * @return the discovered specs as (file, major) pairs
     * @throws IOException if the directory cannot be listed
     */
    public static List<Discovered> discover(Path apiDirectory) throws IOException {
        List<Discovered> found = new ArrayList<>();
        if (apiDirectory == null || !Files.isDirectory(apiDirectory)) {
            return found;
        }
        try (Stream<Path> children = Files.list(apiDirectory)) {
            for (Path child : children.toList()) {
                OptionalInt major = majorOfDirectoryName(child.getFileName().toString());
                Path spec = child.resolve(SPEC_FILE_NAME);
                if (major.isPresent() && Files.isRegularFile(spec)) {
                    found.add(new Discovered(spec, major.getAsInt()));
                }
            }
        }
        found.sort(Comparator.comparingInt(Discovered::major));
        return found;
    }

    /**
     * The major a spec file declares through its parent directory name ({@code v2} →
     * 2), if that directory follows the convention.
     *
     * @param specFile the spec file
     * @return the major, or empty when the parent directory is not {@code v<major>}
     */
    public static OptionalInt majorOfDirectory(Path specFile) {
        Path parent = specFile.toAbsolutePath().getParent();
        return parent == null ? OptionalInt.empty() : majorOfDirectoryName(parent.getFileName().toString());
    }

    private static OptionalInt majorOfDirectoryName(String name) {
        Matcher matcher = MAJOR_DIRECTORY.matcher(name);
        return matcher.matches() ? parseMajor(matcher.group(1)) : OptionalInt.empty();
    }

    /**
     * Reads a YAML or JSON OpenAPI document.
     *
     * @param file the document
     * @return its tree
     * @throws IOException if the file cannot be read or is not a YAML/JSON document
     */
    public static JsonNode read(Path file) throws IOException {
        JsonNode root = READER.readTree(file.toFile());
        if (root == null || !root.isObject()) {
            throw new IOException(file + " is not an OpenAPI document (expected a mapping at the top level)");
        }
        return root;
    }

    /**
     * Whether {@code version} is a Semantic Versioning 2.0.0 version.
     *
     * @param version the candidate
     * @return true for {@code 1.4.2}, {@code 2.0.0-rc.1}; false for {@code 1.0} or {@code v1.0.0}
     */
    public static boolean isSemanticVersion(String version) {
        return version != null && SEMVER.matcher(version).matches();
    }

    /**
     * The major of a semantic version ({@code 1.4.2} → 1).
     *
     * @param version a semantic version
     * @return the major, or empty when {@code version} is not semantic
     */
    public static OptionalInt majorOf(String version) {
        if (!isSemanticVersion(version)) {
            return OptionalInt.empty();
        }
        return parseMajor(version.substring(0, version.indexOf('.')));
    }

    private static OptionalInt parseMajor(String digits) {
        try {
            return OptionalInt.of(Integer.parseInt(digits));
        } catch (NumberFormatException tooLarge) {
            return OptionalInt.empty();
        }
    }

    /**
     * A spec found by convention.
     *
     * @param file  the {@code api/v<major>/openapi.yaml} file
     * @param major the major its directory names
     */
    public record Discovered(Path file, int major) {
    }
}
