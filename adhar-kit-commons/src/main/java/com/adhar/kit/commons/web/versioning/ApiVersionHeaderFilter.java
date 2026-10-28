package com.adhar.kit.commons.web.versioning;

import com.adhar.kit.commons.web.CorrelationIdFilter;
import com.adhar.kit.commons.web.ProblemDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Applies the versioning contract to every request under {@code /api/v<major>}:
 * the response reports the full version of the API that answered in
 * {@code API-Version}, and a request whose {@code API-Version} header names another
 * major is refused with a 400 problem - the client was written against an API this
 * path does not serve, and answering anyway would hand it a contract it does not know.
 *
 * <p>A filter rather than an interceptor, so error responses (404, 405, a rejected
 * request) carry the version too. Paths outside the versioned API, and majors the
 * service does not serve, pass through untouched. The deprecation headers are written
 * by {@link AdharApiVersionDeprecationHandler} through Spring's API versioning.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class ApiVersionHeaderFilter extends OncePerRequestFilter implements Ordered {

    /** After the correlation and tenant filters, so a rejection carries the request id. */
    public static final int ORDER = CorrelationIdFilter.ORDER + 20;

    private final ApiVersionRegistry registry;
    private final boolean rejectMismatchedMajor;

    /**
     * Creates the filter.
     *
     * @param registry              the versions the service serves
     * @param rejectMismatchedMajor whether a contradicting {@code API-Version} request
     *                              header is refused with 400
     */
    public ApiVersionHeaderFilter(ApiVersionRegistry registry, boolean rejectMismatchedMajor) {
        this.registry = registry;
        this.rejectMismatchedMajor = rejectMismatchedMajor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<ApiMajorVersion> served = registry.forPath(ApiPaths.pathWithinApplication(request));
        if (served.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        ApiMajorVersion api = served.get();
        response.setHeader(ApiPaths.API_VERSION_HEADER, api.version());

        String requested = request.getHeader(ApiPaths.API_VERSION_HEADER);
        if (rejectMismatchedMajor && requested != null && !requested.isBlank()) {
            OptionalInt asked = ApiPaths.majorOfRequestedVersion(requested);
            if (asked.isEmpty()) {
                reject(request, response, api, requested, ApiPaths.API_VERSION_HEADER + " '" + requested.trim()
                        + "' is not a version; send the version of " + api.prefix()
                        + " the client was written against, e.g. " + api.version());
                return;
            }
            if (asked.getAsInt() != api.major()) {
                reject(request, response, api, requested, ApiPaths.API_VERSION_HEADER + " '" + requested.trim()
                        + "' asks for major version " + asked.getAsInt() + ", but " + api.prefix()
                        + " serves version " + api.version() + "; call /api/v" + asked.getAsInt() + " instead");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, ApiMajorVersion api,
                        String requested, String detail) throws IOException {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, detail, request);
        problem.setProperty("requested", requested.trim());
        problem.setProperty("supported", api.version());
        ProblemDetails.write(response, problem);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
