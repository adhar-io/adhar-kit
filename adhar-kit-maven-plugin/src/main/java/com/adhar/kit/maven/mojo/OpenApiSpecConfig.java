package com.adhar.kit.maven.mojo;

import java.io.File;

/**
 * One explicitly configured spec of the {@code openapi} goals, in addition to (or
 * instead of) the {@code api/v<major>/openapi.yaml} files found by convention -
 * typically another service's API to generate a client for:
 *
 * <pre>{@code
 * <specs>
 *   <spec>
 *     <file>${project.basedir}/api-clients/payments/v1/openapi.yaml</file>
 *     <preset>spring-client</preset>
 *     <packageName>com.example.orders.clients.payments.v1</packageName>
 *   </spec>
 * </specs>
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class OpenApiSpecConfig {

    /** The spec file (required). */
    private File file;

    /** {@code spring}, {@code jaxrs} or {@code spring-client}; defaults to the goal's preset. */
    private String preset;

    /**
     * Package of the generated interfaces (models go to {@code <packageName>.model});
     * defaults to {@code <basePackage>.api.v<major>} for server presets and
     * {@code <basePackage>.client.v<major>} for {@code spring-client}.
     */
    private String packageName;

    /**
     * The major version; defaults to the {@code v<major>} directory the file is in, else
     * the major of its {@code info.version}.
     */
    private Integer major;

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getPreset() {
        return preset;
    }

    public void setPreset(String preset) {
        this.preset = preset;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public Integer getMajor() {
        return major;
    }

    public void setMajor(Integer major) {
        this.major = major;
    }
}
