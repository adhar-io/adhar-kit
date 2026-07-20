package com.adhar.adharkit.security.aspect;

import com.adhar.kit.security.annotation.CheckMode;
import com.adhar.kit.security.annotation.RequiresPermission;
import com.adhar.kit.security.annotation.RequiresRole;
import com.adhar.kit.security.api.SecurityService;
import com.adhar.kit.security.aspect.AccessControlAspect;
import com.adhar.kit.security.exception.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AccessControlAspect}.
 */
class AccessControlAspectTest {

    private SecurityService securityService;

    /** Target with method-level annotations. */
    static class OrderService {

        @RequiresRole("ADMIN")
        public String deleteOrder() {
            return "deleted";
        }

        @RequiresRole(value = {"AUDITOR", "COMPLIANCE"}, mode = CheckMode.ALL_OF)
        public String auditOrders() {
            return "audited";
        }

        @RequiresRole(value = {"SUPPORT", "ADMIN"})
        public String viewOrder() {
            return "viewed";
        }

        @RequiresPermission("order:create")
        public String createOrder() {
            return "created";
        }

        @RequiresPermission(value = {"order:read", "order:export"}, mode = CheckMode.ALL_OF)
        public String exportOrders() {
            return "exported";
        }

        @RequiresPermission(value = {"order:read", "order:export"})
        public String previewExport() {
            return "previewed";
        }

        @RequiresRole("ADMIN")
        @RequiresPermission("order:purge")
        public String purgeOrders() {
            return "purged";
        }

        public String unannotated() {
            return "open";
        }
    }

    /** Target with a class-level annotation. */
    @RequiresRole("MANAGER")
    static class ReportingService {

        public String monthlyReport() {
            return "monthly";
        }

        @RequiresRole("DIRECTOR")
        public String boardReport() {
            return "board";
        }
    }

    @BeforeEach
    void setUp() {
        securityService = Mockito.mock(SecurityService.class);
    }

    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new AccessControlAspect(securityService));
        return factory.getProxy();
    }

    @Test
    void allowsWhenRequiredRolePresent() {
        when(securityService.hasAnyRole("ADMIN")).thenReturn(true);

        assertThat(proxy(new OrderService()).deleteOrder()).isEqualTo("deleted");
    }

    @Test
    void deniesWhenRequiredRoleMissing() {
        when(securityService.hasAnyRole("ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> proxy(new OrderService()).deleteOrder())
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("any")
            .hasMessageContaining("ADMIN");
    }

    @Test
    void anyOfRoleModeGrantsWithSingleMatch() {
        when(securityService.hasAnyRole("SUPPORT", "ADMIN")).thenReturn(true);

        assertThat(proxy(new OrderService()).viewOrder()).isEqualTo("viewed");
    }

    @Test
    void allOfRoleModeRequiresEveryRole() {
        when(securityService.hasAllRoles("AUDITOR", "COMPLIANCE")).thenReturn(false);

        assertThatThrownBy(() -> proxy(new OrderService()).auditOrders())
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("all")
            .hasMessageContaining("AUDITOR");

        when(securityService.hasAllRoles("AUDITOR", "COMPLIANCE")).thenReturn(true);
        assertThat(proxy(new OrderService()).auditOrders()).isEqualTo("audited");
    }

    @Test
    void allowsWhenRequiredPermissionPresent() {
        when(securityService.hasPermission("order:create")).thenReturn(true);

        assertThat(proxy(new OrderService()).createOrder()).isEqualTo("created");
    }

    @Test
    void deniesWhenRequiredPermissionMissing() {
        when(securityService.hasPermission("order:create")).thenReturn(false);

        assertThatThrownBy(() -> proxy(new OrderService()).createOrder())
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("order:create");
    }

    @Test
    void anyOfPermissionModeGrantsWithSingleMatch() {
        when(securityService.hasPermission("order:read")).thenReturn(false);
        when(securityService.hasPermission("order:export")).thenReturn(true);

        assertThat(proxy(new OrderService()).previewExport()).isEqualTo("previewed");
    }

    @Test
    void allOfPermissionModeRequiresEveryPermission() {
        when(securityService.hasPermission("order:read")).thenReturn(true);
        when(securityService.hasPermission("order:export")).thenReturn(false);

        assertThatThrownBy(() -> proxy(new OrderService()).exportOrders())
            .isInstanceOf(AccessDeniedException.class);

        when(securityService.hasPermission("order:export")).thenReturn(true);
        assertThat(proxy(new OrderService()).exportOrders()).isEqualTo("exported");
    }

    @Test
    void combinedRoleAndPermissionMustBothPass() {
        when(securityService.hasAnyRole("ADMIN")).thenReturn(true);
        when(securityService.hasPermission("order:purge")).thenReturn(false);

        assertThatThrownBy(() -> proxy(new OrderService()).purgeOrders())
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("order:purge");

        when(securityService.hasPermission("order:purge")).thenReturn(true);
        assertThat(proxy(new OrderService()).purgeOrders()).isEqualTo("purged");
    }

    @Test
    void unannotatedMethodIsNotIntercepted() {
        assertThat(proxy(new OrderService()).unannotated()).isEqualTo("open");
        Mockito.verifyNoInteractions(securityService);
    }

    @Test
    void classLevelAnnotationAppliesToAllMethods() {
        when(securityService.hasAnyRole("MANAGER")).thenReturn(false);

        assertThatThrownBy(() -> proxy(new ReportingService()).monthlyReport())
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("MANAGER");

        when(securityService.hasAnyRole("MANAGER")).thenReturn(true);
        assertThat(proxy(new ReportingService()).monthlyReport()).isEqualTo("monthly");
    }

    @Test
    void methodLevelAnnotationOverridesClassLevel() {
        when(securityService.hasAnyRole(any(String[].class))).thenAnswer(invocation ->
            Set.of(invocation.getArguments()).contains("DIRECTOR"));

        assertThat(proxy(new ReportingService()).boardReport()).isEqualTo("board");

        when(securityService.hasAnyRole(any(String[].class))).thenAnswer(invocation ->
            Set.of(invocation.getArguments()).contains("MANAGER"));

        // MANAGER alone is not enough for the DIRECTOR-only method.
        assertThatThrownBy(() -> proxy(new ReportingService()).boardReport())
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("DIRECTOR");
    }
}
