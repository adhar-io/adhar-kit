package com.adhar.kit.test.base;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BaseUnitTest functionality.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("BaseUnitTest Tests")
class BaseUnitTestTest extends BaseUnitTest {

    @Mock
    private List<String> mockList;

    @Test
    @DisplayName("Should support Mockito mocking")
    void testMockitoSupport() {
        // Given
        when(mockList.size()).thenReturn(5);

        // When
        int size = mockList.size();

        // Then
        assertEquals(5, size);
        verify(mockList, times(1)).size();
    }

    @Test
    @DisplayName("Should allow mock interactions")
    void testMockInteractions() {
        // When
        mockList.add("test");
        mockList.add("item");

        // Then
        verify(mockList, times(2)).add(anyString());
        verify(mockList).add("test");
        verify(mockList).add("item");
    }

    @Test
    @DisplayName("Should support mock stubbing")
    void testMockStubbing() {
        // Given
        when(mockList.get(0)).thenReturn("first");
        when(mockList.get(1)).thenReturn("second");

        // When/Then
        assertEquals("first", mockList.get(0));
        assertEquals("second", mockList.get(1));
    }

    @Test
    @DisplayName("Should verify no interactions when expected")
    void testVerifyNoInteractions() {
        // When - no calls made

        // Then
        verifyNoInteractions(mockList);
    }

    @Test
    @DisplayName("Should support argument matchers")
    void testArgumentMatchers() {
        // When
        mockList.add("test");

        // Then
        verify(mockList).add(eq("test"));
        verify(mockList).add(argThat(s -> s.startsWith("t")));
    }

    @Test
    @DisplayName("Should allow multiple mocks in same test")
    void testMultipleMocks() {
        // Given
        List<Integer> mockIntList = mock(List.class);
        when(mockIntList.size()).thenReturn(10);
        when(mockList.size()).thenReturn(5);

        // When/Then
        assertEquals(10, mockIntList.size());
        assertEquals(5, mockList.size());
    }
}

