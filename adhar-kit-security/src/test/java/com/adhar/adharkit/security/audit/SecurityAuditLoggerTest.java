package com.adhar.adharkit.security.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.adhar.kit.security.audit.SecurityAuditLogger;
import com.adhar.kit.security.audit.SecurityAuditLogger.SecurityEventType;
import com.adhar.kit.security.properties.AdharSecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SecurityAuditLogger}.
 */
class SecurityAuditLoggerTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger auditLogger;

    private AdharSecurityProperties.AuditProperties config(boolean enabled) {
        AdharSecurityProperties.AuditProperties props = new AdharSecurityProperties.AuditProperties();
        props.setEnabled(enabled);
        props.setLogSuccessfulAuth(true);
        props.setLogFailedAuth(true);
        props.setLogLogout(true);
        return props;
    }

    @BeforeEach
    void setUp() {
        auditLogger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
    }

    private Authentication authWithDetails() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            "alice@example.com", "secret", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.setSession(new MockHttpSession(null, "SESSION1234567890"));
        auth.setDetails(new WebAuthenticationDetails(request));
        return auth;
    }

    private String lastMessage() {
        assertThat(appender.list).isNotEmpty();
        return appender.list.get(appender.list.size() - 1).getFormattedMessage();
    }

    @Test
    void logsAuthenticationSuccess() {
        SecurityAuditLogger logger = new SecurityAuditLogger(config(true));

        logger.onAuthenticationSuccess(new AuthenticationSuccessEvent(authWithDetails()));

        String msg = lastMessage();
        assertThat(msg).contains("\"event\":\"AUTHENTICATION_SUCCESS\"");
        assertThat(msg).contains("\"principal\":\"alice@example.com\"");
        assertThat(msg).contains("\"ipAddress\":\"192.168.1.100\"");
        assertThat(msg).contains("\"sessionId\":\"SESSION1***\"");
        assertThat(msg).contains("ROLE_USER");
        assertThat(appender.list.get(appender.list.size() - 1).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void logsInteractiveAuthenticationSuccess() {
        SecurityAuditLogger logger = new SecurityAuditLogger(config(true));

        logger.onInteractiveAuthenticationSuccess(
            new InteractiveAuthenticationSuccessEvent(authWithDetails(), getClass()));

        assertThat(lastMessage()).contains("\"event\":\"AUTHENTICATION_SUCCESS\"");
    }

    @Test
    void logsBadCredentialsFailureAsWarning() {
        SecurityAuditLogger logger = new SecurityAuditLogger(config(true));

        logger.onAuthenticationFailureBadCredentials(new AuthenticationFailureBadCredentialsEvent(
            authWithDetails(), new BadCredentialsException("Bad \"creds\"")));

        ILoggingEvent event = appender.list.get(appender.list.size() - 1);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        String msg = event.getFormattedMessage();
        assertThat(msg).contains("\"event\":\"AUTHENTICATION_FAILURE_BAD_CREDENTIALS\"");
        assertThat(msg).contains("bad_credentials");
        // JSON escaping of the embedded quotes in the error message.
        assertThat(msg).contains("Bad \\\"creds\\\"");
    }

    @Test
    void logsLockedFailureAsWarning() {
        SecurityAuditLogger logger = new SecurityAuditLogger(config(true));

        logger.onAuthenticationFailureLocked(new AuthenticationFailureLockedEvent(
            authWithDetails(), new LockedException("Account locked")));

        ILoggingEvent event = appender.list.get(appender.list.size() - 1);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("account_locked");
    }

    @Test
    void logsLogoutSuccess() {
        SecurityAuditLogger logger = new SecurityAuditLogger(config(true));

        logger.onLogoutSuccess(new LogoutSuccessEvent(authWithDetails()));

        assertThat(lastMessage()).contains("\"event\":\"LOGOUT_SUCCESS\"");
    }

    @Test
    void doesNotLogWhenAuditDisabled() {
        SecurityAuditLogger logger = new SecurityAuditLogger(config(false));

        logger.onAuthenticationSuccess(new AuthenticationSuccessEvent(authWithDetails()));
        logger.onAuthenticationFailureBadCredentials(new AuthenticationFailureBadCredentialsEvent(
            authWithDetails(), new BadCredentialsException("x")));
        logger.onLogoutSuccess(new LogoutSuccessEvent(authWithDetails()));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void doesNotLogSuccessWhenSuccessLoggingDisabled() {
        AdharSecurityProperties.AuditProperties props = config(true);
        props.setLogSuccessfulAuth(false);
        SecurityAuditLogger logger = new SecurityAuditLogger(props);

        logger.onAuthenticationSuccess(new AuthenticationSuccessEvent(authWithDetails()));
        logger.onInteractiveAuthenticationSuccess(
            new InteractiveAuthenticationSuccessEvent(authWithDetails(), getClass()));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void doesNotLogFailureWhenFailureLoggingDisabled() {
        AdharSecurityProperties.AuditProperties props = config(true);
        props.setLogFailedAuth(false);
        SecurityAuditLogger logger = new SecurityAuditLogger(props);

        logger.onAuthenticationFailureBadCredentials(new AuthenticationFailureBadCredentialsEvent(
            authWithDetails(), new BadCredentialsException("x")));
        logger.onAuthenticationFailureLocked(new AuthenticationFailureLockedEvent(
            authWithDetails(), new LockedException("x")));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void doesNotLogLogoutWhenLogoutLoggingDisabled() {
        AdharSecurityProperties.AuditProperties props = config(true);
        props.setLogLogout(false);
        SecurityAuditLogger logger = new SecurityAuditLogger(props);

        logger.onLogoutSuccess(new LogoutSuccessEvent(authWithDetails()));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void handlesAnonymousPrincipal() {
        SecurityAuditLogger logger = new SecurityAuditLogger(config(true));
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(null);
        when(auth.getDetails()).thenReturn(null);
        when(auth.getAuthorities()).thenReturn(null);

        logger.onAuthenticationSuccess(new AuthenticationSuccessEvent(auth));

        assertThat(lastMessage()).contains("\"principal\":\"anonymous\"");
    }

    @Test
    void handlesNonStringPrincipalViaGetName() {
        SecurityAuditLogger logger = new SecurityAuditLogger(config(true));
        Object principal = new Object() {
            @Override
            public String toString() {
                return "principal-object";
            }
        };
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(principal, "creds", List.of());

        logger.onAuthenticationSuccess(new AuthenticationSuccessEvent(auth));

        assertThat(lastMessage()).contains("principal-object");
    }

    @Test
    void logsCustomEventWithStructuredDetails() {
        SecurityAuditLogger logger = new SecurityAuditLogger(config(true));
        Map<String, Object> details = new HashMap<>();
        details.put("count", 5);
        details.put("active", true);
        details.put("tags", List.of("a", "b"));
        details.put("nested", Map.of("k", "v"));
        details.put("missing", null);

        logger.logCustomEvent(SecurityEventType.SESSION_CREATED, "bob", "10.0.0.5", details);

        ILoggingEvent event = appender.list.get(appender.list.size() - 1);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        String msg = event.getFormattedMessage();
        assertThat(msg).contains("\"event\":\"SESSION_CREATED\"");
        assertThat(msg).contains("\"principal\":\"bob\"");
        assertThat(msg).contains("\"ipAddress\":\"10.0.0.5\"");
        assertThat(msg).contains("\"count\":5");
        assertThat(msg).contains("\"active\":true");
        assertThat(msg).contains("[\"a\",\"b\"]");
        assertThat(msg).contains("\"nested\":{\"k\":\"v\"}");
        assertThat(msg).contains("\"missing\":null");
    }

    @Test
    void logsCustomFailureEventAsWarning() {
        SecurityAuditLogger logger = new SecurityAuditLogger(config(true));

        logger.logCustomEvent(SecurityEventType.AUTHENTICATION_FAILURE_OTHER, "carol", "10.0.0.6", null);

        ILoggingEvent event = appender.list.get(appender.list.size() - 1);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("AUTHENTICATION_FAILURE_OTHER");
    }

    @Test
    void doesNotLogCustomEventWhenDisabled() {
        SecurityAuditLogger logger = new SecurityAuditLogger(config(false));

        logger.logCustomEvent(SecurityEventType.SESSION_DESTROYED, "x", "y", null);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void securityEventTypeEnumExposesValues() {
        assertThat(SecurityEventType.values()).contains(
            SecurityEventType.AUTHENTICATION_SUCCESS,
            SecurityEventType.LOGOUT_SUCCESS,
            SecurityEventType.SESSION_CREATED,
            SecurityEventType.SESSION_DESTROYED);
        assertThat(SecurityEventType.valueOf("AUTHENTICATION_FAILURE_LOCKED"))
            .isEqualTo(SecurityEventType.AUTHENTICATION_FAILURE_LOCKED);
    }
}
