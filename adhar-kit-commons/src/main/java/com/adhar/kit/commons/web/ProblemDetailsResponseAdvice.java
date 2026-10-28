package com.adhar.kit.commons.web;

import com.adhar.kit.commons.config.AdharCommonsProperties.ProblemDetailsScope;
import com.adhar.kit.commons.model.ErrorResponse;
import com.adhar.kit.commons.web.versioning.ApiPaths;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Writes error responses as RFC 9457 problem details ({@code application/problem+json})
 * where the API contract asks for them: every {@code api/v<major>/openapi.yaml}
 * declares its 4xx and 5xx responses that way, and the openapi-lint goal enforces it.
 *
 * <p>The {@link SpringGlobalExceptionHandler} - and any controller - keeps returning
 * the kit's {@link ErrorResponse}; this advice converts it on the way out, so one
 * handler serves both the versioned API and the paths whose clients expect the
 * {@code ErrorResponse} body. Which responses are converted is
 * {@code adhar.commons.exception-handler.problem-details}: {@code versioned-api}
 * (default, {@code /api/v<major>} only), {@code always} or {@code never}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@ControllerAdvice
public class ProblemDetailsResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ProblemDetailsScope scope;

    /**
     * Creates the advice for the versioned API only, the default scope.
     */
    public ProblemDetailsResponseAdvice() {
        this(ProblemDetailsScope.VERSIONED_API);
    }

    /**
     * Creates the advice.
     *
     * @param scope which error responses become problem details
     */
    public ProblemDetailsResponseAdvice(ProblemDetailsScope scope) {
        this.scope = scope;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return scope != ProblemDetailsScope.NEVER;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof ErrorResponse error)
                || !(request instanceof ServletServerHttpRequest servletRequest)
                || !(response instanceof ServletServerHttpResponse servletResponse)
                || !isJson(selectedContentType)) {
            return body;
        }
        int status = servletResponse.getServletResponse().getStatus();
        if (status < 400) {
            return body;
        }
        if (scope == ProblemDetailsScope.VERSIONED_API && ApiPaths.majorOf(
                ApiPaths.pathWithinApplication(servletRequest.getServletRequest())).isEmpty()) {
            return body;
        }
        // Set before the converter writes: it only fills in a content type that is missing.
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return ProblemDetails.toMap(ProblemDetails.of(status, error));
    }

    private static boolean isJson(MediaType contentType) {
        return contentType != null && (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                || contentType.getSubtype().endsWith("+json"));
    }
}
