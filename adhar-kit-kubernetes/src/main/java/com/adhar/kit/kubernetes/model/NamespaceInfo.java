package com.adhar.kit.kubernetes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Namespace information model.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NamespaceInfo {

    private String name;

    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    private String status;

    /**
     * Checks if namespace is active.
     *
     * @return true if active
     */
    public boolean isActive() {
        return "Active".equalsIgnoreCase(status);
    }
}

