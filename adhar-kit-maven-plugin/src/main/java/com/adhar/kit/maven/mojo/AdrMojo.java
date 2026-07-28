package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.adr.AdrGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Maven Mojo that generates Architecture Decision Record (ADR) markdown files
 * with sequential numbering under {@code docs/adr/}.
 *
 * <p>Each ADR is written as {@code NNNN-slugified-title.md} and pre-populated
 * with the standard Status/Context/Decision/Consequences template.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * mvn adhar:adr -Dadr.title="Use PostgreSQL for persistence"
 * mvn adhar:adr -Dadr.title="Adopt hexagonal architecture" -Dadr.status=Accepted
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Mojo(name = "adr", defaultPhase = LifecyclePhase.NONE)
public class AdrMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The decision title, e.g. "Use PostgreSQL for persistence".
     */
    @Parameter(property = "adr.title", required = true)
    private String title;

    /**
     * The ADR status, e.g. "Proposed", "Accepted", or "Superseded".
     */
    @Parameter(property = "adr.status", defaultValue = "Proposed")
    private String status;

    /**
     * Directory under which ADR markdown files are written.
     */
    @Parameter(property = "adr.dir", defaultValue = "${project.basedir}/docs/adr")
    private File adrDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("====================================================");
        getLog().info("  Adhar Kit ADR Generator");
        getLog().info("====================================================");

        try {
            AdrGenerator generator = new AdrGenerator(adrDirectory, getLog());
            File adr = generator.create(title, status);

            getLog().info("====================================================");
            getLog().info("  ADR created: " + adr);
            getLog().info("====================================================");
        } catch (Exception e) {
            throw new MojoExecutionException("ADR generation failed: " + e.getMessage(), e);
        }
    }
}
