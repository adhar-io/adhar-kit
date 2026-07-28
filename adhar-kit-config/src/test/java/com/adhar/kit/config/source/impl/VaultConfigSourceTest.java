package com.adhar.kit.config.source.impl;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VaultConfigSourceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(String path, int status, String body, AtomicReference<String> tokenCapture,
                               AtomicReference<String> namespaceCapture) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            if (tokenCapture != null) {
                tokenCapture.set(exchange.getRequestHeaders().getFirst("X-Vault-Token"));
            }
            if (namespaceCapture != null) {
                namespaceCapture.set(exchange.getRequestHeaders().getFirst("X-Vault-Namespace"));
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
    void loadsKvV2SecretsAndFlattensNested() throws IOException {
        String body = "{\"data\":{\"data\":{\"database.password\":\"s3cr3t\",\"nested\":{\"key\":\"val\"}}}}";
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> namespace = new AtomicReference<>();
        String address = startServer("/v1/secret/data/myapp", 200, body, token, namespace);

        VaultConfigSource source = new VaultConfigSource(address, "secret/myapp", "team-a", "my-token", 150, true, 2);

        assertThat(source.getType()).isEqualTo("vault");
        assertThat(source.getPriority()).isEqualTo(150);
        assertThat(source.supportsRefresh()).isTrue();
        assertThat(source.getProperty("database.password")).contains("s3cr3t");
        assertThat(source.getProperty("nested.key")).contains("val");
        assertThat(source.loadConfig()).hasSize(2);
        assertThat(token.get()).isEqualTo("my-token");
        assertThat(namespace.get()).isEqualTo("team-a");
    }

    @Test
    void loadsKvV1Secrets() throws IOException {
        String body = "{\"data\":{\"api.key\":\"abc123\"}}";
        String address = startServer("/v1/secret/app", 200, body, null, null);

        VaultConfigSource source = new VaultConfigSource(address, "secret/app", null, "tok", 100, true, 1);

        assertThat(source.getProperty("api.key")).contains("abc123");
    }

    @Test
    void refreshReturnsTrueOnSuccess() throws IOException {
        String body = "{\"data\":{\"data\":{\"k\":\"v\"}}}";
        String address = startServer("/v1/secret/data/app", 200, body, null, null);

        VaultConfigSource source = new VaultConfigSource(address, "secret/app", null, "tok", 100, true, 2);
        assertThat(source.refresh()).isTrue();
        assertThat(source.getProperty("k")).contains("v");
    }

    @Test
    void httpErrorReturnsFalseAndKeepsEmptyConfig() throws IOException {
        String address = startServer("/v1/secret/data/app", 500, "boom", null, null);

        VaultConfigSource source = new VaultConfigSource(address, "secret/app", null, "tok", 100, true, 2);
        assertThat(source.loadConfig()).isEmpty();
        assertThat(source.refresh()).isFalse();
    }

    @Test
    void disabledWhenTokenMissing() {
        VaultConfigSource source = new VaultConfigSource("http://localhost:8200", "secret/app", null, "", 100, true, 2);
        assertThat(source.isEnabled()).isFalse();
        assertThat(source.refresh()).isFalse();
        assertThat(source.loadConfig()).isEmpty();
    }

    @Test
    void unreachableAddressLoadsEmptyWithoutThrowing() {
        // Nothing listening on this port; construction should not throw.
        VaultConfigSource source = new VaultConfigSource("http://127.0.0.1:1", "secret/app", null, "tok", 100, true, 2);
        assertThat(source.loadConfig()).isEmpty();
    }
}
