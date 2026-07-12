package com.adhar.kit.persistence.specification;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Fluent builder for JPA {@link Specification} instances.
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * Specification<User> spec = SpecificationBuilder.of(User.class)
 *     .equal("status", "ACTIVE")
 *     .like("name", "%john%")
 *     .greaterThan("age", 18)
 *     .in("role", List.of("ADMIN", "USER"))
 *     .between("createdAt", startDate, endDate)
 *     .isNull("deletedAt")
 *     .build();
 *
 * List<User> users = persistenceFacade.findAll(User.class, spec);
 * }</pre>
 *
 * @param <T> the root entity type
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public class SpecificationBuilder<T> {

    private final List<Specification<T>> specifications = new ArrayList<>();

    private SpecificationBuilder() {
    }

    /**
     * Creates a new builder for the given entity class.
     *
     * @param entityClass the entity type (used for generic inference)
     * @param <T>         the entity type
     * @return a new SpecificationBuilder
     */
    public static <T> SpecificationBuilder<T> of(Class<T> entityClass) {
        return new SpecificationBuilder<>();
    }

    /**
     * Adds an equality predicate ({@code field = value}).
     *
     * @param field the entity field name
     * @param value the value to match
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> equal(String field, Object value) {
        specifications.add((root, query, cb) -> cb.equal(root.get(field), value));
        return this;
    }

    /**
     * Adds a not-equal predicate ({@code field != value}).
     *
     * @param field the entity field name
     * @param value the value to exclude
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> notEqual(String field, Object value) {
        specifications.add((root, query, cb) -> cb.notEqual(root.get(field), value));
        return this;
    }

    /**
     * Adds a case-sensitive LIKE predicate.
     *
     * @param field   the entity field name
     * @param pattern the LIKE pattern (e.g., {@code "%john%"})
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> like(String field, String pattern) {
        specifications.add((root, query, cb) -> cb.like(root.get(field), pattern));
        return this;
    }

    /**
     * Adds a case-insensitive LIKE predicate.
     *
     * @param field   the entity field name
     * @param pattern the LIKE pattern (e.g., {@code "%john%"})
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> likeIgnoreCase(String field, String pattern) {
        specifications.add((root, query, cb) ->
                cb.like(cb.lower(root.get(field)), pattern.toLowerCase()));
        return this;
    }

    /**
     * Adds a greater-than predicate ({@code field > value}).
     *
     * @param field the entity field name
     * @param value the lower bound (exclusive)
     * @param <Y>   the comparable value type
     * @return this builder for chaining
     */
    @SuppressWarnings("unchecked")
    public <Y extends Comparable<Y>> SpecificationBuilder<T> greaterThan(String field, Y value) {
        specifications.add((root, query, cb) ->
                cb.greaterThan(root.get(field), value));
        return this;
    }

    /**
     * Adds a greater-than-or-equal predicate ({@code field >= value}).
     *
     * @param field the entity field name
     * @param value the lower bound (inclusive)
     * @param <Y>   the comparable value type
     * @return this builder for chaining
     */
    @SuppressWarnings("unchecked")
    public <Y extends Comparable<Y>> SpecificationBuilder<T> greaterThanOrEqual(String field, Y value) {
        specifications.add((root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get(field), value));
        return this;
    }

    /**
     * Adds a less-than predicate ({@code field < value}).
     *
     * @param field the entity field name
     * @param value the upper bound (exclusive)
     * @param <Y>   the comparable value type
     * @return this builder for chaining
     */
    @SuppressWarnings("unchecked")
    public <Y extends Comparable<Y>> SpecificationBuilder<T> lessThan(String field, Y value) {
        specifications.add((root, query, cb) ->
                cb.lessThan(root.get(field), value));
        return this;
    }

    /**
     * Adds a less-than-or-equal predicate ({@code field <= value}).
     *
     * @param field the entity field name
     * @param value the upper bound (inclusive)
     * @param <Y>   the comparable value type
     * @return this builder for chaining
     */
    @SuppressWarnings("unchecked")
    public <Y extends Comparable<Y>> SpecificationBuilder<T> lessThanOrEqual(String field, Y value) {
        specifications.add((root, query, cb) ->
                cb.lessThanOrEqualTo(root.get(field), value));
        return this;
    }

    /**
     * Adds a BETWEEN predicate ({@code lower <= field <= upper}).
     *
     * @param field the entity field name
     * @param lower the lower bound (inclusive)
     * @param upper the upper bound (inclusive)
     * @param <Y>   the comparable value type
     * @return this builder for chaining
     */
    @SuppressWarnings("unchecked")
    public <Y extends Comparable<Y>> SpecificationBuilder<T> between(String field, Y lower, Y upper) {
        specifications.add((root, query, cb) ->
                cb.between(root.get(field), lower, upper));
        return this;
    }

    /**
     * Adds an IN predicate ({@code field IN (values)}).
     *
     * @param field  the entity field name
     * @param values the collection of allowed values
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> in(String field, Collection<?> values) {
        specifications.add((root, query, cb) -> root.get(field).in(values));
        return this;
    }

    /**
     * Adds an IS NULL predicate.
     *
     * @param field the entity field name
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> isNull(String field) {
        specifications.add((root, query, cb) -> cb.isNull(root.get(field)));
        return this;
    }

    /**
     * Adds an IS NOT NULL predicate.
     *
     * @param field the entity field name
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> isNotNull(String field) {
        specifications.add((root, query, cb) -> cb.isNotNull(root.get(field)));
        return this;
    }

    /**
     * Adds a custom {@link Specification} predicate.
     *
     * @param spec the specification to add
     * @return this builder for chaining
     */
    public SpecificationBuilder<T> where(Specification<T> spec) {
        specifications.add(spec);
        return this;
    }

    /**
     * Builds the combined specification by ANDing all predicates.
     *
     * @return a composed Specification, or a no-op specification producing a
     *         {@code null} predicate if empty
     */
    public Specification<T> build() {
        if (specifications.isEmpty()) {
            // Spring Data 4's Specification.where(null) now rejects null; return a
            // no-op specification that contributes no predicate instead.
            return (root, query, criteriaBuilder) -> null;
        }
        Specification<T> result = Specification.where(specifications.getFirst());
        for (int i = 1; i < specifications.size(); i++) {
            result = result.and(specifications.get(i));
        }
        return result;
    }
}
