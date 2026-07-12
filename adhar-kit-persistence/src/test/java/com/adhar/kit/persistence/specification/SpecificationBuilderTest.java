package com.adhar.kit.persistence.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SpecificationBuilder Tests")
@SuppressWarnings({"unchecked", "rawtypes"})
class SpecificationBuilderTest {

    static class User {
    }

    @Mock
    private Root<User> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path path;
    @Mock
    private Expression lowerExpr;
    @Mock
    private Predicate predicate;

    @BeforeEach
    void setUp() {
        when(root.get(any(String.class))).thenReturn(path);
        when(cb.lower(any(Expression.class))).thenReturn(lowerExpr);
        // Stub all predicate-producing CB methods to return the shared predicate.
        lenient().when(cb.equal(any(Expression.class), any(Object.class))).thenReturn(predicate);
        lenient().when(cb.notEqual(any(Expression.class), any(Object.class))).thenReturn(predicate);
        lenient().when(cb.like(any(Expression.class), any(String.class))).thenReturn(predicate);
        lenient().when(cb.greaterThan(any(Expression.class), any(Comparable.class))).thenReturn(predicate);
        lenient().when(cb.greaterThanOrEqualTo(any(Expression.class), any(Comparable.class))).thenReturn(predicate);
        lenient().when(cb.lessThan(any(Expression.class), any(Comparable.class))).thenReturn(predicate);
        lenient().when(cb.lessThanOrEqualTo(any(Expression.class), any(Comparable.class))).thenReturn(predicate);
        lenient().when(cb.between(any(Expression.class), any(Comparable.class), any(Comparable.class))).thenReturn(predicate);
        lenient().when(cb.isNull(any(Expression.class))).thenReturn(predicate);
        lenient().when(cb.isNotNull(any(Expression.class))).thenReturn(predicate);
        lenient().when(path.in(any(java.util.Collection.class))).thenReturn(predicate);
    }

    private Predicate toPredicate(Specification<User> spec) {
        return spec.toPredicate((Root) root, (CriteriaQuery) query, cb);
    }

    @Test
    @DisplayName("of() returns a non-null builder")
    void testOf() {
        assertNotNull(SpecificationBuilder.of(User.class));
    }

    @Test
    @DisplayName("equal builds cb.equal predicate")
    void testEqual() {
        Specification<User> spec = SpecificationBuilder.of(User.class).equal("status", "ACTIVE").build();
        toPredicate(spec);
        verify(cb).equal(path, "ACTIVE");
    }

    @Test
    @DisplayName("notEqual builds cb.notEqual predicate")
    void testNotEqual() {
        Specification<User> spec = SpecificationBuilder.of(User.class).notEqual("status", "X").build();
        toPredicate(spec);
        verify(cb).notEqual(path, "X");
    }

    @Test
    @DisplayName("like builds cb.like predicate")
    void testLike() {
        Specification<User> spec = SpecificationBuilder.of(User.class).like("name", "%jo%").build();
        toPredicate(spec);
        verify(cb).like(path, "%jo%");
    }

    @Test
    @DisplayName("likeIgnoreCase lowercases field and pattern")
    void testLikeIgnoreCase() {
        Specification<User> spec = SpecificationBuilder.of(User.class).likeIgnoreCase("name", "%JOHN%").build();
        toPredicate(spec);
        verify(cb).lower(path);
        verify(cb).like(lowerExpr, "%john%");
    }

    @Test
    @DisplayName("greaterThan builds cb.greaterThan predicate")
    void testGreaterThan() {
        Specification<User> spec = SpecificationBuilder.of(User.class).greaterThan("age", 18).build();
        toPredicate(spec);
        verify(cb).greaterThan(path, 18);
    }

    @Test
    @DisplayName("greaterThanOrEqual builds cb.greaterThanOrEqualTo predicate")
    void testGreaterThanOrEqual() {
        Specification<User> spec = SpecificationBuilder.of(User.class).greaterThanOrEqual("age", 18).build();
        toPredicate(spec);
        verify(cb).greaterThanOrEqualTo(path, 18);
    }

    @Test
    @DisplayName("lessThan builds cb.lessThan predicate")
    void testLessThan() {
        Specification<User> spec = SpecificationBuilder.of(User.class).lessThan("age", 65).build();
        toPredicate(spec);
        verify(cb).lessThan(path, 65);
    }

    @Test
    @DisplayName("lessThanOrEqual builds cb.lessThanOrEqualTo predicate")
    void testLessThanOrEqual() {
        Specification<User> spec = SpecificationBuilder.of(User.class).lessThanOrEqual("age", 65).build();
        toPredicate(spec);
        verify(cb).lessThanOrEqualTo(path, 65);
    }

    @Test
    @DisplayName("between builds cb.between predicate")
    void testBetween() {
        Specification<User> spec = SpecificationBuilder.of(User.class).between("age", 18, 65).build();
        toPredicate(spec);
        verify(cb).between(path, 18, 65);
    }

    @Test
    @DisplayName("in builds path.in predicate")
    void testIn() {
        List<String> roles = List.of("ADMIN", "USER");
        Specification<User> spec = SpecificationBuilder.of(User.class).in("role", roles).build();
        toPredicate(spec);
        verify(path).in(roles);
    }

    @Test
    @DisplayName("isNull builds cb.isNull predicate")
    void testIsNull() {
        Specification<User> spec = SpecificationBuilder.of(User.class).isNull("deletedAt").build();
        toPredicate(spec);
        verify(cb).isNull(path);
    }

    @Test
    @DisplayName("isNotNull builds cb.isNotNull predicate")
    void testIsNotNull() {
        Specification<User> spec = SpecificationBuilder.of(User.class).isNotNull("deletedAt").build();
        toPredicate(spec);
        verify(cb).isNotNull(path);
    }

    @Test
    @DisplayName("where adds a custom specification")
    void testWhereCustomSpec() {
        Specification<User> custom = (r, q, c) -> c.equal(r.get("flag"), true);
        when(cb.equal(any(Expression.class), eq(true))).thenReturn(predicate);
        Specification<User> spec = SpecificationBuilder.of(User.class).where(custom).build();
        toPredicate(spec);
        verify(cb).equal(path, true);
    }

    @Test
    @DisplayName("build with no predicates returns a null-wrapping specification")
    void testBuildEmpty() {
        Specification<User> spec = SpecificationBuilder.of(User.class).build();
        assertNotNull(spec);
        // Empty builder wraps a null spec, which produces a null predicate.
        assertNull(toPredicate(spec));
    }

    @Test
    @DisplayName("build with multiple predicates ANDs them together")
    void testBuildMultiple() {
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        Specification<User> spec = SpecificationBuilder.of(User.class)
                .equal("status", "ACTIVE")
                .like("name", "%jo%")
                .greaterThan("age", 18)
                .build();
        Predicate result = toPredicate(spec);
        assertSame(predicate, result);
        verify(cb).equal(path, "ACTIVE");
        verify(cb).like(path, "%jo%");
        verify(cb).greaterThan(path, 18);
    }
}
