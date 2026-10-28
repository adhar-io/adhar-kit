package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.openapi.ApiSpec;
import com.adhar.kit.maven.openapi.OpenApiCodeGenerator;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.openapitools.codegen.SpecValidationException;
import org.openapitools.codegen.utils.ImplementationVersion;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Generates Java sources from the service's OpenAPI specs - spec first: the spec is
 * written, reviewed and versioned, and the code that serves (or calls) it is derived.
 *
 * <p>Each {@code api/v<major>/openapi.yaml} generates into
 * {@code <basePackage>.api.v<major>} with the {@code spring} preset by default
 * (interfaces the controllers implement, plus models in {@code .model}). The output
 * directory becomes a compile source root, and the service's own specs are copied
 * into the artifact as {@code api/v<major>/openapi.yaml}, where adhar-kit-commons
 * serves them at {@code GET /api/v<major>/openapi.yaml}.</p>
 *
 * <p>Generation is deterministic and skipped when nothing it depends on changed (the
 * spec files, presets, packages, options and the generator version). Without a spec
 * the goal does nothing, so a parent can bind it for every service.</p>
 *
 * <p><b>Usage</b> (adhar-kit-parent binds this goal to {@code generate-sources}; a
 * service only declares the plugin):</p>
 * <pre>{@code
 * <plugin>
 *   <groupId>com.adhar.kit</groupId>
 *   <artifactId>adhar-kit-maven-plugin</artifactId>
 *   <configuration>
 *     <basePackage>com.example.orders</basePackage>
 *   </configuration>
 * </plugin>
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Mojo(name = "openapi", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class OpenApiMojo extends AbstractOpenApiMojo {

    /**
     * Source root the sources are generated into.
     */
    @Parameter(property = "adhar.openapi.outputDirectory",
            defaultValue = "${project.build.directory}/generated-sources/openapi")
    private File outputDirectory;

    /**
     * Resource root the service's own specs are copied into (as
     * {@code api/v<major>/openapi.yaml}) so they ship in the artifact.
     */
    @Parameter(property = "adhar.openapi.resourcesDirectory",
            defaultValue = "${project.build.directory}/generated-resources/openapi")
    private File resourcesDirectory;

    /**
     * Whether the service's own specs are shipped in the artifact.
     */
    @Parameter(property = "adhar.openapi.shipSpecs", defaultValue = "true")
    private boolean shipSpecs;

    /**
     * Generator options that override the preset's, for the rare case a preset does not
     * fit, e.g. {@code <useOptional>true</useOptional>}.
     */
    @Parameter
    private Map<String, String> additionalProperties;

    /**
     * Where the fingerprint of the last generation is kept.
     */
    @Parameter(defaultValue = "${project.build.directory}/adhar-openapi", readonly = true)
    private File stateDirectory;

    private final OpenApiCodeGenerator generator = new OpenApiCodeGenerator();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("OpenAPI generation skipped (adhar.openapi.skip)");
            return;
        }
        List<ApiSpec> resolved = resolveSpecs();
        if (resolved.isEmpty()) {
            getLog().info("No OpenAPI spec at " + relative(apiDirectory.toPath()) + "/v<major>/openapi.yaml"
                    + " and none configured; nothing to generate");
            return;
        }

        generateSources(resolved);
        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());

        if (shipSpecs) {
            ship(resolved);
        }
    }

    private void generateSources(List<ApiSpec> resolved) throws MojoExecutionException, MojoFailureException {
        String fingerprint = fingerprint(resolved);
        Path stamp = stateDirectory.toPath().resolve("inputs.sha256");
        try {
            if (Files.isDirectory(outputDirectory.toPath()) && Files.isRegularFile(stamp)
                    && fingerprint.equals(Files.readString(stamp, StandardCharsets.UTF_8).trim())) {
                getLog().info("Generated OpenAPI sources are up to date");
                return;
            }
            // Regenerating into a clean directory is what makes the output a function of
            // the spec alone: an operation removed from the spec must not survive as a
            // stale interface method.
            deleteRecursively(outputDirectory.toPath());
            Files.createDirectories(outputDirectory.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot prepare " + outputDirectory + ": " + e.getMessage(), e);
        }

        for (ApiSpec spec : resolved) {
            getLog().info("Generating " + spec.preset().id() + " sources for /api/v" + spec.major()
                    + " from " + relative(spec.file()) + " into " + spec.packageName());
            try {
                generator.generate(spec, outputDirectory, additionalProperties);
            } catch (SpecValidationException e) {
                e.getErrors().forEach(error -> getLog().error(relative(spec.file()) + ": " + error));
                throw new MojoFailureException(relative(spec.file()) + " is not a valid OpenAPI document: "
                        + String.join("; ", e.getErrors()), e);
            } catch (RuntimeException e) {
                throw new MojoExecutionException("Generating sources from " + relative(spec.file())
                        + " failed: " + e.getMessage(), e);
            }
        }

        try {
            Files.createDirectories(stamp.getParent());
            Files.writeString(stamp, fingerprint, StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLog().warn("Cannot record the OpenAPI generation fingerprint in " + stamp
                    + "; the next build regenerates: " + e.getMessage());
        }
    }

    private void ship(List<ApiSpec> resolved) throws MojoExecutionException {
        Path root = resourcesDirectory.toPath();
        boolean shipped = false;
        try {
            // A major removed from the repository must not linger in the artifact.
            deleteRecursively(root.resolve("api"));
            for (ApiSpec spec : resolved) {
                if (spec.shipped()) {
                    Path target = root.resolve(spec.classpathLocation());
                    Files.createDirectories(target.getParent());
                    Files.copy(spec.file(), target, StandardCopyOption.REPLACE_EXISTING);
                    shipped = true;
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot copy the OpenAPI specs into " + root + ": " + e.getMessage(), e);
        }
        if (shipped && project.getResources().stream()
                .noneMatch(resource -> root.toString().equals(resource.getDirectory()))) {
            Resource resource = new Resource();
            resource.setDirectory(root.toString());
            project.addResource(resource);
        }
    }

    /**
     * Everything the generated sources depend on: generator version, preset options,
     * overrides, packages and the spec bytes.
     */
    private String fingerprint(List<ApiSpec> resolved) throws MojoExecutionException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "generator=" + ImplementationVersion.read());
            update(digest, "overrides=" + new TreeMap<>(additionalProperties == null ? Map.of() : additionalProperties));
            for (ApiSpec spec : resolved.stream().sorted(Comparator.comparing(ApiSpec::packageName)).toList()) {
                update(digest, "spec=" + spec.packageName() + "|" + spec.major() + "|" + spec.preset().id()
                        + "|" + spec.preset().options());
                digest.update(Files.readAllBytes(spec.file()));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read OpenAPI spec: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        }
    }
}
