package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.openapi.ApiSpec;
import com.adhar.kit.maven.openapi.LintFinding;
import com.adhar.kit.maven.openapi.OpenApiLinter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.List;

/**
 * Checks the service's OpenAPI specs against the platform's API contract before code
 * is generated from them, and fails the build on any violation.
 *
 * <p>The contract (see {@link OpenApiLinter}): OpenAPI 3.0.x; a semantic
 * {@code info.version} whose major is the directory's; every path under
 * {@code /api/v<major>}; an operationId and a tag on every operation; every 4xx, 5xx
 * and default response as {@code application/problem+json}; no {@code $ref} leaving
 * the document. Only the service's own specs are linted - a client spec describes
 * somebody else's API.</p>
 *
 * <p><b>Usage</b> (adhar-kit-parent binds this goal to {@code validate}):</p>
 * <pre>{@code
 * mvn adhar:openapi-lint
 * mvn adhar:openapi-lint -Dadhar.openapi.lint.failOnWarnings=true
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Mojo(name = "openapi-lint", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class OpenApiLintMojo extends AbstractOpenApiMojo {

    /**
     * Whether warnings (conventions such as named body schemas) fail the build too.
     */
    @Parameter(property = "adhar.openapi.lint.failOnWarnings", defaultValue = "false")
    private boolean failOnWarnings;

    /**
     * Skips linting while still generating.
     */
    @Parameter(property = "adhar.openapi.lint.skip", defaultValue = "false")
    private boolean skipLint;

    private final OpenApiLinter linter = new OpenApiLinter();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip || skipLint) {
            getLog().info("OpenAPI lint skipped");
            return;
        }
        int errors = 0;
        int warnings = 0;
        int linted = 0;
        for (ApiSpec spec : resolveSpecs()) {
            if (!spec.shipped()) {
                continue;
            }
            linted++;
            String file = relative(spec.file());
            List<LintFinding> findings = linter.lint(spec.file(), spec.major());
            for (LintFinding finding : findings) {
                if (finding.isError()) {
                    errors++;
                    getLog().error(file + ": " + finding);
                } else {
                    warnings++;
                    getLog().warn(file + ": " + finding);
                }
            }
            if (findings.isEmpty()) {
                getLog().info(file + ": follows the API contract");
            }
        }
        if (linted == 0) {
            getLog().debug("No OpenAPI spec of this service to lint");
            return;
        }
        if (errors > 0 || (failOnWarnings && warnings > 0)) {
            throw new MojoFailureException("OpenAPI lint found " + errors + " error(s) and " + warnings
                    + " warning(s); see the log above");
        }
    }
}
