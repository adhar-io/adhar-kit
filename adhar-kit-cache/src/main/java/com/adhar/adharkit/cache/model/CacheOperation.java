package com.adhar.adharkit.cache.model;

/**
 * Enum representing the type of cache operation.
 */
public enum CacheOperation {
    /**
     * Put a value in the cache.
     */
    PUT,
    
    /**
     * Evict a specific key from the cache.
     */
    EVICT,
    
    /**
     * Clear all entries from the cache.
     */
    CLEAR
}