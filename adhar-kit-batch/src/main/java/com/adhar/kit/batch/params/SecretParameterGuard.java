package com.adhar.kit.batch.params;

/**
 * Refuses parameter names that look like credentials (design 0025 §5.6).
 *
 * <p>Spring Batch stores parameters in clear text ({@code BATCH_JOB_EXECUTION_PARAMS}), and run
 * names, events and the Console show identifying ones. A declaration or a run naming a parameter
 * {@code password}, {@code apiKey} or similar is refused outright; credentials reach a run only
 * through Secrets ({@code secretKeyRef}, {@code envFrom}). The names are those
 * {@link ParameterMasking} masks.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class SecretParameterGuard {

    private SecretParameterGuard() {
    }

    /**
     * @param name a parameter name
     * @throws IllegalArgumentException when the name looks like a credential
     */
    public static void check(String name) {
        if (ParameterMasking.isSecretName(name)) {
            throw new IllegalArgumentException(message(name));
        }
    }

    /**
     * @param name a parameter name that looks like a credential
     * @return why it is refused and what to do instead
     */
    static String message(String name) {
        return "Parameter " + name + " looks like a credential: parameters are stored and shown in clear text. "
                + "Read credentials from a Secret (secretKeyRef or envFrom) instead";
    }
}
