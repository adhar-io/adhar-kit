package com.adhar.kit.config.refresh;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.context.refresh.ContextRefresher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ConfigRefreshManager Tests")
@ExtendWith(MockitoExtension.class)
class ConfigRefreshManagerTest {

    @Mock
    private ContextRefresher contextRefresher;

    @InjectMocks
    private ConfigRefreshManager configRefreshManager;

    @Test
    @DisplayName("Should auto-refresh configuration successfully")
    void testAutoRefreshSuccess() {
        when(contextRefresher.refresh()).thenReturn(java.util.Collections.emptySet());

        assertDoesNotThrow(() -> configRefreshManager.autoRefresh());

        verify(contextRefresher, times(1)).refresh();
    }

    @Test
    @DisplayName("Should handle auto-refresh failure gracefully")
    void testAutoRefreshFailure() {
        when(contextRefresher.refresh()).thenThrow(new RuntimeException("Refresh failed"));

        assertDoesNotThrow(() -> configRefreshManager.autoRefresh());

        verify(contextRefresher, times(1)).refresh();
    }

    @Test
    @DisplayName("Should manually refresh configuration successfully")
    void testManualRefreshSuccess() {
        when(contextRefresher.refresh()).thenReturn(java.util.Collections.emptySet());

        assertDoesNotThrow(() -> configRefreshManager.refresh());

        verify(contextRefresher, times(1)).refresh();
    }

    @Test
    @DisplayName("Should throw exception on manual refresh failure")
    void testManualRefreshFailure() {
        when(contextRefresher.refresh()).thenThrow(new RuntimeException("Refresh failed"));

        assertThrows(ConfigRefreshManager.ConfigRefreshException.class,
                     () -> configRefreshManager.refresh());

        verify(contextRefresher, times(1)).refresh();
    }

    @Test
    @DisplayName("Should test ConfigRefreshException")
    void testConfigRefreshException() {
        String message = "Test error";
        Throwable cause = new RuntimeException("Root cause");

        ConfigRefreshManager.ConfigRefreshException exception =
                new ConfigRefreshManager.ConfigRefreshException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}

