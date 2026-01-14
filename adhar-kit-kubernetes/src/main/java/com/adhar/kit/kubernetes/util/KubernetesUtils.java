package com.adhar.kit.kubernetes.util;

import com.adhar.kit.kubernetes.model.PodInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for Kubernetes operations.
 *
 * <p>Provides helper methods for common Kubernetes tasks:</p>
 * <ul>
 *   <li>Label and annotation parsing</li>
 *   <li>Resource name validation</li>
 *   <li>Namespace resolution</li>
 *   <li>Environment variable helpers</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Check if running in Kubernetes
 * boolean inK8s = KubernetesUtils.isRunningInKubernetes();
 *
 * // Get pod name from environment
 * String podName = KubernetesUtils.getPodName();
 *
 * // Parse label selector
 * Map<String, String> labels = KubernetesUtils.parseLabelSelector("app=order,env=prod");
 *
 * // Validate resource name
 * boolean valid = KubernetesUtils.isValidResourceName("my-service");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public final class KubernetesUtils {

    private KubernetesUtils() {
        // Utility class
    }

    /**
     * Checks if running inside Kubernetes.
     *
     * @return true if running in Kubernetes
     */
    public static boolean isRunningInKubernetes() {
        return System.getenv("KUBERNETES_SERVICE_HOST") != null &&
               System.getenv("KUBERNETES_SERVICE_PORT") != null;
    }

    /**
     * Gets pod name from environment.
     *
     * @return pod name or null
     */
    public static String getPodName() {
        return System.getenv("HOSTNAME");
    }

    /**
     * Gets namespace from environment.
     *
     * @return namespace or "default"
     */
    public static String getNamespace() {
        String namespace = System.getenv("POD_NAMESPACE");
        return namespace != null ? namespace : "default";
    }

    /**
     * Gets pod IP from environment.
     *
     * @return pod IP or null
     */
    public static String getPodIp() {
        return System.getenv("POD_IP");
    }

    /**
     * Gets node name from environment.
     *
     * @return node name or null
     */
    public static String getNodeName() {
        return System.getenv("NODE_NAME");
    }

    /**
     * Gets service account name from environment.
     *
     * @return service account name or null
     */
    public static String getServiceAccountName() {
        return System.getenv("SERVICE_ACCOUNT");
    }

    /**
     * Parses label selector string into map.
     *
     * @param labelSelector label selector (e.g., "app=order,env=prod")
     * @return map of labels
     */
    public static Map<String, String> parseLabelSelector(String labelSelector) {
        Map<String, String> labels = new HashMap<>();

        if (labelSelector == null || labelSelector.trim().isEmpty()) {
            return labels;
        }

        String[] parts = labelSelector.split(",");
        for (String part : parts) {
            String[] kv = part.trim().split("=");
            if (kv.length == 2) {
                labels.put(kv[0].trim(), kv[1].trim());
            }
        }

        return labels;
    }

    /**
     * Validates Kubernetes resource name.
     *
     * <p>Valid names must:</p>
     * <ul>
     *   <li>Be lowercase alphanumeric or '-'</li>
     *   <li>Start and end with alphanumeric</li>
     *   <li>Be max 253 characters</li>
     * </ul>
     *
     * @param name resource name
     * @return true if valid
     */
    public static boolean isValidResourceName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        if (name.length() > 253) {
            return false;
        }

        // Must start and end with alphanumeric
        if (!Character.isLetterOrDigit(name.charAt(0)) ||
            !Character.isLetterOrDigit(name.charAt(name.length() - 1))) {
            return false;
        }

        // Only lowercase alphanumeric or '-'
        return name.matches("[a-z0-9]([a-z0-9-]*[a-z0-9])?");
    }

    /**
     * Validates namespace name.
     *
     * @param namespace namespace name
     * @return true if valid
     */
    public static boolean isValidNamespace(String namespace) {
        return isValidResourceName(namespace);
    }

    /**
     * Creates label selector string from map.
     *
     * @param labels label map
     * @return label selector string
     */
    public static String createLabelSelector(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }

        return labels.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    }

    /**
     * Gets pod info from environment variables.
     *
     * @return pod info
     */
    public static PodInfo getPodInfoFromEnv() {
        return PodInfo.builder()
            .name(getPodName())
            .namespace(getNamespace())
            .ip(getPodIp())
            .nodeName(getNodeName())
            .serviceAccount(getServiceAccountName())
            .build();
    }

    /**
     * Sanitizes resource name to be Kubernetes-compliant.
     *
     * @param name resource name
     * @return sanitized name
     */
    public static String sanitizeResourceName(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }

        // Convert to lowercase
        String sanitized = name.toLowerCase();

        // Replace invalid characters with '-'
        sanitized = sanitized.replaceAll("[^a-z0-9-]", "-");

        // Remove leading/trailing '-'
        sanitized = sanitized.replaceAll("^-+|-+$", "");

        // Ensure starts and ends with alphanumeric
        if (sanitized.isEmpty() || !Character.isLetterOrDigit(sanitized.charAt(0))) {
            sanitized = "x" + sanitized;
        }
        if (!Character.isLetterOrDigit(sanitized.charAt(sanitized.length() - 1))) {
            sanitized = sanitized + "x";
        }

        // Truncate to 253 characters
        if (sanitized.length() > 253) {
            sanitized = sanitized.substring(0, 253);
        }

        return sanitized;
    }

    /**
     * Checks if a pod is ready.
     *
     * @param podInfo pod information
     * @return true if ready
     */
    public static boolean isPodReady(PodInfo podInfo) {
        return podInfo != null && podInfo.isRunning();
    }

    /**
     * Gets Kubernetes API server URL.
     *
     * @return API server URL or null
     */
    public static String getKubernetesApiServer() {
        String host = System.getenv("KUBERNETES_SERVICE_HOST");
        String port = System.getenv("KUBERNETES_SERVICE_PORT");

        if (host != null && port != null) {
            return "https://" + host + ":" + port;
        }

        return null;
    }

    /**
     * Gets service account token path.
     *
     * @return token path
     */
    public static String getServiceAccountTokenPath() {
        return "/var/run/secrets/kubernetes.io/serviceaccount/token";
    }

    /**
     * Gets service account CA cert path.
     *
     * @return CA cert path
     */
    public static String getServiceAccountCACertPath() {
        return "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
    }
}

