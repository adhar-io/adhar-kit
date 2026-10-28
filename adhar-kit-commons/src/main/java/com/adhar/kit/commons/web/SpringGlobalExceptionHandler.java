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
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.accept.MissingApiVersionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
 *   <li>Spring MVC's own exceptions - the status Spring assigns them: 404 for a path
 *       nothing maps, 405, 406, 415, 400 for an unreadable body, a parameter of the
 *       wrong type or an unsupported API version, and the status of any
 *       {@code ResponseStatusException}</li>
 *   <li>any other {@link Exception} - 500 (message not leaked to clients)</li>
 * </ul>
 *
 * <p>Under {@code /api/v<major>} the body goes out as an RFC 9457 problem
 * ({@code application/problem+json}), converted by {@link ProblemDetailsResponseAdvice};
 * see {@code adhar.commons.exception-handler.problem-details}.</p>
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

    /** Spring's own status mapping for its exceptions, used for the status and detail only. */
    private final ResponseEntityExceptionHandler springWebExceptions = new ResponseEntityExceptionHandler() {
    };

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
     * Handles constraint violations on controller method parameters reported by Spring
     * MVC's built-in method validation, with one field error per parameter. A violation
     * on a return value is the service's own fault and stays a 500.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> onHandlerMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        if (!ex.getStatusCode().is4xxClientError()) {
            return onGenericException(ex, request);
        }
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String parameter = parameterName(result.getMethodParameter());
            result.getResolvableErrors().forEach(error ->
                fieldErrors.putIfAbsent(parameter, error.getDefaultMessage()));
        }
        ErrorResponse body = ErrorResponse.ofFieldErrors(
            CommonConstants.ERROR_VALIDATION, "Validation failed", fieldErrors);
        return respond(400, enrich(body, request));
    }

    /**
     * Handles Spring MVC's own exceptions with the status Spring assigns them, instead of
     * letting the catch-all below turn a client's mistake - a path nothing maps, a wrong
     * method or media type, an unreadable body, an unsupported API version - into a 500.
     * The headers Spring adds ({@code Allow}, {@code Accept}) are kept.
     */
    @ExceptionHandler({
        HttpRequestMethodNotSupportedException.class,
        HttpMediaTypeNotSupportedException.class,
        HttpMediaTypeNotAcceptableException.class,
        MissingPathVariableException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class,
        ServletRequestBindingException.class,
        NoHandlerFoundException.class,
        NoResourceFoundException.class,
        AsyncRequestTimeoutException.class,
        ErrorResponseException.class,
        MaxUploadSizeExceededException.class,
        ConversionNotSupportedException.class,
        TypeMismatchException.class,
        HttpMessageNotReadableException.class,
        HttpMessageNotWritableException.class,
        MethodValidationException.class,
        AsyncRequestNotUsableException.class
    })
    public ResponseEntity<ErrorResponse> onSpringWebException(Exception ex, HttpServletRequest request) {
        ResponseEntity<Object> handled;
        try {
            handled = springWebExceptions.handleException(ex, new ServletWebRequest(request));
        } catch (Exception unhandled) {
            return onGenericException(unhandled, request);
        }
        if (handled == null) {
            // The client went away (AsyncRequestNotUsableException): there is nobody to answer.
            return null;
        }
        HttpStatusCode status = handled.getStatusCode();
        if (status.is5xxServerError()) {
            log.error("Spring MVC exception: {}", ex.getMessage(), ex);
        }
        String detail = handled.getBody() instanceof ProblemDetail problem && problem.getDetail() != null
            ? problem.getDetail() : reasonPhrase(status);
        ErrorResponse body = ErrorResponse.of(errorCode(ex, status), detail);
        return ResponseEntity.status(status).headers(handled.getHeaders()).body(enrich(body, request));
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

    private static String errorCode(Exception ex, HttpStatusCode status) {
        if (ex instanceof InvalidApiVersionException) {
            return "INVALID_API_VERSION";
        }
        if (ex instanceof MissingApiVersionException) {
            return "MISSING_API_VERSION";
        }
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : "HTTP_" + status.value();
    }

    private static String reasonPhrase(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.getReasonPhrase() : "HTTP " + status.value();
    }

    private static String parameterName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }
}
