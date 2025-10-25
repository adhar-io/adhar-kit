package com.adhar.kit.commons.constant;

/**
 * Common constants used throughout the Adhar platform.
 */
public final class CommonConstants {

    // HTTP Headers
    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    public static final String HEADER_USER_ID = "X-User-ID";
    public static final String HEADER_TENANT_ID = "X-Tenant-ID";
    public static final String HEADER_API_VERSION = "X-API-Version";

    // Error Codes
    public static final String ERROR_VALIDATION = "VALIDATION_ERROR";
    public static final String ERROR_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String ERROR_SERVICE = "SERVICE_ERROR";
    public static final String ERROR_AUTHENTICATION = "AUTHENTICATION_ERROR";
    public static final String ERROR_AUTHORIZATION = "AUTHORIZATION_ERROR";
    public static final String ERROR_INTERNAL_SERVER = "INTERNAL_SERVER_ERROR";
    public static final String ERROR_BAD_REQUEST = "BAD_REQUEST";
    public static final String ERROR_CONFLICT = "CONFLICT_ERROR";

    // Pagination defaults
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_NUMBER = 0;

    // Date and Time formats
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String DATETIME_FORMAT_WITH_ZONE = "yyyy-MM-dd'T'HH:mm:ssXXX";

    // Status values
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DELETED = "DELETED";

    // Common field names
    public static final String FIELD_ID = "id";
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_UPDATED_AT = "updatedAt";
    public static final String FIELD_CREATED_BY = "createdBy";
    public static final String FIELD_UPDATED_BY = "updatedBy";
    public static final String FIELD_VERSION = "version";

    // Cache keys
    public static final String CACHE_KEY_SEPARATOR = "::";
    public static final String CACHE_PREFIX_USER = "user";
    public static final String CACHE_PREFIX_TENANT = "tenant";
    public static final String CACHE_PREFIX_CONFIG = "config";

    // Validation messages
    public static final String VALIDATION_NOT_NULL = "Field cannot be null";
    public static final String VALIDATION_NOT_EMPTY = "Field cannot be empty";
    public static final String VALIDATION_NOT_BLANK = "Field cannot be blank";
    public static final String VALIDATION_INVALID_EMAIL = "Invalid email format";
    public static final String VALIDATION_INVALID_PHONE = "Invalid phone number format";

    // Private constructor to prevent instantiation
    private CommonConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
