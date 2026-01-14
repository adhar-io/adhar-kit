package com.adhar.kit.core.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Specification pattern implementation for building complex queries.
 *
 * <p>Enables combining business rules in a composable way using AND, OR, NOT operations.</p>
 *
 * <p><b>Example - Basic Specification:</b></p>
 * <pre>{@code
 * Specification<User> activeUsers = user -> user.isActive();
 * Specification<User> premiumUsers = user -> user.isPremium();
 *
 * // Combine specifications
 * Specification<User> activePremiumUsers = activeUsers.and(premiumUsers);
 *
 * // Use with collections
 * List<User> filteredUsers = users.stream()
 *     .filter(activePremiumUsers)
 *     .collect(Collectors.toList());
 * }</pre>
 *
 * <p><b>Example - JPA Repository:</b></p>
 * <pre>{@code
 * public interface UserRepository extends JpaSpecificationExecutor<User> {
 * }
 *
 * // Build complex query
 * Specification<User> spec = Specification
 *     .<User>where((root, query, cb) -> cb.equal(root.get("active"), true))
 *     .and((root, query, cb) -> cb.equal(root.get("role"), "ADMIN"))
 *     .and((root, query, cb) -> cb.greaterThan(root.get("loginCount"), 10));
 *
 * List<User> users = userRepository.findAll(spec);
 * }</pre>
 *
 * @param <T> the type to filter
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@FunctionalInterface
public interface Specification<T> extends Predicate<T> {

    /**
     * Combines this specification with another using AND.
     *
     * @param other the other specification
     * @return combined specification
     */
    default Specification<T> and(Specification<T> other) {
        return t -> test(t) && other.test(t);
    }

    /**
     * Combines this specification with another using OR.
     *
     * @param other the other specification
     * @return combined specification
     */
    default Specification<T> or(Specification<T> other) {
        return t -> test(t) || other.test(t);
    }

    /**
     * Negates this specification.
     *
     * @return negated specification
     */
    default Specification<T> negate() {
        return t -> !test(t);
    }

    /**
     * Creates a specification that always returns true.
     *
     * @param <T> the type
     * @return always-true specification
     */
    static <T> Specification<T> alwaysTrue() {
        return t -> true;
    }

    /**
     * Creates a specification that always returns false.
     *
     * @param <T> the type
     * @return always-false specification
     */
    static <T> Specification<T> alwaysFalse() {
        return t -> false;
    }

    /**
     * Creates a WHERE clause for starting specification chain.
     *
     * @param spec the specification
     * @param <T> the type
     * @return the specification
     */
    static <T> Specification<T> where(Specification<T> spec) {
        return spec;
    }

    /**
     * Filters a list using this specification.
     *
     * @param items the items to filter
     * @return filtered list
     */
    default List<T> filter(List<T> items) {
        List<T> result = new ArrayList<>();
        for (T item : items) {
            if (test(item)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Finds first matching item.
     *
     * @param items the items to search
     * @return first matching item or empty
     */
    default Optional<T> findFirst(List<T> items) {
        for (T item : items) {
            if (test(item)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if any item matches.
     *
     * @param items the items to check
     * @return true if any matches
     */
    default boolean anyMatch(List<T> items) {
        for (T item : items) {
            if (test(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if all items match.
     *
     * @param items the items to check
     * @return true if all match
     */
    default boolean allMatch(List<T> items) {
        for (T item : items) {
            if (!test(item)) {
                return false;
            }
        }
        return true;
    }
}

