package com.adhar.kit.security.api;

import java.util.Set;

/**
 * Framework-agnostic Security Service API.
 *
 * <p>This interface provides a unified security abstraction for authentication
 * and authorization across Spring Boot, Quarkus, and Micronaut frameworks.</p>
 *
 * <p><b>Supported Features:</b></p>
 * <ul>
 *   <li>JWT token generation and validation</li>
 *   <li>OAuth2/OIDC integration</li>
 *   <li>Role-based access control (RBAC)</li>
 *   <li>Permission-based authorization</li>
 *   <li>Current user context</li>
 *   <li>Password encoding</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * SecurityService security = SecurityFacade.getInstance();
 *
 * // Get current user
 * String userId = security.getCurrentUserId();
 * String username = security.getCurrentUsername();
 *
 * // Check authorization
 * if (security.hasRole("ADMIN")) {
 *     // Admin operation
 * }
 *
 * if (security.hasPermission("order:create")) {
 *     // Create order
 * }
 *
 * // Generate JWT
 * String token = security.generateToken(userId, roles);
 *
 * // Validate JWT
 * boolean valid = security.validateToken(token);
 *
 * // Encode password
 * String encoded = security.encodePassword("password123");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see com.adhar.kit.security.SecurityFacade
 */
public interface SecurityService {

    /**
     * Gets the current authenticated user ID.
     *
     * @return user ID or null if not authenticated
     */
    String getCurrentUserId();

    /**
     * Gets the current authenticated username.
     *
     * @return username or null if not authenticated
     */
    String getCurrentUsername();

    /**
     * Gets all roles for the current user.
     *
     * @return set of roles
     */
    Set<String> getCurrentUserRoles();

    /**
     * Checks if the current user has a specific role.
     *
     * @param role the role to check
     * @return true if user has role, false otherwise
     */
    boolean hasRole(String role);

    /**
     * Checks if the current user has any of the specified roles.
     *
     * @param roles the roles to check
     * @return true if user has any role, false otherwise
     */
    boolean hasAnyRole(String... roles);

    /**
     * Checks if the current user has all of the specified roles.
     *
     * @param roles the roles to check
     * @return true if user has all roles, false otherwise
     */
    boolean hasAllRoles(String... roles);

    /**
     * Checks if the current user has a specific permission.
     *
     * @param permission the permission to check (e.g., "order:create")
     * @return true if user has permission, false otherwise
     */
    boolean hasPermission(String permission);

    /**
     * Generates a JWT token.
     *
     * @param userId the user ID
     * @param roles the user roles
     * @return JWT token string
     */
    String generateToken(String userId, Set<String> roles);

    /**
     * Generates a JWT token with custom claims.
     *
     * @param userId the user ID
     * @param roles the user roles
     * @param claims additional claims
     * @return JWT token string
     */
    String generateToken(String userId, Set<String> roles, java.util.Map<String, Object> claims);

    /**
     * Validates a JWT token.
     *
     * @param token the token to validate
     * @return true if valid, false otherwise
     */
    boolean validateToken(String token);

    /**
     * Extracts user ID from a token.
     *
     * @param token the JWT token
     * @return user ID
     */
    String extractUserId(String token);

    /**
     * Encodes a password using the configured encoder.
     *
     * @param rawPassword the raw password
     * @return encoded password
     */
    String encodePassword(String rawPassword);

    /**
     * Verifies a raw password against an encoded password.
     *
     * @param rawPassword the raw password
     * @param encodedPassword the encoded password
     * @return true if passwords match, false otherwise
     */
    boolean verifyPassword(String rawPassword, String encodedPassword);

    /**
     * Checks if a user is authenticated.
     *
     * @return true if authenticated, false otherwise
     */
    boolean isAuthenticated();
}

