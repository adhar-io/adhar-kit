package com.adhar.kit.graphql.allowlist;

import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of approved (allow-listed) GraphQL queries.
 *
 * <p>Queries are keyed both by the SHA-256 hex hash of their (trimmed) document text
 * &mdash; the same hash format used by the Apollo Automatic Persisted Queries protocol
 * &mdash; and by their operation name(s), so clients may be matched either way.</p>
 *
 * <p>Queries can be registered:</p>
 * <ul>
 *   <li><b>From the classpath</b>: every {@code *.graphql} / {@code *.gql} file below a
 *       configurable directory (default {@code graphql/allowed-queries}) is loaded via
 *       {@link #loadFromClasspath()}.</li>
 *   <li><b>Programmatically</b>: via {@link #register(String)} or
 *       {@link #register(String, String)}.</li>
 * </ul>
 *
 * <p>The registry is thread-safe.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AllowedQueryRegistry {

    private static final String[] FILE_PATTERNS = {"/**/*.graphql", "/**/*.gql"};

    private final String classpathLocation;
    private final Map<String, String> queriesByHash = new ConcurrentHashMap<>();
    private final Set<String> operationNames = ConcurrentHashMap.newKeySet();

    /**
     * Creates a registry that loads classpath queries from the given directory.
     *
     * @param classpathLocation classpath directory containing approved query documents
     */
    public AllowedQueryRegistry(String classpathLocation) {
        this.classpathLocation = Objects.requireNonNull(classpathLocation, "classpathLocation must not be null");
    }

    /**
     * Loads all {@code *.graphql} / {@code *.gql} documents below the configured
     * classpath directory and registers them.
     *
     * @return the number of documents loaded
     */
    public int loadFromClasspath() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String base = classpathLocation.replaceAll("/+$", "");
        int loaded = 0;
        for (String pattern : FILE_PATTERNS) {
            try {
                Resource[] resources = resolver.getResources("classpath*:" + base + pattern);
                for (Resource resource : resources) {
                    try {
                        String query = resource.getContentAsString(StandardCharsets.UTF_8);
                        register(query);
                        loaded++;
                        log.debug("Registered allow-listed query from {}", resource.getFilename());
                    } catch (IOException e) {
                        log.warn("Failed to read allow-listed query resource {}: {}", resource, e.getMessage());
                    }
                }
            } catch (IOException e) {
                log.debug("No allow-listed query resources found for pattern {}{}: {}", base, pattern, e.getMessage());
            }
        }
        log.info("Loaded {} allow-listed GraphQL queries from classpath:{}", loaded, base);
        return loaded;
    }

    /**
     * Registers an approved query. The query is keyed by the SHA-256 hash of its
     * trimmed text and by every operation name found in the document.
     *
     * @param query the GraphQL document text
     * @return the SHA-256 hex hash under which the query was registered
     */
    public String register(String query) {
        Objects.requireNonNull(query, "query must not be null");
        String trimmed = query.trim();
        String hash = sha256Hex(trimmed);
        queriesByHash.put(hash, trimmed);
        parseOperationNames(trimmed);
        return hash;
    }

    /**
     * Registers an approved query under an explicit operation name in addition to
     * its hash and any operation names found in the document.
     *
     * @param operationName the operation name to allow
     * @param query         the GraphQL document text
     * @return the SHA-256 hex hash under which the query was registered
     */
    public String register(String operationName, String query) {
        Objects.requireNonNull(operationName, "operationName must not be null");
        String hash = register(query);
        operationNames.add(operationName);
        return hash;
    }

    /**
     * Returns whether the given SHA-256 hex hash identifies an approved query.
     *
     * @param sha256Hash the hash to check (case-insensitive)
     * @return true if the hash is allow-listed
     */
    public boolean isAllowedHash(String sha256Hash) {
        return sha256Hash != null && queriesByHash.containsKey(sha256Hash.toLowerCase());
    }

    /**
     * Returns whether the given query document text is approved (by hash of its
     * trimmed text).
     *
     * @param query the GraphQL document text
     * @return true if the query is allow-listed
     */
    public boolean isAllowedQuery(String query) {
        return query != null && queriesByHash.containsKey(sha256Hex(query.trim()));
    }

    /**
     * Returns whether the given operation name is approved.
     *
     * @param operationName the operation name to check
     * @return true if the operation name is allow-listed
     */
    public boolean isAllowedOperationName(String operationName) {
        return operationName != null && operationNames.contains(operationName);
    }

    /**
     * Looks up the approved query document registered under the given hash.
     *
     * @param sha256Hash the SHA-256 hex hash
     * @return the query text, if registered
     */
    public Optional<String> queryForHash(String sha256Hash) {
        return sha256Hash == null
                ? Optional.empty()
                : Optional.ofNullable(queriesByHash.get(sha256Hash.toLowerCase()));
    }

    /**
     * Returns the number of registered queries.
     *
     * @return the registry size
     */
    public int size() {
        return queriesByHash.size();
    }

    /**
     * Removes all registered queries and operation names.
     */
    public void clear() {
        queriesByHash.clear();
        operationNames.clear();
    }

    private void parseOperationNames(String query) {
        try {
            Document document = new Parser().parseDocument(query);
            for (Definition<?> definition : document.getDefinitions()) {
                if (definition instanceof OperationDefinition operation && operation.getName() != null) {
                    operationNames.add(operation.getName());
                }
            }
        } catch (Exception e) {
            log.warn("Registered allow-listed query is not parseable; operation-name matching skipped: {}",
                    e.getMessage());
        }
    }

    /**
     * Computes the SHA-256 hex digest of the given text.
     *
     * @param text the text to hash
     * @return the lowercase hex-encoded SHA-256 digest
     */
    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
