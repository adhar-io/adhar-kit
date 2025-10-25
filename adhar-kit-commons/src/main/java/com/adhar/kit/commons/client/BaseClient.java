package com.adhar.kit.commons.client;

import com.adhar.kit.commons.constant.CommonConstants;
import com.adhar.kit.commons.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Base client class providing common functionality for HTTP clients.
 * Includes standardized request handling, error handling, and retry logic.
 */
@Slf4j
public abstract class BaseClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_RETRIES = 3;

    protected final WebClient webClient;

    protected BaseClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Creates default HTTP headers for requests.
     *
     * @return HttpHeaders with common headers
     */
    protected HttpHeaders createDefaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(CommonConstants.HEADER_REQUEST_ID, UUID.randomUUID().toString());
        return headers;
    }

    /**
     * Creates HTTP headers with request ID.
     *
     * @param requestId the request ID
     * @return HttpHeaders with request ID
     */
    protected HttpHeaders createHeadersWithRequestId(String requestId) {
        HttpHeaders headers = createDefaultHeaders();
        headers.set(CommonConstants.HEADER_REQUEST_ID, requestId);
        return headers;
    }

    /**
     * Creates HTTP headers with correlation ID.
     *
     * @param correlationId the correlation ID
     * @return HttpHeaders with correlation ID
     */
    protected HttpHeaders createHeadersWithCorrelationId(String correlationId) {
        HttpHeaders headers = createDefaultHeaders();
        headers.set(CommonConstants.HEADER_CORRELATION_ID, correlationId);
        return headers;
    }

    /**
     * Creates HTTP headers with user context.
     *
     * @param userId the user ID
     * @param tenantId the tenant ID
     * @return HttpHeaders with user context
     */
    protected HttpHeaders createHeadersWithUserContext(String userId, String tenantId) {
        HttpHeaders headers = createDefaultHeaders();
        if (userId != null) {
            headers.set(CommonConstants.HEADER_USER_ID, userId);
        }
        if (tenantId != null) {
            headers.set(CommonConstants.HEADER_TENANT_ID, tenantId);
        }
        return headers;
    }

    /**
     * Performs a GET request with error handling.
     *
     * @param uri the request URI
     * @param responseType the response type class
     * @param <T> the response type
     * @return the response body
     * @throws ServiceException if request fails
     */
    protected <T> T get(String uri, Class<T> responseType) {
        return performRequest(() ->
            webClient.get()
                    .uri(uri)
                    .headers(headers -> headers.addAll(createDefaultHeaders()))
                    .retrieve()
                    .bodyToMono(responseType)
                    .timeout(DEFAULT_TIMEOUT)
                    .block(),
            "GET", uri);
    }

    /**
     * Performs a GET request with custom headers.
     *
     * @param uri the request URI
     * @param headers the request headers
     * @param responseType the response type class
     * @param <T> the response type
     * @return the response body
     * @throws ServiceException if request fails
     */
    protected <T> T get(String uri, HttpHeaders headers, Class<T> responseType) {
        return performRequest(() ->
            webClient.get()
                    .uri(uri)
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .bodyToMono(responseType)
                    .timeout(DEFAULT_TIMEOUT)
                    .block(),
            "GET", uri);
    }

    /**
     * Performs a POST request with error handling.
     *
     * @param uri the request URI
     * @param body the request body
     * @param responseType the response type class
     * @param <T> the response type
     * @return the response body
     * @throws ServiceException if request fails
     */
    protected <T> T post(String uri, Object body, Class<T> responseType) {
        return performRequest(() ->
            webClient.post()
                    .uri(uri)
                    .headers(headers -> headers.addAll(createDefaultHeaders()))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .timeout(DEFAULT_TIMEOUT)
                    .block(),
            "POST", uri);
    }

    /**
     * Performs a POST request with custom headers.
     *
     * @param uri the request URI
     * @param body the request body
     * @param headers the request headers
     * @param responseType the response type class
     * @param <T> the response type
     * @return the response body
     * @throws ServiceException if request fails
     */
    protected <T> T post(String uri, Object body, HttpHeaders headers, Class<T> responseType) {
        return performRequest(() ->
            webClient.post()
                    .uri(uri)
                    .headers(h -> h.addAll(headers))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .timeout(DEFAULT_TIMEOUT)
                    .block(),
            "POST", uri);
    }

    /**
     * Performs a PUT request with error handling.
     *
     * @param uri the request URI
     * @param body the request body
     * @param responseType the response type class
     * @param <T> the response type
     * @return the response body
     * @throws ServiceException if request fails
     */
    protected <T> T put(String uri, Object body, Class<T> responseType) {
        return performRequest(() ->
            webClient.put()
                    .uri(uri)
                    .headers(headers -> headers.addAll(createDefaultHeaders()))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .timeout(DEFAULT_TIMEOUT)
                    .block(),
            "PUT", uri);
    }

    /**
     * Performs a DELETE request with error handling.
     *
     * @param uri the request URI
     * @throws ServiceException if request fails
     */
    protected void delete(String uri) {
        performRequest(() -> {
            webClient.delete()
                    .uri(uri)
                    .headers(headers -> headers.addAll(createDefaultHeaders()))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(DEFAULT_TIMEOUT)
                    .block();
            return null;
        }, "DELETE", uri);
    }

    /**
     * Performs a request with standardized error handling and logging.
     *
     * @param requestSupplier the request supplier
     * @param method the HTTP method
     * @param uri the request URI
     * @param <T> the response type
     * @return the response
     * @throws ServiceException if request fails
     */
    private <T> T performRequest(RequestSupplier<T> requestSupplier, String method, String uri) {
        try {
            log.debug("Performing {} request to: {}", method, uri);
            T result = requestSupplier.get();
            log.debug("Successfully completed {} request to: {}", method, uri);
            return result;
        } catch (WebClientResponseException e) {
            log.error("HTTP error during {} request to {}: {} - {}",
                    method, uri, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new ServiceException(
                String.format("HTTP %s error: %s", e.getStatusCode(), e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            log.error("Error during {} request to {}: {}", method, uri, e.getMessage(), e);
            throw new ServiceException(String.format("Request failed: %s %s", method, uri), e);
        }
    }

    /**
     * Performs an async GET request.
     *
     * @param uri the request URI
     * @param responseType the response type class
     * @param <T> the response type
     * @return Mono with the response
     */
    protected <T> Mono<T> getAsync(String uri, Class<T> responseType) {
        return webClient.get()
                .uri(uri)
                .headers(headers -> headers.addAll(createDefaultHeaders()))
                .retrieve()
                .bodyToMono(responseType)
                .timeout(DEFAULT_TIMEOUT)
                .doOnSuccess(result -> log.debug("Async GET request completed: {}", uri))
                .doOnError(error -> log.error("Async GET request failed: {} - {}", uri, error.getMessage()));
    }

    /**
     * Performs an async POST request.
     *
     * @param uri the request URI
     * @param body the request body
     * @param responseType the response type class
     * @param <T> the response type
     * @return Mono with the response
     */
    protected <T> Mono<T> postAsync(String uri, Object body, Class<T> responseType) {
        return webClient.post()
                .uri(uri)
                .headers(headers -> headers.addAll(createDefaultHeaders()))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(responseType)
                .timeout(DEFAULT_TIMEOUT)
                .doOnSuccess(result -> log.debug("Async POST request completed: {}", uri))
                .doOnError(error -> log.error("Async POST request failed: {} - {}", uri, error.getMessage()));
    }

    /**
     * Checks if an HTTP status indicates success.
     *
     * @param status the HTTP status
     * @return true if status is successful
     */
    protected boolean isSuccessStatus(HttpStatus status) {
        return status.is2xxSuccessful();
    }

    /**
     * Functional interface for request suppliers.
     *
     * @param <T> the response type
     */
    @FunctionalInterface
    private interface RequestSupplier<T> {
        T get() throws Exception;
    }
}
