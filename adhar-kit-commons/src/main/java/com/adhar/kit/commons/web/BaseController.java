package com.adhar.kit.commons.web;

import com.adhar.kit.commons.constant.CommonConstants;
import com.adhar.kit.commons.model.ApiResponse;
import com.adhar.kit.commons.model.ErrorResponse;
import com.adhar.kit.commons.model.PagedResult;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

/**
 * Base controller class providing common functionality for REST controllers.
 *
 * <p>Framework-agnostic base class that works with Spring, Quarkus, and Micronaut.</p>
 *
 * <p><b>Example - Spring:</b></p>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/users")
 * public class UserController extends BaseController {
 *
 *     @GetMapping("/{id}")
 *     public ResponseEntity<ApiResponse<User>> getUser(@PathVariable String id) {
 *         User user = userService.findById(id);
 *         return success(user);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Quarkus:</b></p>
 * <pre>{@code
 * @Path("/api/users")
 * public class UserController extends BaseController {
 *
 *     @GET
 *     @Path("/{id}")
 *     public Response getUser(@PathParam("id") String id) {
 *         User user = userService.findById(id);
 *         return Response.ok(successResponse(user)).build();
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public abstract class BaseController {

    /**
     * HTTP status codes.
     */
    protected static class HttpStatus {
        public static final int OK = 200;
        public static final int CREATED = 201;
        public static final int NO_CONTENT = 204;
        public static final int BAD_REQUEST = 400;
        public static final int NOT_FOUND = 404;
        public static final int CONFLICT = 409;
        public static final int INTERNAL_SERVER_ERROR = 500;
    }

    // ==================== SUCCESS RESPONSES ====================

    // ==================== SUCCESS RESPONSES ====================

    /**
     * Creates a successful response with data.
     *
     * @param data the response data
     * @param <T> the data type
     * @return API response with success
     */
    protected <T> ApiResponse<T> successResponse(T data) {
        return ApiResponse.success(data);
    }

    /**
     * Creates a successful response with data and message.
     *
     * @param data the response data
     * @param message the success message
     * @param <T> the data type
     * @return API response with success
     */
    protected <T> ApiResponse<T> successResponse(T data, String message) {
        return ApiResponse.success(data, message);
    }

    /**
     * Creates a successful response with only a message.
     *
     * @param message the success message
     * @return API response with success
     */
    protected ApiResponse<Void> successResponse(String message) {
        return ApiResponse.success(message);
    }

    /**
     * Creates a created response with data.
     *
     * @param data the created resource data
     * @param <T> the data type
     * @return API response with created status
     */
    protected <T> ApiResponse<T> createdResponse(T data) {
        return ApiResponse.success(data, "Resource created successfully");
    }

    /**
     * Creates a created response with data and message.
     *
     * @param data the created resource data
     * @param message the success message
     * @param <T> the data type
     * @return API response with created status
     */
    protected <T> ApiResponse<T> createdResponse(T data, String message) {
        return ApiResponse.success(data, message);
    }

    // ==================== ERROR RESPONSES ====================

    /**
     * Creates an error response.
     *
     * @param errorCode the error code
     * @param message the error message
     * @return API response with error
     */
    protected ApiResponse<Void> errorResponse(String errorCode, String message) {
        ErrorResponse error = ErrorResponse.of(errorCode, message);
        return ApiResponse.error(error);
    }

    /**
     * Creates a bad request error response.
     *
     * @param message the error message
     * @return API response with bad request error
     */
    protected ApiResponse<Void> badRequestResponse(String message) {
        return errorResponse(CommonConstants.ERROR_BAD_REQUEST, message);
    }

    /**
     * Creates a not found error response.
     *
     * @param message the error message
     * @return API response with not found error
     */
    protected ApiResponse<Void> notFoundResponse(String message) {
        return errorResponse(CommonConstants.ERROR_RESOURCE_NOT_FOUND, message);
    }

    /**
     * Creates a conflict error response.
     *
     * @param message the error message
     * @return API response with conflict error
     */
    protected ApiResponse<Void> conflictResponse(String message) {
        return errorResponse(CommonConstants.ERROR_CONFLICT, message);
    }

    /**
     * Creates an internal server error response.
     *
     * @param message the error message
     * @return API response with server error
     */
    protected ApiResponse<Void> serverErrorResponse(String message) {
        return errorResponse(CommonConstants.ERROR_INTERNAL_SERVER, message);
    }

    /**
     * Creates a paginated response.
     *
     * @param pagedResult the paged result data
     * @param <T> the data type
     * @return API response with paginated data
     */
    protected <T> ApiResponse<PagedResult<T>> paginatedResponse(PagedResult<T> pagedResult) {
        return ApiResponse.success(pagedResult);
    }

    // ==================== REQUEST UTILITIES ====================

    /**
     * Gets or generates a request ID.
     *
     * @param headers request headers
     * @return request ID
     */
    protected String getOrGenerateRequestId(Map<String, String> headers) {
        String requestId = headers.get(CommonConstants.HEADER_REQUEST_ID);
        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        return requestId;
    }

    /**
     * Gets the correlation ID from headers.
     *
     * @param headers request headers
     * @return correlation ID or null
     */
    protected String getCorrelationId(Map<String, String> headers) {
        return headers.get(CommonConstants.HEADER_CORRELATION_ID);
    }

    /**
     * Gets the user ID from headers.
     *
     * @param headers request headers
     * @return user ID or null
     */
    protected String getUserId(Map<String, String> headers) {
        return headers.get(CommonConstants.HEADER_USER_ID);
    }

    /**
     * Gets the tenant ID from headers.
     *
     * @param headers request headers
     * @return tenant ID or null
     */
    protected String getTenantId(Map<String, String> headers) {
        return headers.get(CommonConstants.HEADER_TENANT_ID);
    }

    /**
     * Logs incoming request.
     *
     * @param methodName controller method name
     * @param uri request URI
     * @param requestId request ID
     */
    protected void logRequest(String methodName, String uri, String requestId) {
        log.info("Processing request - Method: {}, URI: {}, RequestId: {}",
                methodName, uri, requestId);
    }

    /**
     * Creates an error response with request context.
     *
     * @param headers request headers
     * @param uri request URI
     * @param errorCode error code
     * @param message error message
     * @return API response with error and context
     */
    protected ApiResponse<Void> errorWithContext(
            Map<String, String> headers, String uri, String errorCode, String message) {

        String requestId = getOrGenerateRequestId(headers);

        ErrorResponse error = ErrorResponse.of(errorCode, message)
                .withRequestId(requestId)
                .withPath(uri);

        return ApiResponse.<Void>error(error)
                .withRequestId(requestId);
    }
}
