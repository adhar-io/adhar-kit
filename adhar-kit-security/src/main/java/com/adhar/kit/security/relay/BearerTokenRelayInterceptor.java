package com.adhar.kit.security.relay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * A {@link ClientHttpRequestInterceptor} that relays the bearer token from the
 * current inbound HTTP request onto outbound calls made through a {@code RestClient}
 * or {@code RestTemplate}, so downstream services receive the caller's identity.
 *
 * <p>Opt-in via {@code adhar.security.token-relay.enabled=true}. The token is read
 * from the inbound {@code Authorization} header (configurable) and copied to the
 * outbound request only when:</p>
 * <ul>
 *   <li>there is a current servlet request (i.e. we are on a request thread),</li>
 *   <li>it carries a {@code Bearer} token, and</li>
 *   <li>the outbound request does not already set an {@code Authorization} header.</li>
 * </ul>
 *
 * <p>Usage — register with a client builder:</p>
 * <pre>{@code
 * RestClient client = RestClient.builder()
 *     .requestInterceptor(bearerTokenRelayInterceptor)
 *     .build();
 * }</pre>
 *
 * <p>Only a servlet ({@code RestClient}/{@code RestTemplate}) variant is provided; no
 * reactive {@code WebClient} filter is included because spring-webflux is not a
 * dependency of this module.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
public class BearerTokenRelayInterceptor implements ClientHttpRequestInterceptor {

    private final String headerName;

    /**
     * Creates an interceptor relaying the standard {@code Authorization} header.
     */
    public BearerTokenRelayInterceptor() {
        this(HttpHeaders.AUTHORIZATION);
    }

    /**
     * Creates an interceptor relaying a specific header.
     *
     * @param headerName the inbound header carrying the bearer token
     */
    public BearerTokenRelayInterceptor(String headerName) {
        this.headerName = (headerName == null || headerName.isBlank())
            ? HttpHeaders.AUTHORIZATION : headerName;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            String token = currentBearerToken();
            if (token != null) {
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, token);
                log.debug("Relayed bearer token to downstream request: {}", request.getURI());
            }
        }
        return execution.execute(request, body);
    }

    /**
     * Reads the bearer token from the current inbound request, if any.
     *
     * @return the {@code Bearer ...} header value, or {@code null} when unavailable
     */
    private String currentBearerToken() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest inbound = attributes.getRequest();
        String value = inbound.getHeader(headerName);
        if (value != null && value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value;
        }
        return null;
    }
}
