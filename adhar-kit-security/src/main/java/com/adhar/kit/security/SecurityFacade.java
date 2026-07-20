package com.adhar.kit.security;

import com.adhar.kit.security.api.SecurityService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Universal Security facade for authentication and authorization.
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * SecurityFacade security = SecurityFacade.getInstance();
 *
 * // Authorization checks
 * @RestController
 * public class OrderController {
 *
 *     @PostMapping("/orders")
 *     public Order createOrder(@RequestBody OrderRequest request) {
 *         // Check permission
 *         if (!security.hasPermission("order:create")) {
 *             throw new ForbiddenException();
 *         }
 *
 *         // Get current user
 *         String userId = security.getCurrentUserId();
 *
 *         // Create order
 *         Order order = new Order();
 *         order.setUserId(userId);
 *         return orderService.create(order);
 *     }
 *
 *     @DeleteMapping("/orders/{id}")
 *     public void deleteOrder(@PathVariable Long id) {
 *         // Check role
 *         if (!security.hasRole("ADMIN")) {
 *             throw new ForbiddenException();
 *         }
 *         orderService.delete(id);
 *     }
 * }
 *
 * // JWT generation
 * Set<String> roles = Set.of("USER", "ORDER_MANAGER");
 * String token = security.generateToken(userId, roles);
 *
 * // Password handling
 * String encodedPassword = security.encodePassword(rawPassword);
 * boolean matches = security.verifyPassword(rawPassword, encodedPassword);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class SecurityFacade implements SecurityService {

    private static volatile SecurityFacade instance;

    /**
     * Framework-specific delegate (e.g. the Spring adapter). When set, all calls are
     * forwarded to it; otherwise the safe stub defaults below are used so the facade
     * remains usable in non-Spring environments.
     */
    private volatile SecurityService delegate;

    private SecurityFacade() {
        log.info("Initialized SecurityFacade");
    }

    public static SecurityFacade getInstance() {
        if (instance == null) {
            synchronized (SecurityFacade.class) {
                if (instance == null) {
                    instance = new SecurityFacade();
                }
            }
        }
        return instance;
    }

    /**
     * Registers a framework-specific {@link SecurityService} implementation that this
     * facade delegates to (e.g. {@code SpringSecurityAdapter}). Setting the facade
     * itself as delegate is ignored to avoid infinite recursion.
     *
     * @param delegate the delegate, or null to clear
     */
    public void setDelegate(SecurityService delegate) {
        if (delegate == this) {
            log.warn("Ignoring attempt to set SecurityFacade as its own delegate");
            return;
        }
        this.delegate = delegate;
        log.info("SecurityFacade delegate set to: {}", delegate != null ? delegate.getClass().getName() : "none");
    }

    /**
     * Clears the registered delegate, restoring stub fallback behavior.
     */
    public void clearDelegate() {
        this.delegate = null;
    }

    /**
     * Whether a framework-specific delegate is registered.
     *
     * @return true if delegation is active
     */
    public boolean hasDelegate() {
        return delegate != null;
    }

    @Override
    public String getCurrentUserId() {
        SecurityService current = delegate;
        if (current != null) {
            return current.getCurrentUserId();
        }
        log.debug("Getting current user ID (no delegate registered)");
        return null;
    }

    @Override
    public String getCurrentUsername() {
        SecurityService current = delegate;
        if (current != null) {
            return current.getCurrentUsername();
        }
        log.debug("Getting current username (no delegate registered)");
        return null;
    }

    @Override
    public Set<String> getCurrentUserRoles() {
        SecurityService current = delegate;
        if (current != null) {
            return current.getCurrentUserRoles();
        }
        log.debug("Getting current user roles (no delegate registered)");
        return new HashSet<>();
    }

    @Override
    public boolean hasRole(String role) {
        SecurityService current = delegate;
        if (current != null) {
            return current.hasRole(role);
        }
        log.debug("Checking if user has role: {} (no delegate registered)", role);
        return false;
    }

    @Override
    public boolean hasAnyRole(String... roles) {
        SecurityService current = delegate;
        if (current != null) {
            return current.hasAnyRole(roles);
        }
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasAllRoles(String... roles) {
        SecurityService current = delegate;
        if (current != null) {
            return current.hasAllRoles(roles);
        }
        for (String role : roles) {
            if (!hasRole(role)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasPermission(String permission) {
        SecurityService current = delegate;
        if (current != null) {
            return current.hasPermission(permission);
        }
        log.debug("Checking permission: {} (no delegate registered)", permission);
        return false;
    }

    @Override
    public String generateToken(String userId, Set<String> roles) {
        SecurityService current = delegate;
        if (current != null) {
            return current.generateToken(userId, roles);
        }
        return generateToken(userId, roles, Map.of());
    }

    @Override
    public String generateToken(String userId, Set<String> roles, Map<String, Object> claims) {
        SecurityService current = delegate;
        if (current != null) {
            return current.generateToken(userId, roles, claims);
        }
        log.debug("Generating token for user: {} (no delegate registered)", userId);
        return "token-" + userId;
    }

    @Override
    public boolean validateToken(String token) {
        SecurityService current = delegate;
        if (current != null) {
            return current.validateToken(token);
        }
        log.debug("Validating token (no delegate registered)");
        return false;
    }

    @Override
    public String extractUserId(String token) {
        SecurityService current = delegate;
        if (current != null) {
            return current.extractUserId(token);
        }
        log.debug("Extracting user ID from token (no delegate registered)");
        return null;
    }

    @Override
    public String encodePassword(String rawPassword) {
        SecurityService current = delegate;
        if (current != null) {
            return current.encodePassword(rawPassword);
        }
        log.debug("Encoding password (no delegate registered)");
        return "encoded-" + rawPassword;
    }

    @Override
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        SecurityService current = delegate;
        if (current != null) {
            return current.verifyPassword(rawPassword, encodedPassword);
        }
        log.debug("Verifying password (no delegate registered)");
        return false;
    }

    @Override
    public boolean isAuthenticated() {
        SecurityService current = delegate;
        if (current != null) {
            return current.isAuthenticated();
        }
        log.debug("Checking if user is authenticated (no delegate registered)");
        return false;
    }
}

