package com.adhar.kit.batch;

import com.adhar.kit.batch.runtime.ExitCodes;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.function.IntConsumer;

/**
 * The {@code main} of a batch application: run the job, exit with its code.
 *
 * <pre>{@code
 * @SpringBootApplication
 * public class ImporterApplication {
 *     public static void main(String[] args) {
 *         AdharBatchApplication.run(ImporterApplication.class, args);
 *     }
 * }
 * }</pre>
 *
 * <p>In a Kubernetes run the context runs the job while it starts ({@code AdharBatchRunner}), and
 * {@code SpringApplication.exit} then returns the run's exit code ({@code BatchExitCodeGenerator}),
 * which the pod's failure policy keys on (design 0025 §5.7). A context that fails to start exits
 * with the failure's own code when it carries one (2 for an invalid JobRepository), otherwise 1.
 * When the pod is terminating, the context is already closing: the JVM keeps its signal status,
 * because calling {@code System.exit} from inside a shutdown blocks.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class AdharBatchApplication {

    private AdharBatchApplication() {
    }

    /**
     * Runs the application and exits the JVM with its code.
     *
     * @param source the application's {@code @SpringBootApplication} class
     * @param args   the command line
     */
    public static void run(Class<?> source, String... args) {
        run(new SpringApplication(source), System::exit, args);
    }

    /**
     * @param application the application to run
     * @param exit        what exits the process with a code
     * @param args        the command line
     */
    public static void run(SpringApplication application, IntConsumer exit, String... args) {
        ConfigurableApplicationContext context;
        try {
            context = application.run(args);
        } catch (RuntimeException ex) {
            exit.accept(exitCodeOf(ex));
            return;
        }
        if (context.isClosed()) {
            return;
        }
        exit.accept(SpringApplication.exit(context));
    }

    /**
     * @param failure why the context could not start
     * @return the first exit code carried in its cause chain, else {@link ExitCodes#STARTUP_FAILURE}
     */
    static int exitCodeOf(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 32; depth++, cause = cause.getCause()) {
            if (cause instanceof ExitCodeGenerator generator && generator.getExitCode() != 0) {
                return generator.getExitCode();
            }
        }
        return ExitCodes.STARTUP_FAILURE;
    }
}
