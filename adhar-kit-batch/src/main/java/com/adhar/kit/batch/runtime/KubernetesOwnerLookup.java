package com.adhar.kit.batch.runtime;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The two reads a run makes of the Kubernetes API, with the pod's own ServiceAccount token and the
 * JDK's {@link HttpClient} (no Kubernetes client library in a run image): the owner Job (its
 * {@value #SCHEDULED_TIMESTAMP} annotation, its uid, its failed pods) and a pod (this one for the
 * image digest, or an execution's owner to see whether it is gone). The composed Role grants
 * {@code get} on {@code jobs} and {@code pods} in the run's namespace and nothing else.
 *
 * <p>Outside a cluster (no {@code KUBERNETES_SERVICE_HOST} or no token) it is
 * {@linkplain #isAvailable() unavailable}: nothing is read and every answer is "unknown".</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class KubernetesOwnerLookup {

    /** The CronJob controller's annotation on the Jobs it creates: the nominal scheduled time. */
    public static final String SCHEDULED_TIMESTAMP = "batch.kubernetes.io/cronjob-scheduled-timestamp";

    /** The run's container in the composed pod template. */
    public static final String CONTAINER = "batch";

    /** Where Kubernetes mounts the pod's ServiceAccount credentials. */
    public static final Path SERVICE_ACCOUNT = Path.of("/var/run/secrets/kubernetes.io/serviceaccount");

    private final URI apiServer;
    private final Supplier<String> token;
    private final String namespace;
    private final HttpClient client;
    private final Duration timeout;

    /**
     * @param apiServer the API server's base URI, {@code null} when unavailable
     * @param token     supplies the bearer token (re-read per call: projected tokens rotate)
     * @param namespace the run's namespace
     * @param client    the HTTP client, trusting the cluster's CA
     * @param timeout   per request
     */
    public KubernetesOwnerLookup(URI apiServer, Supplier<String> token, String namespace, HttpClient client,
                                 Duration timeout) {
        this.apiServer = apiServer;
        this.token = token;
        this.namespace = namespace;
        this.client = client;
        this.timeout = timeout;
    }

    /**
     * @return a lookup that reads nothing
     */
    public static KubernetesOwnerLookup unavailable() {
        return new KubernetesOwnerLookup(null, () -> "", "", null, Duration.ZERO);
    }

    /**
     * The lookup of a pod running in a cluster.
     *
     * @param env            the process environment ({@code KUBERNETES_SERVICE_HOST}/{@code _PORT},
     *                       {@code POD_NAMESPACE})
     * @param serviceAccount the mounted ServiceAccount directory (token, ca.crt, namespace)
     * @return the lookup, {@linkplain #unavailable() unavailable} outside a cluster
     */
    public static KubernetesOwnerLookup inCluster(Function<String, String> env, Path serviceAccount) {
        String host = env.apply("KUBERNETES_SERVICE_HOST");
        Path tokenFile = serviceAccount.resolve("token");
        if (host == null || host.isBlank() || !Files.isReadable(tokenFile)) {
            return unavailable();
        }
        String port = Optional.ofNullable(env.apply("KUBERNETES_SERVICE_PORT")).orElse("443");
        String namespace = Optional.ofNullable(env.apply("POD_NAMESPACE")).filter(ns -> !ns.isBlank())
                .orElseGet(() -> read(serviceAccount.resolve("namespace")).strip());
        URI uri = URI.create("https://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + port);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .sslContext(trusting(serviceAccount.resolve("ca.crt"))).build();
        return new KubernetesOwnerLookup(uri, () -> read(tokenFile).strip(), namespace, client,
                Duration.ofSeconds(10));
    }

    /**
     * @return whether the API can be asked at all
     */
    public boolean isAvailable() {
        return apiServer != null;
    }

    /**
     * @param name a Job in the run's namespace
     * @return the Job, empty when unavailable or not found
     * @throws KubernetesLookupException when the API answers with anything but the Job or 404
     */
    public Optional<OwnerJob> job(String name) {
        return get("/apis/batch/v1/namespaces/" + encode(namespace) + "/jobs/" + encode(name)).map(job -> {
            Object scheduled = Json.at(job, "metadata", "annotations", SCHEDULED_TIMESTAMP);
            Object failed = Json.at(job, "status", "failed");
            return new OwnerJob(string(Json.at(job, "metadata", "uid")), timestamp(scheduled),
                    failed instanceof BigDecimal count ? count.intValue() : 0);
        });
    }

    /**
     * @param name a pod in the run's namespace
     * @return the pod, empty when unavailable or not found
     * @throws KubernetesLookupException when the API answers with anything but the pod or 404
     */
    public Optional<Pod> pod(String name) {
        return get("/api/v1/namespaces/" + encode(namespace) + "/pods/" + encode(name)).map(pod -> new Pod(
                string(Json.at(pod, "metadata", "uid")), string(Json.at(pod, "status", "phase")),
                Json.at(pod, "metadata", "deletionTimestamp") != null, imageDigest(pod)));
    }

    private Optional<Object> get(String path) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        HttpRequest request = HttpRequest.newBuilder(apiServer.resolve(path)).timeout(timeout)
                .header("Authorization", "Bearer " + token.get()).header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() != 200) {
                throw new KubernetesLookupException("GET " + path + " answered " + response.statusCode()
                        + (response.statusCode() == 403 ? ": the run's ServiceAccount needs get on jobs and pods"
                        : ""));
            }
            return Optional.of(Json.parse(response.body()));
        } catch (IOException | IllegalArgumentException ex) {
            throw new KubernetesLookupException("GET " + path + " failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new KubernetesLookupException("GET " + path + " was interrupted", ex);
        }
    }

    private static String imageDigest(Object pod) {
        if (Json.at(pod, "status", "containerStatuses") instanceof List<?> statuses) {
            for (Object status : statuses) {
                if (CONTAINER.equals(Json.at(status, "name")) || statuses.size() == 1) {
                    String imageId = string(Json.at(status, "imageID"));
                    int digest = imageId.indexOf("sha256:");
                    return digest >= 0 ? imageId.substring(digest) : "";
                }
            }
        }
        return "";
    }

    private static Optional<OffsetDateTime> timestamp(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(text));
        } catch (DateTimeParseException ex) {
            log.warn("The owner Job's {} annotation is not a timestamp: '{}'", SCHEDULED_TIMESTAMP, text);
            return Optional.empty();
        }
    }

    private static String string(Object value) {
        return value instanceof String text ? text : "";
    }

    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException ex) {
            throw new KubernetesLookupException("Cannot read " + file + ": " + ex.getMessage(), ex);
        }
    }

    private static SSLContext trusting(Path caCertificate) {
        try {
            if (!Files.isReadable(caCertificate)) {
                return SSLContext.getDefault();
            }
            KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
            trust.load(null, null);
            try (InputStream in = Files.newInputStream(caCertificate)) {
                int i = 0;
                for (Certificate certificate : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                    trust.setCertificateEntry("cluster-ca-" + i++, certificate);
                }
            }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trust);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            return context;
        } catch (IOException | GeneralSecurityException ex) {
            throw new KubernetesLookupException("Cannot trust the cluster CA " + caCertificate + ": "
                    + ex.getMessage(), ex);
        }
    }

    /**
     * What a run needs from its owner Job.
     *
     * @param uid           the Job's uid
     * @param scheduledTime the CronJob's nominal time, empty for a Job no CronJob created
     * @param failed        the Job's failed pods so far
     */
    public record OwnerJob(String uid, Optional<OffsetDateTime> scheduledTime, int failed) {
    }

    /**
     * What a run needs from a pod.
     *
     * @param uid         the pod's uid
     * @param phase       {@code Pending}, {@code Running}, {@code Succeeded}, {@code Failed} or empty
     * @param terminating whether it is being deleted
     * @param imageDigest the run container's image digest, empty when not yet known
     */
    public record Pod(String uid, String phase, boolean terminating, String imageDigest) {

        /**
         * @return whether its containers have ended for good
         */
        public boolean isTerminal() {
            return "Succeeded".equals(phase) || "Failed".equals(phase);
        }
    }

    /**
     * The Kubernetes API could not be asked. A scheduled run cannot know its business date
     * without it, so the run fails as a start-up failure (exit 1), which the Job retries.
     */
    public static class KubernetesLookupException extends RuntimeException {

        KubernetesLookupException(String message) {
            super(message);
        }

        KubernetesLookupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
