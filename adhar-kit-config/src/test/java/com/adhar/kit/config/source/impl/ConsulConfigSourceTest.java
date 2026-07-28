package com.adhar.kit.config.source.impl;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ConsulConfigSourceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private String startServer(int status, String body, AtomicReference<String> tokenCapture) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/kv/", exchange -> {
            if (tokenCapture != null) {
                tokenCapture.set(exchange.getRequestHeaders().getFirst("X-Consul-Token"));
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void loadsRecursiveKvAndDecodesBase64() throws IOException {
        String body = "[" +
                "{\"Key\":\"config/myapp/database/url\",\"Value\":\"" + b64("jdbc:pg") + "\"}," +
                "{\"Key\":\"config/myapp/feature.enabled\",\"Value\":\"" + b64("true") + "\"}," +
                "{\"Key\":\"config/myapp/\",\"Value\":null}]";
        AtomicReference<String> token = new AtomicReference<>();
        String url = startServer(200, body, token);

        ConsulConfigSource source = new ConsulConfigSource(url, "config/myapp", "acl-tok", 140, true);

        assertThat(source.getType()).isEqualTo("consul");
        assertThat(source.getPriority()).isEqualTo(140);
        assertThat(source.getProperty("database.url")).contains("jdbc:pg");
        assertThat(source.getProperty("feature.enabled")).contains("true");
        // folder marker (trailing slash) is skipped
        assertThat(source.loadConfig()).hasSize(2);
        assertThat(token.get()).isEqualTo("acl-tok");
    }

    @Test
    void nullValueBecomesEmptyString() throws IOException {
        String body = "[{\"Key\":\"config/x/empty\",\"Value\":null}]";
        String url = startServer(200, body, null);

        ConsulConfigSource source = new ConsulConfigSource(url, "config/x", null, 100, true);
        assertThat(source.getProperty("empty")).contains("");
    }

    @Test
    void notFoundLoadsEmptyConfig() throws IOException {
        String url = startServer(404, "not found", null);

        ConsulConfigSource source = new ConsulConfigSource(url, "missing", null, 100, true);
        assertThat(source.loadConfig()).isEmpty();
        assertThat(source.refresh()).isTrue();
    }

    @Test
    void serverErrorReturnsFalse() throws IOException {
        String url = startServer(500, "err", null);

        ConsulConfigSource source = new ConsulConfigSource(url, "p", null, 100, true);
        assertThat(source.refresh()).isFalse();
    }

    @Test
    void refreshReloads() throws IOException {
        String body = "[{\"Key\":\"p/k\",\"Value\":\"" + b64("v") + "\"}]";
        String url = startServer(200, body, null);

        ConsulConfigSource source = new ConsulConfigSource(url, "p", null, 100, true);
        assertThat(source.refresh()).isTrue();
        assertThat(source.getProperty("k")).contains("v");
    }

    @Test
    void disabledWhenUrlBlank() {
        ConsulConfigSource source = new ConsulConfigSource("", "p", null, 100, true);
        assertThat(source.isEnabled()).isFalse();
        assertThat(source.refresh()).isFalse();
    }

    @Test
    void defaultConstructorPriority() throws IOException {
        String url = startServer(200, "[]", null);
        ConsulConfigSource source = new ConsulConfigSource(url, "p");
        assertThat(source.getPriority()).isEqualTo(140);
        assertThat(source.supportsRefresh()).isTrue();
    }
}
