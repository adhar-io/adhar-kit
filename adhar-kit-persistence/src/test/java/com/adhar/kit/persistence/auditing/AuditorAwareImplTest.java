package com.adhar.kit.persistence.auditing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AuditorAwareImpl Tests")
class AuditorAwareImplTest {

    private AuditorAwareImpl auditorAware;

    @BeforeEach
    void setUp() {
        auditorAware = new AuditorAwareImpl();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should return 'system' when authentication is null")
    void testGetCurrentAuditorNoAuthentication() {
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(context);

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertTrue(auditor.isPresent());
        assertEquals("system", auditor.get());
    }

    @Test
    @DisplayName("Should return 'system' when not authenticated")
    void testGetCurrentAuditorNotAuthenticated() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertTrue(auditor.isPresent());
        assertEquals("system", auditor.get());
    }

    @Test
    @DisplayName("Should return username when authenticated")
    void testGetCurrentAuditorAuthenticated() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            "testUser", "password", Collections.emptyList());

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertTrue(auditor.isPresent());
        assertEquals("testUser", auditor.get());
    }

    @Test
    @DisplayName("Should return authentication name")
    void testGetCurrentAuditorWithCustomName() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("customUser@example.com");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertTrue(auditor.isPresent());
        assertEquals("customUser@example.com", auditor.get());
    }

    @Test
    @DisplayName("Should return 'system' with empty security context")
    void testGetCurrentAuditorEmptyContext() {
        // Default empty context
        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertTrue(auditor.isPresent());
        assertEquals("system", auditor.get());
    }
}
