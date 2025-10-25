package com.adhar.kit.commons.web;

import com.adhar.kit.commons.constant.CommonConstants;
import com.adhar.kit.commons.model.ApiResponse;
import com.adhar.kit.commons.model.ErrorResponse;
import com.adhar.kit.commons.model.PagedResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base controller class providing common functionality for REST controllers.
 * Includes standardized response handling, error handling, and request tracking.
 */
@Slf4j
public abstract class BaseController {

    /**
     * Creates a successful response with data.
     *
     * @param data the response data
     * @param <T> the data type
     * @return ResponseEntity with success response
     */
    protected <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * Creates a successful response with data and message.
     *
     * @param data the response data
     * @param message the success message
     * @param <T> the data type
     * @return ResponseEntity with success response
     */
    protected <T> ResponseEntity<ApiResponse<T>> success(T data, String message) {
        return ResponseEntity.ok(ApiResponse.success(data, message));
    }

    /**
     * Creates a successful response with only a message.
     *
     * @param message the success message
     * @return ResponseEntity with success response
     */
    protected ResponseEntity<ApiResponse<Void>> success(String message) {
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    /**
     * Creates a created (201) response with data.
     *
     * @param data the created resource data
     * @param <T> the data type
     * @return ResponseEntity with created status
     */
    protected <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Resource created successfully"));
    }

    /**
     * Creates a created (201) response with data and message.
     *
     * @param data the created resource data
     * @param message the success message
     * @param <T> the data type
     * @return ResponseEntity with created status
     */
    protected <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, message));
    }

    /**
     * Creates a no content (204) response.
     *
     * @return ResponseEntity with no content status
     */
    protected ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    /**
     * Creates an error response with custom status.
     *
     * @param status the HTTP status
     * @param errorCode the error code
     * @param message the error message
     * @return ResponseEntity with error response
     */
    protected ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String errorCode, String message) {
        ErrorResponse error = ErrorResponse.of(errorCode, message);
        return ResponseEntity.status(status)
                .body(ApiResponse.error(error));
    }

    /**
     * Creates a bad request (400) error response.
     *
     * @param message the error message
     * @return ResponseEntity with bad request status
     */
    protected ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return error(HttpStatus.BAD_REQUEST, CommonConstants.ERROR_BAD_REQUEST, message);
    }

    /**
     * Creates a not found (404) error response.
     *
     * @param message the error message
     * @return ResponseEntity with not found status
     */
    protected ResponseEntity<ApiResponse<Void>> notFound(String message) {
        return error(HttpStatus.NOT_FOUND, CommonConstants.ERROR_RESOURCE_NOT_FOUND, message);
    }

    /**
     * Creates a conflict (409) error response.
     *
     * @param message the error message
     * @return ResponseEntity with conflict status
     */
    protected ResponseEntity<ApiResponse<Void>> conflict(String message) {
        return error(HttpStatus.CONFLICT, CommonConstants.ERROR_CONFLICT, message);
    }

    /**
     * Creates an internal server error (500) response.
     *
     * @param message the error message
     * @return ResponseEntity with internal server error status
     */
    protected ResponseEntity<ApiResponse<Void>> internalServerError(String message) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, CommonConstants.ERROR_INTERNAL_SERVER, message);
    }

    /**
     * Creates a paginated response.
     *
     * @param pagedResult the paged result data
     * @param <T> the data type
     * @return ResponseEntity with paginated data
     */
    protected <T> ResponseEntity<ApiResponse<PagedResult<T>>> paginated(PagedResult<T> pagedResult) {
        return ResponseEntity.ok(ApiResponse.success(pagedResult));
    }

    /**
     * Gets the request ID from headers or generates a new one.
     *
     * @param request the HTTP request
     * @return the request ID
     */
    protected String getOrGenerateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(CommonConstants.HEADER_REQUEST_ID);
        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        return requestId;
    }

    /**
     * Gets the correlation ID from headers.
     *
     * @param request the HTTP request
     * @return the correlation ID or null if not present
     */
    protected String getCorrelationId(HttpServletRequest request) {
        return request.getHeader(CommonConstants.HEADER_CORRELATION_ID);
    }

    /**
     * Gets the user ID from headers.
     *
     * @param request the HTTP request
     * @return the user ID or null if not present
     */
    protected String getUserId(HttpServletRequest request) {
        return request.getHeader(CommonConstants.HEADER_USER_ID);
    }

    /**
     * Gets the tenant ID from headers.
     *
     * @param request the HTTP request
     * @return the tenant ID or null if not present
     */
    protected String getTenantId(HttpServletRequest request) {
        return request.getHeader(CommonConstants.HEADER_TENANT_ID);
    }

    /**
     * Logs the incoming request for debugging purposes.
     *
     * @param request the HTTP request
     * @param methodName the controller method name
     */
    protected void logRequest(HttpServletRequest request, String methodName) {
        String requestId = getOrGenerateRequestId(request);
        log.info("Processing request - Method: {}, URI: {}, RequestId: {}",
                methodName, request.getRequestURI(), requestId);
    }

    /**
     * Creates an error response with request context.
     *
     * @param request the HTTP request
     * @param status the HTTP status
     * @param errorCode the error code
     * @param message the error message
     * @return ResponseEntity with error response including request context
     */
    protected ResponseEntity<ApiResponse<Void>> errorWithContext(
            HttpServletRequest request, HttpStatus status, String errorCode, String message) {

        String requestId = getOrGenerateRequestId(request);
        String path = request.getRequestURI();

        ErrorResponse error = ErrorResponse.of(errorCode, message)
                .withRequestId(requestId)
                .withPath(path);

        ApiResponse<Void> response = ApiResponse.error(error)
                .withRequestId(requestId);

        return ResponseEntity.status(status).body(response);
    }
}
