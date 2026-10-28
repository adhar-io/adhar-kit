package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.catalog.CatalogUpdater;
import com.adhar.kit.maven.openapi.ApiSpec;
import com.adhar.kit.maven.openapi.ApiSpecs;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Declares the service's APIs in its {@code catalog-info.yaml}: a {@code kind: API}
 * entity per {@code api/v<major>/openapi.yaml} - its {@code definition} is that file -
 * and the Component's {@code spec.providesApis} naming them, which is how the platform's
 * console finds a service's APIs.
 *
 * <p>It writes to the source tree, so it is not bound to the lifecycle: run it when a
 * major is added or removed, or bind it with {@code check} in CI to fail a build whose
 * catalog lags behind its specs.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * mvn adhar:catalog
 * mvn adhar:catalog -Dadhar.catalog.check=true
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Mojo(name = "catalog", defaultPhase = LifecyclePhase.NONE, threadSafe = true)
public class CatalogMojo extends AbstractOpenApiMojo {

    /**
     * The catalog file to update.
     */
    @Parameter(property = "adhar.catalog.file", defaultValue = "${project.basedir}/catalog-info.yaml")
    private File catalogFile;

    /**
     * Name of the Component that provides the APIs; needed only when the file declares
     * more than one.
     */
    @Parameter(property = "adhar.catalog.component")
    private String component;

    /**
     * Fail instead of writing when the catalog is out of date.
     */
    @Parameter(property = "adhar.catalog.check", defaultValue = "false")
    private boolean check;

    private final CatalogUpdater updater = new CatalogUpdater();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Catalog update skipped");
            return;
        }
        if (!catalogFile.isFile()) {
            throw new MojoExecutionException("No catalog file at " + catalogFile
                    + "; every platform service has one (set adhar.catalog.file if it lives elsewhere)");
        }
        Path catalogDirectory = catalogFile.toPath().toAbsolutePath().normalize().getParent();
        List<CatalogUpdater.Api> apis = new ArrayList<>();
        for (ApiSpec spec : resolveSpecs()) {
            if (spec.shipped()) {
                apis.add(api(spec, catalogDirectory));
            }
        }

        try {
            String current = Files.readString(catalogFile.toPath(), StandardCharsets.UTF_8);
            CatalogUpdater.Result result = updater.update(current, component, apis);
            if (!result.changed()) {
                getLog().info(relative(catalogFile.toPath()) + " declares " + result.apiNames());
                return;
            }
            if (check) {
                throw new MojoFailureException(relative(catalogFile.toPath()) + " does not declare the APIs this"
                        + " service ships (" + result.apiNames() + "); run mvn adhar:catalog and commit it");
            }
            Files.writeString(catalogFile.toPath(), result.text(), StandardCharsets.UTF_8);
            getLog().info("Updated " + relative(catalogFile.toPath()) + ": provides " + result.apiNames());
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot update " + catalogFile + ": " + e.getMessage(), e);
        }
    }

    private CatalogUpdater.Api api(ApiSpec spec, Path catalogDirectory) throws MojoExecutionException {
        try {
            JsonNode info = ApiSpecs.read(spec.file()).path("info");
            String definition = catalogDirectory.relativize(spec.file().toAbsolutePath().normalize())
                    .toString().replace('\\', '/');
            return new CatalogUpdater.Api(spec.major(), info.path("version").asText(spec.major() + ".0.0"),
                    info.path("title").asText(null), info.path("description").asText(null),
                    definition.startsWith(".") ? definition : "./" + definition);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read " + spec.file() + ": " + e.getMessage(), e);
        }
    }
}
