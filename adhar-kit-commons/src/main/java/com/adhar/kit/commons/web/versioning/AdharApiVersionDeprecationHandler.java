package com.adhar.kit.commons.web.versioning;

import com.adhar.kit.commons.annotation.ApiVersion;
import com.adhar.kit.commons.constant.CommonConstants;
import com.adhar.kit.commons.web.ApiVersionInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.accept.ApiVersionDeprecationHandler;
import org.springframework.web.accept.ApiVersionParser;
import org.springframework.web.accept.SemanticApiVersionParser;
import org.springframework.web.accept.StandardApiVersionDeprecationHandler;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Announces a deprecated API version to its clients the way Spring's API versioning
 * does: the RFC 9745 {@code Deprecation} header ({@code @<epoch-seconds>}), the RFC 8594
 * {@code Sunset} header and documentation {@code Link}s, written by a
 * {@link StandardApiVersionDeprecationHandler} fed from
 * {@code adhar.commons.api-versioning.versions.v<major>.*} - plus a
 * {@code Link: <successor>; rel="successor-version"}, which the standard handler has no
 * option for.
 *
 * <p>A handler annotated {@link ApiVersion @ApiVersion(deprecated = true)} gets the same
 * headers for that endpoint alone, from a standard handler fed by the annotation; when
 * its version is deprecated as a whole, the version's dates win.</p>
 *
 * <p>Spring calls this for requests with a resolved version only - with the kit's
 * configuration, requests under {@code /api/v<major>}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@SuppressWarnings("deprecation")
public class AdharApiVersionDeprecationHandler implements ApiVersionDeprecationHandler {

    private static final Logger log = LoggerFactory.getLogger(AdharApiVersionDeprecationHandler.class);

    private final ApiVersionRegistry registry;
    private final ApiVersionParser<?> parser;
    private final StandardApiVersionDeprecationHandler versions;
    private final Map<EndpointKey, StandardApiVersionDeprecationHandler> endpoints = new ConcurrentHashMap<>();

    /**
     * Creates a handler for the versions in {@code registry}.
     *
     * @param registry the versions and what is announced about them
     * @param parser   the parser Spring parses request versions with
     */
    public AdharApiVersionDeprecationHandler(ApiVersionRegistry registry, ApiVersionParser<?> parser) {
        this.registry = registry;
        this.parser = parser != null ? parser : new SemanticApiVersionParser();
        this.versions = new StandardApiVersionDeprecationHandler(this.parser);
        for (ApiMajorVersion version : registry.versions()) {
            if (!version.deprecationAnnounced()) {
                continue;
            }
            // Configured as the bare major: the request version comes from the path ("v1"),
            // so both go through the same parser and compare equal.
            StandardApiVersionDeprecationHandler.VersionSpec spec =
                    versions.configureVersion(String.valueOf(version.major()));
            spec.setDeprecationDate(utc(version.deprecation()));
            if (version.sunset() != null) {
                spec.setSunsetDate(utc(version.sunset()));
            }
            if (version.deprecationLink() != null) {
                spec.setDeprecationLink(version.deprecationLink());
            }
            if (version.sunsetLink() != null) {
                spec.setSunsetLink(version.sunsetLink());
            }
        }
    }

    @Override
    public void handleVersion(Comparable<?> requestVersion, Object handler,
                              HttpServletRequest request, HttpServletResponse response) {
        OptionalInt major = majorOf(requestVersion);
        if (handler instanceof HandlerMethod handlerMethod && major.isPresent()) {
            handleEndpoint(handlerMethod, major.getAsInt(), requestVersion, request, response);
        }
        versions.handleVersion(requestVersion, handler, request, response);
        if (major.isPresent()) {
            registry.get(major.getAsInt())
                    .filter(ApiMajorVersion::deprecationAnnounced)
                    .map(ApiMajorVersion::successorLinkHeader)
                    .ifPresent(link -> response.addHeader(HttpHeaders.LINK, link));
        }
    }

    private void handleEndpoint(HandlerMethod handlerMethod, int major, Comparable<?> requestVersion,
                                HttpServletRequest request, HttpServletResponse response) {
        ApiVersion annotation = annotationOf(handlerMethod);
        if (annotation == null) {
            return;
        }
        response.setHeader(CommonConstants.HEADER_API_VERSION, annotation.version());
        if (!annotation.deprecated()) {
            return;
        }
        endpoints.computeIfAbsent(new EndpointKey(handlerMethod.getMethod(), major),
                        key -> endpointHandler(annotation, major))
                .handleVersion(requestVersion, handlerMethod, request, response);
        if (!annotation.deprecationMessage().isEmpty()) {
            response.setHeader(ApiVersionInterceptor.DEPRECATION_MESSAGE_HEADER, annotation.deprecationMessage());
        }
    }

    private StandardApiVersionDeprecationHandler endpointHandler(ApiVersion annotation, int major) {
        StandardApiVersionDeprecationHandler handler = new StandardApiVersionDeprecationHandler(parser);
        StandardApiVersionDeprecationHandler.VersionSpec spec = handler.configureVersion(String.valueOf(major));
        Instant deprecation = lenientDate(annotation.deprecationDate(), "deprecationDate");
        spec.setDeprecationDate(utc(deprecation != null ? deprecation : Instant.EPOCH));
        Instant sunset = lenientDate(annotation.sunsetDate(), "sunsetDate");
        if (sunset != null) {
            spec.setSunsetDate(utc(sunset));
        }
        return handler;
    }

    private static ApiVersion annotationOf(HandlerMethod handlerMethod) {
        ApiVersion annotation = handlerMethod.getMethodAnnotation(ApiVersion.class);
        return annotation != null ? annotation
                : AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ApiVersion.class);
    }

    private static Instant lenientDate(String value, String attribute) {
        try {
            return ApiVersionDates.instant(value, "@ApiVersion." + attribute);
        } catch (IllegalArgumentException e) {
            log.debug("Ignoring {}", e.getMessage());
            return null;
        }
    }

    /**
     * The major of a parsed request version; for a custom parser, the leading digits of
     * its string form.
     */
    private static OptionalInt majorOf(Comparable<?> version) {
        if (version instanceof SemanticApiVersionParser.Version semantic) {
            return OptionalInt.of(semantic.getMajor());
        }
        String text = String.valueOf(version);
        int start = 0;
        while (start < text.length() && !Character.isDigit(text.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end)) && end - start < 9) {
            end++;
        }
        return end > start ? OptionalInt.of(Integer.parseInt(text.substring(start, end))) : OptionalInt.empty();
    }

    private static ZonedDateTime utc(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record EndpointKey(Method method, int major) {
    }
}
