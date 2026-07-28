package com.adhar.kit.test.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;

/**
 * Testcontainer configuration for LocalStack - a local emulator for AWS services (S3, SQS, SNS,
 * DynamoDB, etc.) for integration tests that talk to AWS.
 *
 * <p>Follows the singleton style of the other {@code *TestContainer} helpers. Enable the services a
 * test needs before the first {@link #start()} via {@link #withServices(String...)}, then wire the
 * AWS SDK to {@link #getEndpoint()} with {@link #getAccessKey()}/{@link #getSecretKey()}/
 * {@link #getRegion()}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public class LocalStackTestContainer {

    private static final Logger log = LoggerFactory.getLogger(LocalStackTestContainer.class);
    private static final String LOCALSTACK_IMAGE = "localstack/localstack:3.4";

    private static LocalStackContainer container;

    /**
     * Get a singleton LocalStack container instance.
     */
    public static LocalStackContainer getInstance() {
        if (container == null) {
            container = new LocalStackContainer(DockerImageName.parse(LOCALSTACK_IMAGE))
                    .withReuse(true);
        }
        return container;
    }

    /**
     * Enable one or more AWS services (e.g. {@code "s3"}, {@code "sqs"}) before the container is
     * started. Returns the container so calls can be chained.
     */
    public static LocalStackContainer withServices(String... services) {
        return getInstance().withServices(services);
    }

    /**
     * Start the LocalStack container.
     */
    public static void start() {
        getInstance().start();
        log.info("LocalStack container started at {}", container.getEndpoint());
    }

    /**
     * Stop the LocalStack container.
     */
    public static void stop() {
        if (container != null && container.isRunning()) {
            container.stop();
            log.info("LocalStack container stopped");
        }
    }

    /**
     * Get the LocalStack endpoint URI (the edge port serving all enabled services).
     */
    public static URI getEndpoint() {
        return getInstance().getEndpoint();
    }

    /**
     * Get the AWS access key to use with LocalStack.
     */
    public static String getAccessKey() {
        return getInstance().getAccessKey();
    }

    /**
     * Get the AWS secret key to use with LocalStack.
     */
    public static String getSecretKey() {
        return getInstance().getSecretKey();
    }

    /**
     * Get the AWS region LocalStack is emulating.
     */
    public static String getRegion() {
        return getInstance().getRegion();
    }
}
