package com.adhar.kit.commons.web;

import com.adhar.kit.commons.annotation.ApiVersion;
import com.adhar.kit.commons.constant.CommonConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.accept.ApiVersionHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Spring MVC interceptor providing the runtime behaviour for {@link ApiVersion}.
 *
 * <p>For handler methods (or controller classes) annotated with {@code @ApiVersion} it:</p>
 * <ul>
 *   <li>echoes the handler's version in the {@code X-API-Version} response header,</li>
 *   <li>emits {@code Deprecation: true} (plus {@code X-API-Deprecation-Message}) when the
 *       version is marked deprecated,</li>
 *   <li>emits a {@code Sunset} header (RFC 8594, converted to an HTTP date when the
 *       annotation's ISO-8601 {@code sunsetDate} is parseable) when a sunset date is set,</li>
 *   <li>optionally rejects requests whose {@code X-API-Version} header does not match the
 *       handler's version with {@code 400} (disabled by default; enable via
 *       {@code adhar.commons.api-versioning.validate-request-version}).</li>
 * </ul>
 *
 * <p>It acts only outside the versioned API. On {@code /api/v<major>} paths Spring's
 * API versioning has resolved a version, and the contract headers ({@code API-Version},
 * RFC 9745 {@code Deprecation}, {@code Sunset}, the successor {@code Link}) are written
 * by {@code ApiVersionHeaderFilter} and {@code AdharApiVersionDeprecationHandler}, which
 * also honour {@code @ApiVersion} there.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @deprecated since 0.2.0: an interceptor cannot route between versions; version the API
 * by path ({@code /api/v<major>}), which the kit configures through Spring's built-in API
 * versioning. Kept, and still registered, for {@code @ApiVersion} on legacy paths.
 */
@Deprecated(since = "0.2.0")
@SuppressWarnings("deprecation")
public class ApiVersionInterceptor implements HandlerInterceptor {

    /** Draft-standard deprecation response header. */
    public static final String DEPRECATION_HEADER = "Deprecation";

    /** RFC 8594 sunset response header. */
    public static final String SUNSET_HEADER = "Sunset";

    /** Custom header carrying the deprecation message. */
    public static final String DEPRECATION_MESSAGE_HEADER = "X-API-Deprecation-Message";

    private final boolean validateRequestedVersion;

    /**
     * Creates an interceptor that never validates the requested version.
     */
    public ApiVersionInterceptor() {
        this(false);
    }

    /**
     * Constructor.
     *
     * @param validateRequestedVersion whether a mismatching {@code X-API-Version} request
     *                                 header should be rejected with {@code 400}
     */
    public ApiVersionInterceptor(boolean validateRequestedVersion) {
        this.validateRequestedVersion = validateRequestedVersion;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod) || hasResolvedApiVersion(request)) {
            return true;
        }
        ApiVersion apiVersion = resolveAnnotation(handlerMethod);
        if (apiVersion == null) {
            return true;
        }

        if (validateRequestedVersion) {
            String requested = request.getHeader(CommonConstants.HEADER_API_VERSION);
            if (requested != null && !requested.isBlank() && !requested.equals(apiVersion.version())) {
                response.sendError(400, "Unsupported API version '" + requested
                    + "'; this endpoint serves version '" + apiVersion.version() + "'");
                return false;
            }
        }

        response.setHeader(CommonConstants.HEADER_API_VERSION, apiVersion.version());
        if (apiVersion.deprecated()) {
            response.setHeader(DEPRECATION_HEADER, "true");
            if (!apiVersion.deprecationMessage().isEmpty()) {
                response.setHeader(DEPRECATION_MESSAGE_HEADER, apiVersion.deprecationMessage());
            }
        }
        if (!apiVersion.sunsetDate().isEmpty()) {
            response.setHeader(SUNSET_HEADER, toHttpDate(apiVersion.sunsetDate()));
        }
        return true;
    }

    /**
     * Finds {@link ApiVersion} on the handler method, falling back to the controller class.
     */
    private ApiVersion resolveAnnotation(HandlerMethod handlerMethod) {
        ApiVersion annotation = handlerMethod.getMethodAnnotation(ApiVersion.class);
        return annotation != null ? annotation : handlerMethod.getBeanType().getAnnotation(ApiVersion.class);
    }

    /**
     * Whether Spring's API versioning resolved a version for this request, which with
     * the kit's configuration means the request is under {@code /api/v<major>}.
     */
    private static boolean hasResolvedApiVersion(HttpServletRequest request) {
        return request.getAttribute(HandlerMapping.API_VERSION_ATTRIBUTE) instanceof ApiVersionHolder holder
            && holder.hasVersion();
    }

    /**
     * Converts an ISO-8601 date to the RFC 1123 HTTP date required by RFC 8594,
     * passing the raw value through when it cannot be parsed.
     */
    private static String toHttpDate(String isoDate) {
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME
                .format(LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC));
        } catch (DateTimeParseException ex) {
            return isoDate;
        }
    }
}
