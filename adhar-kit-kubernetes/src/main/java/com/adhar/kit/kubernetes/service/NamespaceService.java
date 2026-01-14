package com.adhar.kit.kubernetes.service;

import com.adhar.kit.kubernetes.model.NamespaceInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Service for managing Kubernetes namespaces.
 *
 * <p>Provides comprehensive namespace management:</p>
 * <ul>
 *   <li>Create and delete namespaces</li>
 *   <li>Manage resource quotas</li>
 *   <li>Manage limit ranges</li>
 *   <li>Namespace isolation</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Autowired
 * private NamespaceService namespaceService;
 *
 * // List namespaces
 * List<NamespaceInfo> namespaces = namespaceService.listNamespaces();
 *
 * // Get namespace
 * NamespaceInfo ns = namespaceService.getNamespace("production");
 *
 * // Check namespace status
 * if (ns.isActive()) {
 *     log.info("Namespace is active");
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class NamespaceService {

    private final io.fabric8.kubernetes.client.KubernetesClient client;

    /**
     * Creates namespace service.
     */
    public NamespaceService() {
        this.client = createFabric8Client();
    }

    /**
     * Gets namespace information.
     *
     * @param name namespace name
     * @return namespace information
     */
    public NamespaceInfo getNamespace(String name) {
        try {
            io.fabric8.kubernetes.api.model.Namespace namespace = client.namespaces()
                .withName(name)
                .get();

            if (namespace == null) {
                log.warn("Namespace {} not found", name);
                return null;
            }

            return toNamespaceInfo(namespace);

        } catch (Exception e) {
            log.error("Failed to get namespace {}", name, e);
            return null;
        }
    }

    /**
     * Lists all namespaces.
     *
     * @return list of namespaces
     */
    public List<NamespaceInfo> listNamespaces() {
        try {
            io.fabric8.kubernetes.api.model.NamespaceList namespaceList = client.namespaces()
                .list();

            return namespaceList.getItems().stream()
                .map(this::toNamespaceInfo)
                .toList();

        } catch (Exception e) {
            log.error("Failed to list namespaces", e);
            return List.of();
        }
    }

    /**
     * Creates a namespace.
     *
     * @param name namespace name
     * @param labels labels to apply
     * @return true if successful
     */
    public boolean createNamespace(String name, Map<String, String> labels) {
        try {
            io.fabric8.kubernetes.api.model.Namespace namespace =
                new io.fabric8.kubernetes.api.model.NamespaceBuilder()
                    .withNewMetadata()
                        .withName(name)
                        .withLabels(labels)
                    .endMetadata()
                    .build();

            client.namespaces().resource(namespace).create();

            log.info("Created namespace: {}", name);
            return true;

        } catch (Exception e) {
            log.error("Failed to create namespace {}", name, e);
            return false;
        }
    }

    /**
     * Deletes a namespace.
     *
     * @param name namespace name
     * @return true if successful
     */
    public boolean deleteNamespace(String name) {
        try {
            client.namespaces()
                .withName(name)
                .delete();

            log.info("Deleted namespace: {}", name);
            return true;

        } catch (Exception e) {
            log.error("Failed to delete namespace {}", name, e);
            return false;
        }
    }

    /**
     * Checks if namespace exists.
     *
     * @param name namespace name
     * @return true if exists
     */
    public boolean namespaceExists(String name) {
        try {
            return client.namespaces()
                .withName(name)
                .get() != null;

        } catch (Exception e) {
            log.error("Failed to check namespace existence {}", name, e);
            return false;
        }
    }

    /**
     * Gets namespace labels.
     *
     * @param name namespace name
     * @return labels map
     */
    public Map<String, String> getNamespaceLabels(String name) {
        NamespaceInfo namespace = getNamespace(name);
        return namespace != null ? namespace.getLabels() : Map.of();
    }

    /**
     * Creates Fabric8 Kubernetes client.
     */
    private io.fabric8.kubernetes.client.KubernetesClient createFabric8Client() {
        return new io.fabric8.kubernetes.client.KubernetesClientBuilder().build();
    }

    /**
     * Converts Fabric8 Namespace to NamespaceInfo.
     */
    private NamespaceInfo toNamespaceInfo(io.fabric8.kubernetes.api.model.Namespace namespace) {
        return NamespaceInfo.builder()
            .name(namespace.getMetadata().getName())
            .labels(namespace.getMetadata().getLabels())
            .status(namespace.getStatus() != null ?
                namespace.getStatus().getPhase() : "Unknown")
            .build();
    }
}

