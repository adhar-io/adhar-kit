package com.adhar.apidocs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for API documentation.
 */
@Data
@ConfigurationProperties(prefix = "adhar.api-docs")
public class ApiDocsProperties {

    /**
     * Enable or disable API documentation.
     */
    private boolean enabled = true;

    /**
     * API title.
     */
    private String title;

    /**
     * API version.
     */
    private String version = "1.0.0";

    /**
     * API description.
     */
    private String description = "API Documentation";

    /**
     * Contact information.
     */
    private Contact contact;

    /**
     * License information.
     */
    private License license;

    /**
     * Server configurations.
     */
    private List<Server> servers = new ArrayList<>();

    /**
     * Contact information.
     */
    @Data
    public static class Contact {
        /**
         * Contact name.
         */
        private String name;

        /**
         * Contact email.
         */
        private String email;

        /**
         * Contact URL.
         */
        private String url;
    }

    /**
     * License information.
     */
    @Data
    public static class License {
        /**
         * License name.
         */
        private String name;

        /**
         * License URL.
         */
        private String url;
    }

    /**
     * Server information.
     */
    @Data
    public static class Server {
        /**
         * Server URL.
         */
        private String url;

        /**
         * Server description.
         */
        private String description;
    }
}