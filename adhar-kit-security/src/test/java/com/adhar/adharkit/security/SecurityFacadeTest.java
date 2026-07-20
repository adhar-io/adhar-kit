package com.adhar.adharkit.security;

import com.adhar.kit.security.SecurityFacade;
import com.adhar.kit.security.api.SecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SecurityFacade}.
 */
class SecurityFacadeTest {

    private final SecurityFacade facade = SecurityFacade.getInstance();

    @BeforeEach
    @AfterEach
    void resetDelegate() {
        // The facade is a JVM-wide singleton; other tests (e.g. auto-configuration
        // context tests) may have registered a delegate. Clear it so the stub
        // fallback behavior below is deterministic.
        facade.clearDelegate();
    }

    @Test
    void getInstanceReturnsSingleton() {
        SecurityFacade first = SecurityFacade.getInstance();
        SecurityFacade second = SecurityFacade.getInstance();
        assertThat(first).isNotNull().isSameAs(second);
    }

    @Test
    void implementsSecurityService() {
        assertThat(facade).isInstanceOf(SecurityService.class);
    }

    @Test
    void currentUserAccessorsReturnDefaults() {
        assertThat(facade.getCurrentUserId()).isNull();
        assertThat(facade.getCurrentUsername()).isNull();
        assertThat(facade.getCurrentUserRoles()).isNotNull().isEmpty();
    }

    @Test
    void roleChecksReturnFalseByDefault() {
        assertThat(facade.hasRole("ADMIN")).isFalse();
        assertThat(facade.hasPermission("order:create")).isFalse();
    }

    @Test
    void hasAnyRoleReturnsFalseWhenNoRoleMatches() {
        assertThat(facade.hasAnyRole("ADMIN", "USER")).isFalse();
        assertThat(facade.hasAnyRole()).isFalse();
    }

    @Test
    void hasAllRolesReturnsTrueForEmptyAndFalseWhenAnyMissing() {
        // No roles requested -> vacuously true.
        assertThat(facade.hasAllRoles()).isTrue();
        // Default implementation always reports the role as absent.
        assertThat(facade.hasAllRoles("ADMIN")).isFalse();
        assertThat(facade.hasAllRoles("ADMIN", "USER")).isFalse();
    }

    @Test
    void generateTokenWithRolesDelegatesToClaimsOverload() {
        String token = facade.generateToken("user-1", Set.of("USER"));
        assertThat(token).isEqualTo("token-user-1");
    }

    @Test
    void generateTokenWithClaimsReturnsStubToken() {
        String token = facade.generateToken("user-2", Set.of("USER"), Map.of("k", "v"));
        assertThat(token).isEqualTo("token-user-2");
    }

    @Test
    void validateAndExtractReturnDefaults() {
        assertThat(facade.validateToken("anything")).isFalse();
        assertThat(facade.extractUserId("anything")).isNull();
    }

    @Test
    void passwordHelpersReturnDefaults() {
        assertThat(facade.encodePassword("secret")).isEqualTo("encoded-secret");
        assertThat(facade.verifyPassword("secret", "encoded-secret")).isFalse();
    }

    @Test
    void isAuthenticatedReturnsFalseByDefault() {
        assertThat(facade.isAuthenticated()).isFalse();
    }

    @Test
    void delegatesAllCallsWhenDelegateRegistered() {
        SecurityService delegate = Mockito.mock(SecurityService.class);
        when(delegate.getCurrentUserId()).thenReturn("id-1");
        when(delegate.getCurrentUsername()).thenReturn("alice");
        when(delegate.getCurrentUserRoles()).thenReturn(Set.of("ADMIN"));
        when(delegate.hasRole("ADMIN")).thenReturn(true);
        when(delegate.hasAnyRole("A", "B")).thenReturn(true);
        when(delegate.hasAllRoles("A", "B")).thenReturn(true);
        when(delegate.hasPermission("order:create")).thenReturn(true);
        when(delegate.generateToken("u", Set.of("R"))).thenReturn("jwt-2arg");
        when(delegate.generateToken("u", Set.of("R"), Map.of("k", "v"))).thenReturn("jwt-3arg");
        when(delegate.validateToken("jwt")).thenReturn(true);
        when(delegate.extractUserId("jwt")).thenReturn("id-1");
        when(delegate.encodePassword("pw")).thenReturn("hashed");
        when(delegate.verifyPassword("pw", "hashed")).thenReturn(true);
        when(delegate.isAuthenticated()).thenReturn(true);

        facade.setDelegate(delegate);

        assertThat(facade.hasDelegate()).isTrue();
        assertThat(facade.getCurrentUserId()).isEqualTo("id-1");
        assertThat(facade.getCurrentUsername()).isEqualTo("alice");
        assertThat(facade.getCurrentUserRoles()).containsExactly("ADMIN");
        assertThat(facade.hasRole("ADMIN")).isTrue();
        assertThat(facade.hasAnyRole("A", "B")).isTrue();
        assertThat(facade.hasAllRoles("A", "B")).isTrue();
        assertThat(facade.hasPermission("order:create")).isTrue();
        assertThat(facade.generateToken("u", Set.of("R"))).isEqualTo("jwt-2arg");
        assertThat(facade.generateToken("u", Set.of("R"), Map.of("k", "v"))).isEqualTo("jwt-3arg");
        assertThat(facade.validateToken("jwt")).isTrue();
        assertThat(facade.extractUserId("jwt")).isEqualTo("id-1");
        assertThat(facade.encodePassword("pw")).isEqualTo("hashed");
        assertThat(facade.verifyPassword("pw", "hashed")).isTrue();
        assertThat(facade.isAuthenticated()).isTrue();
        verify(delegate).getCurrentUserId();
    }

    @Test
    void clearDelegateRestoresStubBehavior() {
        SecurityService delegate = Mockito.mock(SecurityService.class);
        when(delegate.getCurrentUserId()).thenReturn("id-1");
        facade.setDelegate(delegate);
        assertThat(facade.getCurrentUserId()).isEqualTo("id-1");

        facade.clearDelegate();

        assertThat(facade.hasDelegate()).isFalse();
        assertThat(facade.getCurrentUserId()).isNull();
    }

    @Test
    void settingFacadeAsItsOwnDelegateIsIgnored() {
        facade.setDelegate(facade);

        assertThat(facade.hasDelegate()).isFalse();
        // Must not recurse infinitely.
        assertThat(facade.getCurrentUserId()).isNull();
    }
}
