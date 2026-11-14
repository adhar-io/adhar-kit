package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.runtime.BatchRole;
import com.adhar.kit.batch.runtime.ExitCodes;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The condition of {@link ConditionalOnBatchRole}.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class OnBatchRoleCondition extends SpringBootCondition {

    /** The role property ({@code ADHAR_BATCH_ROLE}). */
    static final String ROLE_PROPERTY = "adhar.batch.role";

    /** The Indexed Job's completion index, which Kubernetes sets on every pod of an Indexed Job. */
    static final String COMPLETION_INDEX = "JOB_COMPLETION_INDEX";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnBatchRole.class.getName());
        BatchRole[] named = attributes == null ? new BatchRole[0] : (BatchRole[]) attributes.get("value");
        Set<BatchRole> roles = named.length == 0 ? EnumSet.noneOf(BatchRole.class) : EnumSet.copyOf(Arrays.asList(named));
        if (roles.contains(BatchRole.INDEXED)) {
            throw new IllegalStateException("@ConditionalOnBatchRole(INDEXED) on " + metadata + ": indexed is resolved "
                    + "to manager or worker by JOB_COMPLETION_INDEX; name MANAGER or WORKER");
        }
        BatchRole effective = effectiveRole(context.getEnvironment());
        boolean matches = roles.contains(effective) || (effective == BatchRole.SINGLE
                && (roles.contains(BatchRole.MANAGER) || roles.contains(BatchRole.WORKER)));
        String message = "batch role " + effective.label() + (matches ? " is one of " : " is not one of ") + roles;
        return matches ? ConditionOutcome.match(message) : ConditionOutcome.noMatch(message);
    }

    /**
     * @param environment the application's environment
     * @return the pod's role: {@code adhar.batch.role}, with {@code indexed} resolved through
     *         {@code JOB_COMPLETION_INDEX}; {@code single} when unset
     * @throws IllegalStateException when {@code JOB_COMPLETION_INDEX} is not a number; it carries exit
     *         code 2, so the pod fails as an invalid request instead of being retried
     */
    public static BatchRole effectiveRole(Environment environment) {
        BatchRole role = Binder.get(environment).bind(ROLE_PROPERTY, BatchRole.class).orElse(BatchRole.SINGLE);
        String index = environment.getProperty(COMPLETION_INDEX, "").strip();
        try {
            return role.effective(index.isEmpty() ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(index)));
        } catch (NumberFormatException ex) {
            throw new InvalidCompletionIndex(COMPLETION_INDEX + " must be a number, was '" + index + "'", ex);
        }
    }

    /** A completion index that is not a number: an invalid request (exit 2), which no retry fixes. */
    static final class InvalidCompletionIndex extends IllegalStateException implements ExitCodeGenerator {

        InvalidCompletionIndex(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public int getExitCode() {
            return ExitCodes.INVALID_REQUEST;
        }
    }
}
