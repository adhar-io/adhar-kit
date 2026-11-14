package com.adhar.kit.test.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @AdharBatchTest(s3 = true)}: Spring Cloud AWS is pointed at a running S3 endpoint, path-style.
 */
@AdharBatchTest(classes = CopyJobApplication.class, s3 = true, properties = {
        "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:batch/schema.sql"})
@Testcontainers(disabledWithoutDocker = true)
class AdharBatchTestS3Test {

    @Autowired
    Environment environment;

    @Test
    @DisplayName("the S3 properties name a live endpoint, path-style, with credentials and a region")
    void s3Endpoint() throws Exception {
        String endpoint = environment.getProperty("spring.cloud.aws.s3.endpoint");
        assertThat(environment.getProperty("spring.cloud.aws.s3.path-style-access-enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.cloud.aws.region.static")).isNotBlank();
        assertThat(environment.getProperty("spring.cloud.aws.credentials.access-key")).isNotBlank();

        HttpResponse<String> health = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create(endpoint + "/_localstack/health")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"s3\"");
    }
}
