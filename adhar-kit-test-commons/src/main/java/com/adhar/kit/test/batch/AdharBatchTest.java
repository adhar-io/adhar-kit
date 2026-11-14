package com.adhar.kit.test.batch;

import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A batch application's integration test as the platform runs it (design 0025 §5.19): the real
 * auto-configuration ({@code @SpringBootTest}, no web server), {@code @SpringBatchTest}'s
 * {@code JobOperatorTestUtils} and {@code JobRepositoryTestUtils}, a fresh database on a
 * PostgreSQL {@value com.adhar.kit.test.container.PostgresTestContainer#PLATFORM_IMAGE} container
 * migrated by the kit's own Flyway schema ({@code batch}), and optionally an S3 endpoint.
 *
 * <pre>{@code
 * @AdharBatchTest
 * class ImportJobIT {
 *     @Autowired JobOperatorTestUtils jobs;
 *
 *     @Test
 *     void importsTheDay() throws Exception {
 *         JobExecution run = jobs.startJob(new JobParametersBuilder()
 *                 .addLocalDate("businessDate", LocalDate.of(2026, 11, 3)).toJobParameters());
 *         assertThat(run.getStatus()).isEqualTo(BatchStatus.COMPLETED);
 *     }
 * }
 * }</pre>
 *
 * <p>The datasource properties point at the container whatever {@code application.yaml} says
 * (a scaffolded service reads them from the environment), and Spring Boot's start-up job runner is
 * off, so a job runs only when the test starts it. The test's own {@code properties} still win. The
 * containers are public images with explicit tags and no bind mounts, so app-ci runs them under
 * kubedock; without Docker the test FAILS rather than skipping, because a skipped restart test is
 * indistinguishable from a passing one.</p>
 *
 * @since 0.1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBatchTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public @interface AdharBatchTest {

    /**
     * @return the configuration classes, as {@link SpringBootTest#classes()}
     */
    @AliasFor(annotation = SpringBootTest.class, attribute = "classes")
    Class<?>[] classes() default {};

    /**
     * @return extra properties, as {@link SpringBootTest#properties()}; they win over the kit's
     */
    @AliasFor(annotation = SpringBootTest.class, attribute = "properties")
    String[] properties() default {};

    /**
     * @return whether to start an S3 endpoint (LocalStack) and point Spring Cloud AWS at it,
     *         path-style, as on RustFS; bucket names are then free to create
     */
    boolean s3() default false;
}
