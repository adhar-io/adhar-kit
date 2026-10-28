package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.InProcessCompiler;
import com.adhar.kit.maven.TestSupport;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Generated;
import jakarta.validation.Valid;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OpenApiMojo}: what each preset generates, that it compiles, and how
 * the goal manages its output.
 */
class OpenApiMojoTest {

    private static final Path PETS = Path.of("src/test/resources/openapi/pets/v1/openapi.yaml");

    /** The APIs generated server and client code is compiled against in a service. */
    private static final Class<?>[] SPRING_CLASSPATH = {
            RequestMapping.class, Validated.class, MultiValueMap.class, Valid.class,
            Generated.class, JsonProperty.class, Nullable.class};

    @Test
    void springPresetGeneratesInterfacesAndModelsThatCompile(@TempDir Path base) throws Exception {
        copyPets(base, "api/v1/openapi.yaml");
        OpenApiMojo mojo = newMojo(base);

        mojo.execute();

        Path out = base.resolve("target/generated-sources/openapi");
        Path api = out.resolve("com/example/pets/api/v1");
        assertThat(api.resolve("PetsApi.java")).exists();
        assertThat(api.resolve("OwnersApi.java")).exists();
        assertThat(api.resolve("model")).isDirectoryContaining("glob:**/Pet.java")
                .isDirectoryContaining("glob:**/NewPet.java")
                .isDirectoryContaining("glob:**/Owner.java")
                .isDirectoryContaining("glob:**/Problem.java");
        // Only what the build compiles: no ApiUtil, pom, README or generator metadata.
        assertThat(relativeFiles(out)).allMatch(path -> path.endsWith(".java"))
                .noneMatch(path -> path.contains("ApiUtil"));

        String petsApi = Files.readString(api.resolve("PetsApi.java"));
        assertThat(petsApi)
                .contains("public interface PetsApi")
                .contains("\"/api/v1/pets/{petId}\"")
                .contains("ResponseEntity<Pet> getPet(")
                .contains("@Validated")
                .contains("@Max(value = 100)")
                .doesNotContain("base-path")      // paths are absolute; no class-level mapping
                .doesNotContain("default ResponseEntity") // a missing operation is a compile error
                .doesNotContain("io.swagger")
                .doesNotContain("org.springframework.lang.Nullable")
                .doesNotContain("date =");        // no timestamp: same spec, same bytes

        InProcessCompiler.Result compiled = InProcessCompiler.compile(out, base.resolve("target/classes"),
                SPRING_CLASSPATH);
        assertThat(compiled.diagnostics()).isEmpty();
        assertThat(compiled.compiled()).isTrue();
        assertThat(compiled.sourceCount()).isEqualTo(6);

        MavenProject project = (MavenProject) field(mojo, "project");
        assertThat(project.getCompileSourceRoots()).contains(out.toString());
    }

    @Test
    void theServicesOwnSpecShipsInTheArtifact(@TempDir Path base) throws Exception {
        copyPets(base, "api/v1/openapi.yaml");
        OpenApiMojo mojo = newMojo(base);

        mojo.execute();
        mojo.execute();

        Path resources = base.resolve("target/generated-resources/openapi");
        assertThat(resources.resolve("api/v1/openapi.yaml")).hasSameBinaryContentAs(PETS);
        MavenProject project = (MavenProject) field(mojo, "project");
        assertThat(project.getResources()).singleElement()
                .satisfies(resource -> assertThat(resource.getDirectory()).isEqualTo(resources.toString()));

        // A major deleted from the repository disappears from the artifact too.
        Files.delete(base.resolve("api/v1/openapi.yaml"));
        copyPets(base, "api/v2/openapi.yaml", "1.2.0", "2.0.0", "/api/v1/", "/api/v2/");
        mojo.execute();
        assertThat(resources.resolve("api/v1")).doesNotExist();
        assertThat(resources.resolve("api/v2/openapi.yaml")).exists();
    }

    @Test
    void generationIsDeterministicAndSkippedWhenNothingChanged(@TempDir Path base) throws Exception {
        copyPets(base, "api/v1/openapi.yaml");
        OpenApiMojo mojo = newMojo(base);
        Path out = base.resolve("target/generated-sources/openapi");

        mojo.execute();
        Map<String, String> first = contents(out);
        Path petsApi = out.resolve("com/example/pets/api/v1/PetsApi.java");
        Files.setLastModifiedTime(petsApi, FileTime.fromMillis(0));

        mojo.execute();
        assertThat(Files.getLastModifiedTime(petsApi).toMillis()).as("untouched when up to date").isZero();

        Files.delete(base.resolve("target/adhar-openapi/inputs.sha256"));
        mojo.execute();
        assertThat(contents(out)).isEqualTo(first);
        assertThat(Files.getLastModifiedTime(petsApi).toMillis()).isNotZero();
    }

    @Test
    void anOperationRemovedFromTheSpecDoesNotSurviveInTheSources(@TempDir Path base) throws Exception {
        Path spec = copyPets(base, "api/v1/openapi.yaml");
        OpenApiMojo mojo = newMojo(base);
        mojo.execute();
        Path ownersApi = base.resolve("target/generated-sources/openapi/com/example/pets/api/v1/OwnersApi.java");
        assertThat(ownersApi).exists();

        String withoutOwners = Files.readString(spec).replaceAll("(?s)  /api/v1/owners/\\{ownerId}:.*?(?=components:)", "");
        Files.writeString(spec, withoutOwners);
        mojo.execute();

        assertThat(ownersApi).doesNotExist();
    }

    @Test
    void withoutASpecTheGoalDoesNothing(@TempDir Path base) throws Exception {
        OpenApiMojo mojo = newMojo(base);

        mojo.execute();

        MavenProject project = (MavenProject) field(mojo, "project");
        assertThat(project.getCompileSourceRoots()).isEmpty();
        assertThat(project.getResources()).isEmpty();
        assertThat(base.resolve("target")).doesNotExist();
    }

    @Test
    void skipDoesNothingEvenWithASpec(@TempDir Path base) throws Exception {
        copyPets(base, "api/v1/openapi.yaml");
        OpenApiMojo mojo = newMojo(base);
        TestSupport.setField(mojo, "skip", true);

        mojo.execute();

        assertThat(base.resolve("target")).doesNotExist();
    }

    @Test
    void springClientPresetGeneratesHttpInterfacesAndShipsNothing(@TempDir Path base) throws Exception {
        Path spec = copyPets(base, "clients/pets/openapi.yaml");
        OpenApiMojo mojo = newMojo(base);
        OpenApiSpecConfig client = new OpenApiSpecConfig();
        client.setFile(spec.toFile());
        client.setPreset("spring-client");
        TestSupport.setField(mojo, "specs", List.of(client));

        mojo.execute();

        Path out = base.resolve("target/generated-sources/openapi");
        // No directory to take the major from: it comes from info.version (1.2.0).
        Path petsApi = out.resolve("com/example/pets/client/v1/PetsApi.java");
        assertThat(Files.readString(petsApi))
                .contains("@HttpExchange(")
                .contains("method = \"GET\"")
                .contains("value = PetsApi.PATH_GET_PET")
                .doesNotContain("@Validated");
        assertThat(InProcessCompiler.compile(out, base.resolve("target/classes"), SPRING_CLASSPATH).diagnostics())
                .isEmpty();

        MavenProject project = (MavenProject) field(mojo, "project");
        assertThat(project.getResources()).isEmpty();
        assertThat(base.resolve("target/generated-resources/openapi/api")).doesNotExist();
    }

    @Test
    void jaxrsPresetGeneratesJakartaRestInterfacesForQuarkus(@TempDir Path base) throws Exception {
        copyPets(base, "api/v1/openapi.yaml");
        OpenApiMojo mojo = newMojo(base);
        TestSupport.setField(mojo, "preset", "jaxrs");

        mojo.execute();

        Path api = base.resolve("target/generated-sources/openapi/com/example/pets/api/v1");
        assertThat(Files.readString(api.resolve("PetsApi.java")))
                .contains("import jakarta.ws.rs.*;")
                .contains("@Path(\"/api/v1/pets\")")
                .contains("public interface PetsApi")
                .contains("Pet getPet(@PathParam(\"petId\") Long petId")
                .doesNotContain("io.swagger")
                .doesNotContain("org.eclipse.microprofile");
        assertThat(Files.readString(api.resolve("model/NewPet.java"))).contains("@NotNull");
        assertThat(base.resolve("target/generated-resources/openapi/api/v1/openapi.yaml")).exists();
    }

    @Test
    void additionalPropertiesOverrideThePreset(@TempDir Path base) throws Exception {
        copyPets(base, "api/v1/openapi.yaml");
        OpenApiMojo mojo = newMojo(base);
        TestSupport.setField(mojo, "additionalProperties", Map.of("useResponseEntity", "false"));

        mojo.execute();

        assertThat(Files.readString(base.resolve(
                "target/generated-sources/openapi/com/example/pets/api/v1/PetsApi.java")))
                .contains("Pet getPet(")
                .doesNotContain("ResponseEntity<Pet> getPet(");
    }

    @Test
    void anInvalidSpecFailsWithTheValidatorsFindings(@TempDir Path base) throws Exception {
        Path spec = base.resolve("api/v1/openapi.yaml");
        Files.createDirectories(spec.getParent());
        Files.writeString(spec, """
                openapi: 3.0.3
                paths:
                  /api/v1/pets:
                    get:
                      operationId: listPets
                      responses:
                        '200': {description: ok}
                """);

        assertThatThrownBy(() -> newMojo(base).execute())
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("api/v1/openapi.yaml is not a valid OpenAPI document")
                .hasMessageContaining("info");
    }

    @Test
    void specsGeneratingIntoTheSamePackageOrServingTheSameMajorAreRejected(@TempDir Path base) throws Exception {
        copyPets(base, "api/v1/openapi.yaml");
        Path elsewhere = copyPets(base, "legacy/openapi.yaml");
        OpenApiMojo mojo = newMojo(base);
        OpenApiSpecConfig legacy = new OpenApiSpecConfig();
        legacy.setFile(elsewhere.toFile());
        TestSupport.setField(mojo, "specs", List.of(legacy));

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("both generate into package com.example.pets.api.v1");

        legacy.setPackageName("com.example.pets.legacy");
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("both describe this service's /api/v1");
    }

    @Test
    void configuredSpecsAreValidated(@TempDir Path base) throws Exception {
        OpenApiMojo mojo = newMojo(base);
        OpenApiSpecConfig missing = new OpenApiSpecConfig();
        missing.setFile(base.resolve("nope.yaml").toFile());
        TestSupport.setField(mojo, "specs", List.of(missing));
        assertThatThrownBy(mojo::execute).hasMessageContaining("does not exist");

        Path unversioned = base.resolve("other/openapi.yaml");
        Files.createDirectories(unversioned.getParent());
        Files.writeString(unversioned, "openapi: 3.0.3\ninfo: {title: x, version: latest}\npaths: {}\n");
        OpenApiSpecConfig noMajor = new OpenApiSpecConfig();
        noMajor.setFile(unversioned.toFile());
        TestSupport.setField(mojo, "specs", List.of(noMajor));
        assertThatThrownBy(mojo::execute).hasMessageContaining("set <major>");

        noMajor.setMajor(3);
        noMajor.setPackageName("not a package");
        assertThatThrownBy(mojo::execute).hasMessageContaining("is not a Java package name");

        noMajor.setPackageName(null);
        noMajor.setPreset("feign");
        assertThatThrownBy(mojo::execute).hasMessageContaining("Unknown OpenAPI preset 'feign'");
    }

    @Test
    void groupIdsBecomeValidPackageNames() {
        assertThat(AbstractOpenApiMojo.javaPackage("io.adhar.my-service")).isEqualTo("io.adhar.my_service");
        assertThat(AbstractOpenApiMojo.javaPackage("com.example.class")).isEqualTo("com.example._class");
        assertThat(AbstractOpenApiMojo.javaPackage("1st..pkg")).isEqualTo("_1st.pkg");
        assertThat(AbstractOpenApiMojo.javaPackage(" ")).isEqualTo("api");
    }

    private static OpenApiMojo newMojo(Path base) {
        Model model = new Model();
        model.setGroupId("com.example.pets");
        model.setArtifactId("pets");
        model.setVersion("1.0.0");
        Build build = new Build();
        build.setDirectory(base.resolve("target").toString());
        model.setBuild(build);
        MavenProject project = new MavenProject(model);
        project.setFile(base.resolve("pom.xml").toFile());

        OpenApiMojo mojo = new OpenApiMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "apiDirectory", base.resolve("api").toFile());
        TestSupport.setField(mojo, "discover", true);
        TestSupport.setField(mojo, "preset", "spring");
        TestSupport.setField(mojo, "basePackage", "com.example.pets");
        TestSupport.setField(mojo, "outputDirectory", base.resolve("target/generated-sources/openapi").toFile());
        TestSupport.setField(mojo, "resourcesDirectory", base.resolve("target/generated-resources/openapi").toFile());
        TestSupport.setField(mojo, "stateDirectory", base.resolve("target/adhar-openapi").toFile());
        TestSupport.setField(mojo, "shipSpecs", true);
        return mojo;
    }

    private static Path copyPets(Path base, String relative, String... replacements) throws Exception {
        String spec = Files.readString(PETS);
        for (int i = 0; i < replacements.length; i += 2) {
            spec = spec.replace(replacements[i], replacements[i + 1]);
        }
        Path target = base.resolve(relative);
        Files.createDirectories(target.getParent());
        return Files.writeString(target, spec);
    }

    private static Map<String, String> contents(Path root) throws Exception {
        Map<String, String> files = new TreeMap<>();
        for (String relative : relativeFiles(root)) {
            files.put(relative, Files.readString(root.resolve(relative)));
        }
        return files;
    }

    private static List<String> relativeFiles(Path root) throws Exception {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).map(p -> root.relativize(p).toString()).sorted().toList();
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
