package com.adhar.kit.kubernetes.event;

/**
 * Kind of change observed by a Kubernetes watch/informer.
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public enum ChangeType {
    ADDED,
    MODIFIED,
    DELETED
}
