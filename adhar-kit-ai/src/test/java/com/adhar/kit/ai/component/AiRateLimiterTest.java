package com.adhar.kit.ai.component;

import com.adhar.kit.ai.config.AiProperties;
import com.adhar.kit.commons.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for {@link AiRateLimiter} sliding-window behaviour.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiRateLimiterTest {

    @Mock
    private AiProperties aiProperties;

    private AiProperties.RateLimiting rateLimiting;
    private AiRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiting = new AiProperties.RateLimiting();
        rateLimiting.setEnabled(true);
        rateLimiting.setRequestsPerMinute(2);
        rateLimiting.setRequestsPerHour(10);
        rateLimiting.setRequestsPerDay(100);
        lenient().when(aiProperties.getRateLimiting()).thenReturn(rateLimiting);
        rateLimiter = new AiRateLimiter(aiProperties);
    }

    @Test
    void allowsWhenRateLimitingDisabled() {
        rateLimiting.setEnabled(false);
        assertThat(rateLimiter.isAllowed("anyone")).isTrue();
        // recordRequest is a no-op when disabled
        rateLimiter.recordRequest("anyone");
        rateLimiter.checkRateLimit("anyone");
    }

    @Test
    void allowsRequestsUntilLimitReached() {
        assertThat(rateLimiter.isAllowed("user")).isTrue();
        rateLimiter.recordRequest("user");
        assertThat(rateLimiter.isAllowed("user")).isTrue();
        rateLimiter.recordRequest("user");
        // Now minute count == 2 == limit -> not allowed
        assertThat(rateLimiter.isAllowed("user")).isFalse();
    }

    @Test
    void getStatusReflectsRecordedRequests() {
        rateLimiter.isAllowed("u1");
        rateLimiter.recordRequest("u1");

        AiRateLimiter.RateLimitStatus status = rateLimiter.getStatus("u1");
        assertThat(status.minuteRequests()).isEqualTo(1);
        assertThat(status.hourRequests()).isEqualTo(1);
        assertThat(status.dayRequests()).isEqualTo(1);
        assertThat(status.lastReset()).isNotNull();
    }

    @Test
    void getStatusForUnknownIdentifierReturnsZeroes() {
        AiRateLimiter.RateLimitStatus status = rateLimiter.getStatus("unknown");
        assertThat(status.minuteRequests()).isZero();
        assertThat(status.hourRequests()).isZero();
        assertThat(status.dayRequests()).isZero();
    }

    @Test
    void checkRateLimitRecordsWhenAllowed() {
        rateLimiter.checkRateLimit("good-user");
        assertThat(rateLimiter.getStatus("good-user").minuteRequests()).isEqualTo(1);
    }

    @Test
    void checkRateLimitThrowsWhenExceeded() {
        rateLimiting.setRequestsPerMinute(0);
        assertThatThrownBy(() -> rateLimiter.checkRateLimit("blocked"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Rate limit exceeded");
    }
}
