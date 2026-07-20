package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CertificateExpiryHealthIndicator}.
 *
 * <p>Keystores are generated on the fly with the JDK's {@code keytool} so the tests
 * exercise real self-signed certificates.</p>
 */
class CertificateExpiryHealthIndicatorTest {

    @TempDir
    Path tempDir;

    private static final String PASSWORD = "changeit";

    private Path generateKeystore(String fileName, String... extraArgs) throws Exception {
        Path keystore = tempDir.resolve(fileName);
        List<String> command = new ArrayList<>(List.of(
            Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
            "-genkeypair",
            "-alias", "test",
            "-keyalg", "EC",
            "-groupname", "secp256r1",
            "-storetype", "PKCS12",
            "-keystore", keystore.toString(),
            "-storepass", PASSWORD,
            "-dname", "CN=adhar-test"));
        command.addAll(List.of(extraArgs));

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as("keytool output: %s", output).isZero();
        return keystore;
    }

    @Test
    void check_certificateFarFromExpiry_returnsUp() throws Exception {
        Path keystore = generateKeystore("valid.p12", "-validity", "3650");
        CertificateExpiryHealthIndicator indicator =
            new CertificateExpiryHealthIndicator(keystore.toString(), PASSWORD, "PKCS12", 30);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getComponent()).isEqualTo("certificate");
        assertThat(health.getDetails())
            .containsEntry("source", keystore.toString())
            .containsEntry("warningDays", 30)
            .containsEntry("certificateCount", 1)
            .containsKeys("subject", "notAfter", "daysRemaining");
        assertThat((long) health.getDetails().get("daysRemaining")).isGreaterThan(3000);
    }

    @Test
    void check_certificateWithinWarningWindow_returnsDown() throws Exception {
        Path keystore = generateKeystore("expiring.p12", "-validity", "5");
        CertificateExpiryHealthIndicator indicator =
            new CertificateExpiryHealthIndicator(keystore.toString(), PASSWORD, "PKCS12", 30);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getError()).contains("expires in");
    }

    @Test
    void check_expiredCertificate_returnsDown() throws Exception {
        Path keystore = generateKeystore("expired.p12", "-startdate", "-60d", "-validity", "30");
        CertificateExpiryHealthIndicator indicator =
            new CertificateExpiryHealthIndicator(keystore.toString(), PASSWORD, "PKCS12", 30);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getError()).contains("expired");
    }

    @Test
    void check_missingKeystore_returnsDown() {
        CertificateExpiryHealthIndicator indicator = new CertificateExpiryHealthIndicator(
            tempDir.resolve("missing.p12").toString(), PASSWORD, "PKCS12", 30);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getError()).isNotBlank();
    }

    @Test
    void check_emptyKeystore_returnsUnknown() throws Exception {
        Path keystore = tempDir.resolve("empty.p12");
        KeyStore empty = KeyStore.getInstance("PKCS12");
        empty.load(null, PASSWORD.toCharArray());
        try (OutputStream out = java.nio.file.Files.newOutputStream(keystore)) {
            empty.store(out, PASSWORD.toCharArray());
        }
        CertificateExpiryHealthIndicator indicator =
            new CertificateExpiryHealthIndicator(keystore.toString(), PASSWORD, "PKCS12", 30);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("message", "No X.509 certificates found");
    }

    @Test
    void check_endpointUnreachable_returnsDown() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        CertificateExpiryHealthIndicator indicator =
            new CertificateExpiryHealthIndicator("127.0.0.1", freePort, 30);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails()).containsEntry("source", "127.0.0.1:" + freePort);
    }

    @Test
    void configConstructor_selectsKeystoreModeWhenPathSet() throws Exception {
        Path keystore = generateKeystore("config.p12", "-validity", "3650");
        AdharHealthProperties.CertificateConfig config = new AdharHealthProperties.CertificateConfig();
        config.setKeystorePath(keystore.toString());
        config.setKeystorePassword(PASSWORD);
        config.setWarningDays(15);

        Health health = new CertificateExpiryHealthIndicator(config).check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails())
            .containsEntry("source", keystore.toString())
            .containsEntry("warningDays", 15);
    }

    @Test
    void configConstructor_selectsEndpointModeWhenNoPath() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        AdharHealthProperties.CertificateConfig config = new AdharHealthProperties.CertificateConfig();
        config.setHost("127.0.0.1");
        config.setPort(freePort);

        Health health = new CertificateExpiryHealthIndicator(config).check();

        assertThat(health.getDetails()).containsEntry("source", "127.0.0.1:" + freePort);
    }

    @Test
    void getName_returnsCertificate() {
        assertThat(new CertificateExpiryHealthIndicator("host", 443, 30).getName())
            .isEqualTo("certificate");
    }

    @Test
    void nullPasswordAndType_useDefaults() {
        CertificateExpiryHealthIndicator indicator = new CertificateExpiryHealthIndicator(
            tempDir.resolve("missing.p12").toString(), null, null, 30);

        // still behaves (missing file -> DOWN) with defaulted password/type
        assertThat(indicator.check().getStatus()).isEqualTo(Health.Status.DOWN);
    }
}
