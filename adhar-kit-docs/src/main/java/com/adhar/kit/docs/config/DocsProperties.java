package com.adhar.kit.docs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for API documentation.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.docs")
public class DocsProperties {

    /**
     * Enable/disable API documentation.
     */
    private boolean enabled = true;

    /**
     * Documentation title.
     */
    private String title = "Adhar API Documentation";

    /**
     * Documentation description.
     */
    private String description = "API documentation for Adhar platform services";

    /**
     * API version.
     */
    private String version = "1.0.0";

    /**
     * Terms of service URL.
     */
    private String termsOfServiceUrl;

    /**
     * Contact information.
     */
    private Contact contact = new Contact();

    /**
     * License information.
     */
    private License license = new License();

    /**
     * Base path for API.
     */
    private String basePath = "/api";

    /**
     * Whether to show actuator endpoints in docs.
     */
    private boolean showActuator = false;

    @Data
    public static class Contact {
        private String name = "Adhar Platform Team";
        private String email;
        private String url;
    }

    @Data
    public static class License {
        private String name = "Apache 2.0";
        private String url = "https://www.apache.org/licenses/LICENSE-2.0";
    }
}

