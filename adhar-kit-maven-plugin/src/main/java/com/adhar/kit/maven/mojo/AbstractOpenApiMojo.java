package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.openapi.ApiSpec;
import com.adhar.kit.maven.openapi.ApiSpecs;
import com.adhar.kit.maven.openapi.OpenApiPreset;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import javax.lang.model.SourceVersion;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * What the OpenAPI goals share: where specs are found, which preset generates them and
 * which package they land in.
 *
 * <p>By convention a service keeps one spec per major version at
 * {@code api/v<major>/openapi.yaml}; {@link #resolveSpecs()} finds those and adds the
 * explicitly configured {@link OpenApiSpecConfig specs}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public abstract class AbstractOpenApiMojo extends AbstractMojo {

    private static final Pattern NOT_IDENTIFIER_PART = Pattern.compile("[^A-Za-z0-9_]");

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * Directory holding one {@code v<major>/openapi.yaml} per major version of the
     * service's API.
     */
    @Parameter(property = "adhar.openapi.apiDirectory", defaultValue = "${project.basedir}/api")
    protected File apiDirectory;

    /**
     * Whether specs are found by convention under {@link #apiDirectory}.
     */
    @Parameter(property = "adhar.openapi.discover", defaultValue = "true")
    protected boolean discover;

    /**
     * Preset for the specs found by convention, and for configured specs that name none:
     * {@code spring}, {@code jaxrs} (Quarkus) or {@code spring-client}.
     */
    @Parameter(property = "adhar.openapi.preset", defaultValue = "spring")
    protected String preset;

    /**
     * Package prefix: the spec of major {@code N} generates into
     * {@code <basePackage>.api.vN} (models in {@code <basePackage>.api.vN.model}).
     */
    @Parameter(property = "adhar.openapi.basePackage", defaultValue = "${project.groupId}")
    protected String basePackage;

    /**
     * Further specs, e.g. other services' APIs to generate {@code spring-client}s for.
     */
    @Parameter
    protected List<OpenApiSpecConfig> specs;

    /**
     * Skips every OpenAPI goal.
     */
    @Parameter(property = "adhar.openapi.skip", defaultValue = "false")
    protected boolean skip;

    /**
     * The specs to work on: those found by convention, then the configured ones (a
     * configured spec replaces a discovered one for the same file).
     *
     * @return the resolved specs, never null
     * @throws MojoExecutionException when the configuration is inconsistent
     */
    protected List<ApiSpec> resolveSpecs() throws MojoExecutionException {
        OpenApiPreset defaultPreset = preset(preset, "adhar.openapi.preset");
        Map<Path, ApiSpec> byFile = new LinkedHashMap<>();

        if (discover && apiDirectory != null) {
            try {
                for (ApiSpecs.Discovered found : ApiSpecs.discover(apiDirectory.toPath())) {
                    byFile.put(found.file().toAbsolutePath().normalize(), new ApiSpec(found.file(), found.major(),
                            defaultPreset, defaultPackage(defaultPreset, found.major())));
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Cannot list " + apiDirectory + ": " + e.getMessage(), e);
            }
        }

        if (specs != null) {
            for (OpenApiSpecConfig config : specs) {
                ApiSpec spec = resolve(config, defaultPreset);
                byFile.put(spec.file().toAbsolutePath().normalize(), spec);
            }
        }

        List<ApiSpec> resolved = new ArrayList<>(byFile.values());
        checkNoCollisions(resolved);
        return resolved;
    }

    private ApiSpec resolve(OpenApiSpecConfig config, OpenApiPreset defaultPreset) throws MojoExecutionException {
        File file = config.getFile();
        if (file == null) {
            throw new MojoExecutionException("Every configured <spec> needs a <file>");
        }
        if (!file.isFile()) {
            throw new MojoExecutionException("OpenAPI spec " + file + " does not exist");
        }
        OpenApiPreset specPreset = config.getPreset() == null
                ? defaultPreset : preset(config.getPreset(), "<spec><preset>");
        int major = config.getMajor() != null ? config.getMajor() : majorOf(file.toPath());
        String packageName = config.getPackageName() != null
                ? config.getPackageName().trim() : defaultPackage(specPreset, major);
        if (!SourceVersion.isName(packageName)) {
            throw new MojoExecutionException("<packageName>" + packageName + "</packageName> of " + file
                    + " is not a Java package name");
        }
        return new ApiSpec(file.toPath(), major, specPreset, packageName);
    }

    private int majorOf(Path file) throws MojoExecutionException {
        OptionalInt fromDirectory = ApiSpecs.majorOfDirectory(file);
        if (fromDirectory.isPresent()) {
            return fromDirectory.getAsInt();
        }
        try {
            String version = ApiSpecs.read(file).path("info").path("version").asText(null);
            OptionalInt fromVersion = ApiSpecs.majorOf(version);
            if (fromVersion.isPresent()) {
                return fromVersion.getAsInt();
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read OpenAPI spec " + file + ": " + e.getMessage(), e);
        }
        throw new MojoExecutionException("Cannot tell the major version of " + file
                + ": it is not in a v<major> directory and its info.version is not a semantic version;"
                + " set <major> on the <spec>");
    }

    private void checkNoCollisions(List<ApiSpec> resolved) throws MojoExecutionException {
        Map<String, ApiSpec> byPackage = new HashMap<>();
        Map<Integer, ApiSpec> servedMajors = new HashMap<>();
        for (ApiSpec spec : resolved) {
            ApiSpec samePackage = byPackage.putIfAbsent(spec.packageName(), spec);
            if (samePackage != null) {
                throw new MojoExecutionException(spec.file() + " and " + samePackage.file()
                        + " both generate into package " + spec.packageName() + "; give one of them a <packageName>");
            }
            if (spec.shipped()) {
                ApiSpec sameMajor = servedMajors.putIfAbsent(spec.major(), spec);
                if (sameMajor != null) {
                    throw new MojoExecutionException(spec.file() + " and " + sameMajor.file()
                            + " both describe this service's /api/v" + spec.major());
                }
            }
        }
    }

    private String defaultPackage(OpenApiPreset specPreset, int major) {
        String base = javaPackage(basePackage);
        return base + (specPreset.server() ? ".api.v" : ".client.v") + major;
    }

    private static OpenApiPreset preset(String id, String where) throws MojoExecutionException {
        try {
            return OpenApiPreset.of(id);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException(where + ": " + e.getMessage(), e);
        }
    }

    /**
     * Turns a groupId into a package name: {@code io.adhar.my-service} becomes
     * {@code io.adhar.my_service}, and a segment that is a keyword or starts with a
     * digit gets an underscore.
     */
    static String javaPackage(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "api";
        }
        List<String> segments = new ArrayList<>();
        for (String segment : candidate.trim().split("\\.")) {
            if (segment.isEmpty()) {
                continue;
            }
            String identifier = NOT_IDENTIFIER_PART.matcher(segment).replaceAll("_");
            if (Character.isDigit(identifier.charAt(0)) || SourceVersion.isKeyword(identifier)) {
                identifier = "_" + identifier;
            }
            segments.add(identifier);
        }
        return segments.isEmpty() ? "api" : String.join(".", segments);
    }

    /**
     * The path of {@code file} relative to the project, for log lines.
     */
    protected String relative(Path file) {
        if (project != null && project.getBasedir() != null) {
            Path base = project.getBasedir().toPath().toAbsolutePath().normalize();
            Path absolute = file.toAbsolutePath().normalize();
            if (absolute.startsWith(base)) {
                return base.relativize(absolute).toString();
            }
        }
        return file.toString();
    }
}
