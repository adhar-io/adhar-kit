package com.adhar.kit.tracing.instrumentation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.observation.LettuceObservationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Valkey/Redis command through Spring Boot's Lettuce connection is a CLIENT span, child of
 * the caller's span, named after the command - and the arguments (keys and values, which can
 * be personal data) are not recorded. Runs against a minimal in-test RESP server.
 */
class LettuceTracingTest {

    private ServerSocket server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<String> commands = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        executor.submit(() -> {
            while (!server.isClosed()) {
                Socket socket = server.accept();
                executor.submit(() -> serve(socket));
            }
            return null;
        });
    }

    @AfterEach
    void stopServer() throws IOException {
        server.close();
        executor.shutdownNow();
    }

    @Test
    void aCommandIsAClientSpanNamedAfterTheCommandWithoutItsArguments() {
        new ApplicationContextRunner()
                .withConfiguration(TracingTestSupport.TRACING)
                .withConfiguration(AutoConfigurations.of(LettuceObservationAutoConfiguration.class,
                        DataRedisAutoConfiguration.class))
                .withUserConfiguration(TracingTestSupport.InMemoryExport.class)
                .withPropertyValues(TracingTestSupport.PROPERTIES)
                .withPropertyValues("spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=" + server.getLocalPort())
                .run(context -> {
                    StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
                    ObservationRegistry registry = context.getBean(ObservationRegistry.class);
                    Observation.createNotStarted("cache-order", registry)
                            .observe(() -> redis.opsForValue().set("customer:alice", "card-4111"));

                    assertThat(commands).contains("SET");
                    InMemorySpanExporter exporter = context.getBean(InMemorySpanExporter.class);
                    SpanData parent = exporter.getFinishedSpanItems().stream()
                            .filter(span -> span.getName().equals("cache-order")).findFirst().orElseThrow();
                    SpanData set = awaitSetSpan(exporter);

                    assertThat(set.getKind()).isEqualTo(SpanKind.CLIENT);
                    assertThat(set.getTraceId()).isEqualTo(parent.getTraceId());
                    assertThat(set.getParentSpanId()).isEqualTo(parent.getSpanId());
                    assertThat(set.getAttributes().toString()).doesNotContain("alice", "card-4111");
                });
    }

    private static SpanData awaitSetSpan(InMemorySpanExporter exporter) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            for (SpanData span : exporter.getFinishedSpanItems()) {
                if (span.getName().toLowerCase(Locale.ROOT).equals("set")) {
                    return span;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("no SET span exported; spans: " + exporter.getFinishedSpanItems());
    }

    /** Answers HELLO with an error (so the client falls back to RESP2) and everything else with +OK. */
    private Void serve(Socket socket) throws IOException {
        try (socket) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            List<String> command;
            while ((command = readCommand(in)) != null) {
                String name = command.isEmpty() ? "" : command.get(0).toUpperCase(Locale.ROOT);
                commands.add(name);
                out.write(("HELLO".equals(name) ? "-ERR unknown command 'HELLO'\r\n" : "+OK\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                out.flush();
            }
        }
        return null;
    }

    private static List<String> readCommand(InputStream in) throws IOException {
        String header = readLine(in);
        if (header == null || !header.startsWith("*")) {
            return null;
        }
        int count = Integer.parseInt(header.substring(1));
        List<String> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int length = Integer.parseInt(readLine(in).substring(1));
            byte[] bytes = in.readNBytes(length);
            in.readNBytes(2);
            parts.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return parts;
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\r') {
                in.read();
                return line.toString();
            }
            line.append((char) c);
        }
        return null;
    }
}
