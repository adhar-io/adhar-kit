package com.adhar.adharkit.security;

import com.adhar.kit.security.SecurityFacade;
import com.adhar.kit.security.api.SecurityService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SecurityFacade}.
 */
class SecurityFacadeTest {

    private final SecurityFacade facade = SecurityFacade.getInstance();

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
}
