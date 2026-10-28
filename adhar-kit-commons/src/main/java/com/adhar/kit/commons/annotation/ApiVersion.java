package com.adhar.kit.commons.annotation;

import java.lang.annotation.*;

/**
 * Declares the version, and the deprecation, of a single endpoint.
 *
 * <p><b>Superseded</b> by path-major versioning: a service serves each major version
 * under {@code /api/v<major>} (generated from {@code api/v<major>/openapi.yaml}), the
 * kit reports the full version in the {@code API-Version} header, and a whole version
 * is deprecated with {@code adhar.commons.api-versioning.versions.v<major>.*}. This
 * annotation never routed requests - two handlers on the same path with different
 * versions fail at startup with an ambiguous mapping - and remains only to mark
 * single endpoints:</p>
 * <ul>
 *   <li>under {@code /api/v<major>}, a {@code deprecated} endpoint answers with the
 *       RFC 9745 {@code Deprecation} header (dated {@link #deprecationDate()}, or the
 *       epoch when no date is given) and the RFC 8594 {@code Sunset} header, through
 *       Spring's {@code StandardApiVersionDeprecationHandler};</li>
 *   <li>elsewhere the legacy {@code ApiVersionInterceptor} behaviour applies:
 *       {@code X-API-Version}, {@code Deprecation: true}, {@code Sunset}.</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @RestController
 * public class OrdersController implements OrdersApi {   // generated from api/v1/openapi.yaml
 *
 *     @ApiVersion(version = "1", deprecated = true, deprecationDate = "2026-09-01",
 *                 sunsetDate = "2027-01-31", deprecationMessage = "Use GET /api/v1/orders?status=")
 *     public ResponseEntity<List<Order>> listOpenOrders() { ... }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @deprecated since 0.2.0: version the API by path ({@code /api/v<major>}) and announce
 * deprecations with {@code adhar.commons.api-versioning.versions.v<major>.deprecated};
 * the annotation keeps working for single endpoints.
 */
@Deprecated(since = "0.2.0")
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    /**
     * API version.
     */
    String version();

    /**
     * Is this version deprecated?
     */
    boolean deprecated() default false;

    /**
     * Deprecation message.
     */
    String deprecationMessage() default "";

    /**
     * When the endpoint was (or will be) deprecated, ISO-8601; sent as the RFC 9745
     * {@code Deprecation} header on {@code /api/v<major>} paths. Empty means the date is
     * not stated.
     *
     * @since 0.2.0
     */
    String deprecationDate() default "";

    /**
     * Sunset date (ISO-8601 format).
     */
    String sunsetDate() default "";
}
