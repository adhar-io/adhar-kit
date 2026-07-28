package com.adhar.kit.docs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Security configuration.
     */
    private Security security = new Security();

    /**
     * API groups.
     */
    private List<Group> groups = new ArrayList<>();

    /**
     * OpenAPI breaking-change diff (CI gate) configuration.
     */
    private Diff diff = new Diff();

    /**
     * AsyncAPI generation/export configuration.
     */
    private AsyncApi asyncApi = new AsyncApi();

    /**
     * Get API info (compatibility method).
     */
    public ApiInfo getApiInfo() {
        ApiInfo apiInfo = new ApiInfo();
        apiInfo.setTitle(title);
        apiInfo.setDescription(description);
        apiInfo.setVersion(version);
        apiInfo.setTermsOfService(termsOfServiceUrl);
        apiInfo.setContact(contact);
        apiInfo.setLicense(license);
        return apiInfo;
    }

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

    @Data
    public static class ApiInfo {
        private String title;
        private String description;
        private String version;
        private String termsOfService;
        private Contact contact;
        private License license;
    }

    @Data
    public static class Security {
        private boolean enabled = true;
        private String schemeName = "bearer-jwt";
        private String scheme = "bearer";
        private String bearerFormat = "JWT";
        private String description = "JWT authentication";
    }

    @Data
    public static class Group {
        private String name;
        private String displayName;
        private String[] packagesToScan = new String[0];
        private String[] pathsToMatch = new String[0];
    }

    /**
     * Configuration for the OpenAPI breaking-change diff service.
     */
    @Data
    public static class Diff {
        /**
         * Enable the {@code OpenApiDiffService} bean for use as a CI gate.
         */
        private boolean enabled = false;

        /**
         * Whether a CI gate should fail the build when breaking changes are detected.
         */
        private boolean failOnBreaking = true;
    }

    /**
     * Configuration for AsyncAPI generation and export.
     */
    @Data
    public static class AsyncApi {
        /**
         * Enable AsyncAPI generation/export beans.
         */
        private boolean enabled = false;

        /**
         * AsyncAPI document title.
         */
        private String title = "Adhar Async API";

        /**
         * AsyncAPI document version.
         */
        private String version = "1.0.0";

        /**
         * AsyncAPI document description.
         */
        private String description = "AsyncAPI documentation for Adhar platform messaging";

        /**
         * Channel/topic definitions.
         */
        private List<Channel> channels = new ArrayList<>();
    }

    /**
     * A single AsyncAPI channel/topic definition.
     */
    @Data
    public static class Channel {
        /**
         * Channel identifier (used as the channel key and operation prefix).
         */
        private String name;

        /**
         * Channel address (topic/queue name); defaults to {@code name} if blank.
         */
        private String address;

        /**
         * Human-readable channel description.
         */
        private String description;

        /**
         * Operation direction: {@code SEND}, {@code RECEIVE}, or {@code SEND_AND_RECEIVE}.
         */
        private String action = "SEND";

        /**
         * Message name; defaults to {@code <name>Message} if blank.
         */
        private String messageName;

        /**
         * JSON schema type of the message payload.
         */
        private String payloadType = "object";
    }
}

