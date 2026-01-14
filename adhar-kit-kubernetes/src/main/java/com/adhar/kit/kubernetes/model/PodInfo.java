package com.adhar.kit.kubernetes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Pod information model.
 *
 * <p>Contains metadata and status information about the current pod.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * PodInfo podInfo = kubernetesClient.getCurrentPodInfo();
 * String podName = podInfo.getName();
 * String podIp = podInfo.getIp();
 * String namespace = podInfo.getNamespace();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PodInfo {

    /**
     * Pod name.
     */
    private String name;

    /**
     * Pod namespace.
     */
    private String namespace;

    /**
     * Pod IP address.
     */
    private String ip;

    /**
     * Node name where pod is running.
     */
    private String nodeName;

    /**
     * Pod phase (Running, Pending, Succeeded, Failed, Unknown).
     */
    private String phase;

    /**
     * Service account name.
     */
    private String serviceAccount;

    /**
     * Pod labels.
     */
    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    /**
     * Pod annotations.
     */
    @Builder.Default
    private Map<String, String> annotations = new HashMap<>();

    /**
     * Pod UID.
     */
    private String uid;

    /**
     * Host IP.
     */
    private String hostIp;

    /**
     * Checks if pod is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return "Running".equalsIgnoreCase(phase);
    }
}

