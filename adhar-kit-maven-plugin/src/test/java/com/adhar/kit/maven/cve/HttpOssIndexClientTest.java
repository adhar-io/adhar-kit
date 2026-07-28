package com.adhar.kit.maven.cve;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HttpOssIndexClient} using a mocked {@link HttpClient} - no
 * real network call is made. Exercises the request/response path and the
 * on-disk caching that avoids re-querying.
 */
class HttpOssIndexClientTest {

    private final Log log = mock(Log.class);
    private final List<String> purls = List.of("pkg:maven/org.slf4j/slf4j-api@2.0.9");

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void fetchesThenServesFromCacheOnSecondCall(@TempDir Path cacheDir) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> ok = response(200, "[{\"coordinates\":\"pkg:maven/org.slf4j/slf4j-api@2.0.9\","
                + "\"vulnerabilities\":[]}]");
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenReturn(ok);
        HttpOssIndexClient client = new HttpOssIndexClient(cacheDir.toFile(), log, httpClient);

        String first = client.componentReport(purls);
        String second = client.componentReport(purls);

        assertThat(first).contains("slf4j-api");
        assertThat(second).isEqualTo(first);
        // Second call must hit the cache, not the network.
        verify(httpClient, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    void nonSuccessStatusRaisesIOException(@TempDir Path cacheDir) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> serverError = response(503, "");
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenReturn(serverError);
        HttpOssIndexClient client = new HttpOssIndexClient(cacheDir.toFile(), log, httpClient);

        assertThatThrownBy(() -> client.componentReport(purls))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("503");
    }

    @Test
    void interruptedSendIsWrappedAsIOException(@TempDir Path cacheDir) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenThrow(new InterruptedException("boom"));
        HttpOssIndexClient client = new HttpOssIndexClient(cacheDir.toFile(), log, httpClient);

        assertThatThrownBy(() -> client.componentReport(purls))
                .isInstanceOf(IOException.class);
    }
}
