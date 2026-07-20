package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

/**
 * Certificate expiry health indicator.
 *
 * <p>Checks either a keystore on disk or the TLS certificate presented by a remote
 * endpoint, and reports DOWN when the certificate closest to expiry is already
 * expired or expires within the configured number of warning days.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Keystore mode
 * CertificateExpiryHealthIndicator indicator = new CertificateExpiryHealthIndicator(
 *     "/etc/tls/service.p12", "changeit", "PKCS12", 30);
 *
 * // Endpoint mode
 * CertificateExpiryHealthIndicator indicator = new CertificateExpiryHealthIndicator(
 *     "api.example.com", 443, 30);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class CertificateExpiryHealthIndicator implements AdharHealthIndicator {

    private static final int CONNECT_TIMEOUT_MILLIS = 5000;

    private final String keystorePath;
    private final char[] keystorePassword;
    private final String keystoreType;
    private final String host;
    private final int port;
    private final int warningDays;

    /**
     * Creates a keystore-based certificate expiry indicator.
     *
     * @param keystorePath     path to the keystore file
     * @param keystorePassword keystore password (may be null)
     * @param keystoreType     keystore type (e.g. PKCS12, JKS)
     * @param warningDays      report DOWN when expiry is within this many days
     */
    public CertificateExpiryHealthIndicator(String keystorePath, String keystorePassword,
                                            String keystoreType, int warningDays) {
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword == null ? new char[0] : keystorePassword.toCharArray();
        this.keystoreType = keystoreType == null ? "PKCS12" : keystoreType;
        this.host = null;
        this.port = 0;
        this.warningDays = warningDays;
    }

    /**
     * Creates an endpoint-based certificate expiry indicator.
     *
     * @param host        TLS endpoint host
     * @param port        TLS endpoint port
     * @param warningDays report DOWN when expiry is within this many days
     */
    public CertificateExpiryHealthIndicator(String host, int port, int warningDays) {
        this.keystorePath = null;
        this.keystorePassword = new char[0];
        this.keystoreType = null;
        this.host = host;
        this.port = port;
        this.warningDays = warningDays;
    }

    /**
     * Creates an indicator from configuration properties.
     *
     * <p>Keystore mode is used when a keystore path is configured, endpoint mode
     * otherwise.</p>
     *
     * @param config certificate health configuration
     */
    public CertificateExpiryHealthIndicator(AdharHealthProperties.CertificateConfig config) {
        if (config.getKeystorePath() != null && !config.getKeystorePath().isBlank()) {
            this.keystorePath = config.getKeystorePath();
            this.keystorePassword = config.getKeystorePassword() == null
                    ? new char[0] : config.getKeystorePassword().toCharArray();
            this.keystoreType = config.getKeystoreType() == null ? "PKCS12" : config.getKeystoreType();
            this.host = null;
            this.port = 0;
        } else {
            this.keystorePath = null;
            this.keystorePassword = new char[0];
            this.keystoreType = null;
            this.host = config.getHost();
            this.port = config.getPort();
        }
        this.warningDays = config.getWarningDays();
    }

    @Override
    public Health check() {
        try {
            List<X509Certificate> certificates =
                    keystorePath != null ? loadFromKeystore() : loadFromEndpoint();

            if (certificates.isEmpty()) {
                return Health.unknown()
                        .component(getName())
                        .withDetail("source", source())
                        .withDetail("message", "No X.509 certificates found")
                        .build();
            }

            X509Certificate earliest = certificates.stream()
                    .min(Comparator.comparing(X509Certificate::getNotAfter))
                    .orElseThrow();

            Instant notAfter = earliest.getNotAfter().toInstant();
            long daysRemaining = Duration.between(Instant.now(), notAfter).toDays();

            Health.HealthBuilder builder;
            if (notAfter.isBefore(Instant.now())) {
                builder = Health.down()
                        .error("Certificate expired on " + notAfter);
            } else if (daysRemaining < warningDays) {
                builder = Health.down()
                        .error("Certificate expires in " + daysRemaining + " days (warning threshold: "
                                + warningDays + " days)");
            } else {
                builder = Health.up();
            }

            return builder
                    .component(getName())
                    .withDetail("source", source())
                    .withDetail("subject", earliest.getSubjectX500Principal().getName())
                    .withDetail("notAfter", notAfter.toString())
                    .withDetail("daysRemaining", daysRemaining)
                    .withDetail("warningDays", warningDays)
                    .withDetail("certificateCount", certificates.size())
                    .build();
        } catch (Exception e) {
            log.error("Certificate expiry health check failed", e);
            return Health.down(e)
                    .component(getName())
                    .withDetail("source", source())
                    .build();
        }
    }

    @Override
    public String getName() {
        return "certificate";
    }

    private String source() {
        return keystorePath != null ? keystorePath : host + ":" + port;
    }

    private List<X509Certificate> loadFromKeystore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keystoreType);
        try (InputStream in = new FileInputStream(keystorePath)) {
            keyStore.load(in, keystorePassword);
        }

        List<X509Certificate> certificates = new ArrayList<>();
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Certificate certificate = keyStore.getCertificate(alias);
            if (certificate instanceof X509Certificate x509) {
                certificates.add(x509);
            }
        }
        return certificates;
    }

    private List<X509Certificate> loadFromEndpoint() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new javax.net.ssl.TrustManager[]{new TrustAllManager()}, null);

        try (Socket raw = new Socket()) {
            raw.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            try (SSLSocket socket = (SSLSocket) context.getSocketFactory()
                    .createSocket(raw, host, port, true)) {
                socket.setSoTimeout(CONNECT_TIMEOUT_MILLIS);
                socket.startHandshake();

                List<X509Certificate> certificates = new ArrayList<>();
                for (Certificate certificate : socket.getSession().getPeerCertificates()) {
                    if (certificate instanceof X509Certificate x509) {
                        certificates.add(x509);
                    }
                }
                return certificates;
            }
        }
    }

    /**
     * Trust manager that accepts any chain — this indicator only inspects expiry,
     * it does not validate trust.
     */
    private static final class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // expiry inspection only — trust decisions are out of scope
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // expiry inspection only — trust decisions are out of scope
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
