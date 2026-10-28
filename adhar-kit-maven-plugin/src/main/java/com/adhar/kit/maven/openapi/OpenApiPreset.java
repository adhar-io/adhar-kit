package com.adhar.kit.maven.openapi;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The code-generation presets the kit supports, each a fixed openapi-generator
 * configuration so every service generates the same shape of code from its spec.
 *
 * <p>Server presets ({@link #SPRING}, {@link #JAXRS}) generate interfaces the service
 * implements; with {@code skipDefaultInterface} an operation the controller forgets is
 * a compile error rather than a 501 at runtime. They also ship the spec inside the
 * artifact, because the service serves it at {@code /api/v<major>/openapi.yaml}.
 * {@link #SPRING_CLIENT} generates Spring HTTP interface clients for calling another
 * service (through its Dapr sidecar) and ships nothing: the spec belongs to the
 * service being called.</p>
 *
 * <p>Every preset hides the generation timestamp and generates no supporting files,
 * so the same spec always produces byte-identical sources.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public enum OpenApiPreset {

    /**
     * Spring MVC server interfaces for Spring Boot 4 (Jackson 3, Jakarta, JSpecify).
     * Bean Validation annotations are generated; no springdoc or swagger annotations,
     * because the shipped spec, not the code, is the documentation.
     */
    SPRING("spring", "spring", "spring-boot", true, options(
            "interfaceOnly", true,
            "skipDefaultInterface", true,
            "useTags", true,
            "useSpringBoot4", true,
            "useJackson3", true,
            "useJspecify", true,
            // jackson-databind-nullable is a Jackson 2 module; Boot 4 runs Jackson 3.
            "openApiNullable", false,
            "documentationProvider", "none",
            "annotationLibrary", "none",
            "useBeanValidation", true,
            // Paths in the spec are already absolute (/api/v<major>/...); a class-level
            // mapping would only add an ${openapi.*.base-path} placeholder.
            "requestMappingMode", "none",
            "hideGenerationTimestamp", true,
            "generateJsonIncludeAnnotations", false,
            "generateJsonSetterNullsAnnotations", false,
            "sourceFolder", "")),

    /**
     * JAX-RS ({@code jakarta.ws.rs}) server interfaces for Quarkus REST.
     */
    JAXRS("jaxrs", "jaxrs-spec", "quarkus", true, options(
            "interfaceOnly", true,
            "useJakartaEe", true,
            "useTags", true,
            "returnResponse", false,
            "useSwaggerAnnotations", false,
            "useMicroProfileOpenAPIAnnotations", false,
            "generatePom", false,
            "openApiNullable", false,
            "useBeanValidation", true,
            "hideGenerationTimestamp", true,
            "openApiSpecFileLocation", "",
            "sourceFolder", "")),

    /**
     * Spring HTTP interface ({@code @HttpExchange}) clients for calling another
     * service's API.
     */
    SPRING_CLIENT("spring-client", "spring", "spring-http-interface", false, options(
            "useTags", true,
            "useSpringBoot4", true,
            "useJackson3", true,
            "useJspecify", true,
            "openApiNullable", false,
            // The called service validates its own input.
            "useBeanValidation", false,
            "hideGenerationTimestamp", true,
            "generateJsonIncludeAnnotations", false,
            "generateJsonSetterNullsAnnotations", false,
            "sourceFolder", ""));

    private final String id;
    private final String generatorName;
    private final String library;
    private final boolean server;
    private final Map<String, Object> options;

    OpenApiPreset(String id, String generatorName, String library, boolean server, Map<String, Object> options) {
        this.id = id;
        this.generatorName = generatorName;
        this.library = library;
        this.server = server;
        this.options = options;
    }

    /**
     * Resolves a preset by its configuration id ({@code spring}, {@code jaxrs},
     * {@code spring-client}), ignoring case.
     *
     * @param id the preset id
     * @return the preset
     * @throws IllegalArgumentException when no preset has that id
     */
    public static OpenApiPreset of(String id) {
        String wanted = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (OpenApiPreset preset : values()) {
            if (preset.id.equals(wanted)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("Unknown OpenAPI preset '" + id + "'; use one of "
                + Arrays.stream(values()).map(OpenApiPreset::id).collect(Collectors.joining(", ")));
    }

    /** The id used in plugin configuration. */
    public String id() {
        return id;
    }

    /** The openapi-generator generator name. */
    public String generatorName() {
        return generatorName;
    }

    /** The openapi-generator library (sub-template). */
    public String library() {
        return library;
    }

    /**
     * Whether the preset generates the service's own API: such specs are linted and
     * shipped in the artifact, client specs are neither.
     */
    public boolean server() {
        return server;
    }

    /** The generator options this preset fixes, sorted by name. */
    public Map<String, Object> options() {
        return options;
    }

    private static Map<String, Object> options(Object... keyValues) {
        Map<String, Object> map = new TreeMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
