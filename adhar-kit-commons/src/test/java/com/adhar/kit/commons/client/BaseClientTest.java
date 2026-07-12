package com.adhar.kit.commons.client;

import com.adhar.kit.commons.constant.CommonConstants;
import com.adhar.kit.commons.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BaseClient}. Uses a stubbed {@link ExchangeFunction} so no
 * real network calls are made.
 */
class BaseClientTest {

    /** Concrete subclass exposing the protected methods for testing. */
    static class TestClient extends BaseClient {
        TestClient(WebClient webClient) {
            super(webClient);
        }

        HttpHeaders defaults() { return createDefaultHeaders(); }
        HttpHeaders withRequestId(String id) { return createHeadersWithRequestId(id); }
        HttpHeaders withCorrelationId(String id) { return createHeadersWithCorrelationId(id); }
        HttpHeaders withUserContext(String u, String t) { return createHeadersWithUserContext(u, t); }
        boolean success(HttpStatus s) { return isSuccessStatus(s); }

        <T> T doGet(String uri, Class<T> type) { return get(uri, type); }
        <T> T doGet(String uri, HttpHeaders h, Class<T> type) { return get(uri, h, type); }
        <T> T doPost(String uri, Object body, Class<T> type) { return post(uri, body, type); }
        <T> T doPost(String uri, Object body, HttpHeaders h, Class<T> type) { return post(uri, body, h, type); }
        <T> T doPut(String uri, Object body, Class<T> type) { return put(uri, body, type); }
        void doDelete(String uri) { delete(uri); }
        <T> Mono<T> doGetAsync(String uri, Class<T> type) { return getAsync(uri, type); }
        <T> Mono<T> doPostAsync(String uri, Object body, Class<T> type) { return postAsync(uri, body, type); }
    }

    private TestClient clientReturning(String body, HttpStatus status) {
        ExchangeFunction exchange = request -> Mono.just(
                ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .body(body)
                        .build());
        WebClient webClient = WebClient.builder().exchangeFunction(exchange).build();
        return new TestClient(webClient);
    }

    private TestClient clientFailing(Throwable error) {
        ExchangeFunction exchange = request -> Mono.error(error);
        WebClient webClient = WebClient.builder().exchangeFunction(exchange).build();
        return new TestClient(webClient);
    }

    @Test
    void createDefaultHeadersSetsJsonAndRequestId() {
        HttpHeaders headers = clientReturning("ok", HttpStatus.OK).defaults();
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
        assertNotNull(headers.getFirst(CommonConstants.HEADER_REQUEST_ID));
    }

    @Test
    void createHeadersWithRequestIdOverridesRequestId() {
        HttpHeaders headers = clientReturning("ok", HttpStatus.OK).withRequestId("req-123");
        assertEquals("req-123", headers.getFirst(CommonConstants.HEADER_REQUEST_ID));
    }

    @Test
    void createHeadersWithCorrelationIdSetsCorrelation() {
        HttpHeaders headers = clientReturning("ok", HttpStatus.OK).withCorrelationId("corr-1");
        assertEquals("corr-1", headers.getFirst(CommonConstants.HEADER_CORRELATION_ID));
    }

    @Test
    void createHeadersWithUserContextSetsBothValues() {
        HttpHeaders headers = clientReturning("ok", HttpStatus.OK).withUserContext("u1", "t1");
        assertEquals("u1", headers.getFirst(CommonConstants.HEADER_USER_ID));
        assertEquals("t1", headers.getFirst(CommonConstants.HEADER_TENANT_ID));
    }

    @Test
    void createHeadersWithUserContextHandlesNulls() {
        HttpHeaders headers = clientReturning("ok", HttpStatus.OK).withUserContext(null, null);
        assertNull(headers.getFirst(CommonConstants.HEADER_USER_ID));
        assertNull(headers.getFirst(CommonConstants.HEADER_TENANT_ID));
    }

    @Test
    void isSuccessStatusReflectsStatusClass() {
        TestClient c = clientReturning("ok", HttpStatus.OK);
        assertTrue(c.success(HttpStatus.OK));
        assertTrue(c.success(HttpStatus.CREATED));
        assertFalse(c.success(HttpStatus.NOT_FOUND));
        assertFalse(c.success(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void getReturnsBody() {
        assertEquals("hello", clientReturning("hello", HttpStatus.OK).doGet("/x", String.class));
    }

    @Test
    void getWithHeadersReturnsBody() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Custom", "v");
        assertEquals("hello", clientReturning("hello", HttpStatus.OK).doGet("/x", h, String.class));
    }

    @Test
    void postReturnsBody() {
        assertEquals("created", clientReturning("created", HttpStatus.OK).doPost("/x", "payload", String.class));
    }

    @Test
    void postWithHeadersReturnsBody() {
        HttpHeaders h = new HttpHeaders();
        assertEquals("created", clientReturning("created", HttpStatus.OK).doPost("/x", "payload", h, String.class));
    }

    @Test
    void putReturnsBody() {
        assertEquals("updated", clientReturning("updated", HttpStatus.OK).doPut("/x", "payload", String.class));
    }

    @Test
    void deleteCompletesWithoutError() {
        assertDoesNotThrow(() -> clientReturning("", HttpStatus.OK).doDelete("/x"));
    }

    @Test
    void getAsyncEmitsBody() {
        assertEquals("async", clientReturning("async", HttpStatus.OK).doGetAsync("/x", String.class).block());
    }

    @Test
    void postAsyncEmitsBody() {
        assertEquals("async", clientReturning("async", HttpStatus.OK).doPostAsync("/x", "b", String.class).block());
    }

    @Test
    void httpErrorStatusWrappedInServiceException() {
        TestClient c = clientReturning("server boom", HttpStatus.INTERNAL_SERVER_ERROR);
        ServiceException ex = assertThrows(ServiceException.class, () -> c.doGet("/x", String.class));
        assertTrue(ex.getMessage().contains("HTTP"));
    }

    @Test
    void genericErrorWrappedInServiceException() {
        TestClient c = clientFailing(new IllegalStateException("connection refused"));
        ServiceException ex = assertThrows(ServiceException.class, () -> c.doPost("/x", "b", String.class));
        assertTrue(ex.getMessage().contains("Request failed"));
    }
}
