package com.adhar.kit.kubernetes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * ReplicaSet information model.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplicaSetInfo {

    private String name;
    private String namespace;
    private Integer replicas;
    private Integer readyReplicas;

    @Builder.Default
    private Map<String, String> labels = new HashMap<>();
}

