package com.adhar.kit.commons.web;

import com.adhar.kit.commons.constant.CommonConstants;
import com.adhar.kit.commons.context.CorrelationContext;
import com.adhar.kit.commons.exception.AdharException;
import com.adhar.kit.commons.exception.AdharExceptionHandler;
import com.adhar.kit.commons.exception.BusinessException;
import com.adhar.kit.commons.exception.IntegrationException;
import com.adhar.kit.commons.exception.ResourceNotFoundException;
import com.adhar.kit.commons.exception.ServiceException;
import com.adhar.kit.commons.exception.ValidationException;
import com.adhar.kit.commons.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring MVC global exception handler translating the Adhar exception hierarchy into
 * HTTP responses with a standard {@link ErrorResponse} body.
 *
 * <p>Status mapping (driven by {@link AdharException#getHttpStatus()}):</p>
 * <ul>
 *   <li>{@link ValidationException} / {@link MethodArgumentNotValidException} /
 *       {@link ConstraintViolationException} - 400 with field-level errors</li>
 *   <li>{@link ResourceNotFoundException} - 404</li>
 *   <li>{@link BusinessException} - 422</li>
 *   <li>{@link ServiceException} - 500</li>
 *   <li>{@link IntegrationException} - 502</li>
 *   <li>other {@link AdharException}s - their own {@code getHttpStatus()}</li>
 *   <li>any other {@link Exception} - 500 (message not leaked to clients)</li>
 * </ul>
 *
 * <p>Registered automatically by the module auto-configuration when Spring Web and the
 * Servlet API are on the classpath; override by defining your own
 * {@code SpringGlobalExceptionHandler} bean or extending this class.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@RestControllerAdvice
public class SpringGlobalExceptionHandler extends AdharExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SpringGlobalExceptionHandler.class);

    /**
     * Handles validation failures raised by application code.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> onValidationException(ValidationException ex, HttpServletRequest request) {
        return respond(ex.getHttpStatus(), enrich(handleValidationException(ex), request));
    }

    /**
     * Handles missing resources.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> onResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(ex.getHttpStatus(), enrich(handleResourceNotFoundException(ex), request));
    }

    /**
     * Handles business rule violations.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> onBusinessException(BusinessException ex, HttpServletRequest request) {
        return respond(ex.getHttpStatus(), enrich(handleAdharException(ex), request));
    }

    /**
     * Handles internal service failures.
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorResponse> onServiceException(ServiceException ex, HttpServletRequest request) {
        log.error("Service exception: {}", ex.getMessage(), ex);
        return respond(ex.getHttpStatus(), enrich(handleServiceException(ex), request));
    }

    /**
     * Handles external system integration failures.
     */
    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<ErrorResponse> onIntegrationException(IntegrationException ex, HttpServletRequest request) {
        log.error("Integration exception: {}", ex.getMessage(), ex);
        return respond(ex.getHttpStatus(), enrich(handleAdharException(ex), request));
    }

    /**
     * Fallback for any other {@link AdharException} subclass; the status comes from
     * {@link AdharException#getHttpStatus()}.
     */
    @ExceptionHandler(AdharException.class)
    public ResponseEntity<ErrorResponse> onAdharException(AdharException ex, HttpServletRequest request) {
        return respond(ex.getHttpStatus(), enrich(handleAdharException(ex), request));
    }

    /**
     * Handles {@code @Valid} body binding failures with field-level errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> onMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ErrorResponse body = ErrorResponse.ofFieldErrors(
            CommonConstants.ERROR_VALIDATION, "Validation failed", fieldErrors);
        return respond(400, enrich(body, request));
    }

    /**
     * Handles {@code jakarta.validation} constraint violations (e.g. validated
     * {@code @RequestParam}s) with field-level errors.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> onConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
            fieldErrors.putIfAbsent(String.valueOf(violation.getPropertyPath()), violation.getMessage()));
        ErrorResponse body = ErrorResponse.ofFieldErrors(
            CommonConstants.ERROR_VALIDATION, "Validation failed", fieldErrors);
        return respond(400, enrich(body, request));
    }

    /**
     * Last-resort handler for unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> onGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return respond(500, enrich(handleGenericException(ex), request));
    }

    /**
     * Adds request path and request id (from {@link CorrelationContext} when available)
     * to the error body.
     */
    protected ErrorResponse enrich(ErrorResponse body, HttpServletRequest request) {
        if (request != null) {
            body.setPath(request.getRequestURI());
        }
        String requestId = CorrelationContext.getRequestId();
        if (requestId == null && request != null) {
            requestId = request.getHeader(CommonConstants.HEADER_REQUEST_ID);
        }
        body.setRequestId(requestId);
        return body;
    }

    private ResponseEntity<ErrorResponse> respond(int status, ErrorResponse body) {
        return ResponseEntity.status(status).body(body);
    }
}
