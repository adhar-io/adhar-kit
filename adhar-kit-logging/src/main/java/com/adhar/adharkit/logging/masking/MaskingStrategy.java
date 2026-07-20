package com.adhar.adharkit.logging.masking;

/**
 * Strategy used when masking a sensitive value.
 */
public enum MaskingStrategy {

    /** Replace the entire value with a fixed mask ({@code ********}). */
    FULL,

    /** Keep the last four characters and mask the rest (falls back to FULL for short values). */
    PARTIAL,

    /** Replace the value with a truncated SHA-256 digest so equal values remain correlatable. */
    HASH
}