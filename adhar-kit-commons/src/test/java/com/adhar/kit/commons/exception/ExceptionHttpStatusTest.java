package com.adhar.kit.commons.exception;

import com.adhar.kit.commons.idempotency.DuplicateRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionHttpStatusTest {

    @Test
    void baseException_shouldDefaultTo500() {
        assertThat(new AdharException("boom").getHttpStatus()).isEqualTo(500);
    }

    @Test
    void validationException_shouldMapTo400() {
        assertThat(new ValidationException("invalid").getHttpStatus()).isEqualTo(400);
    }

    @Test
    void resourceNotFoundException_shouldMapTo404() {
        assertThat(new ResourceNotFoundException("missing").getHttpStatus()).isEqualTo(404);
    }

    @Test
    void businessException_shouldMapTo422() {
        assertThat(new BusinessException("BIZ", "rule violated").getHttpStatus()).isEqualTo(422);
    }

    @Test
    void serviceException_shouldInherit500() {
        assertThat(new ServiceException("failed").getHttpStatus()).isEqualTo(500);
    }

    @Test
    void integrationException_shouldMapTo502() {
        assertThat(new IntegrationException("INT", "gateway down").getHttpStatus()).isEqualTo(502);
    }

    @Test
    void duplicateRequestException_shouldMapTo409() {
        DuplicateRequestException ex = new DuplicateRequestException("key-1");
        assertThat(ex.getHttpStatus()).isEqualTo(409);
        assertThat(ex.getErrorCode()).isEqualTo("DUPLICATE_REQUEST");
        assertThat(ex.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(ex.getMessage()).contains("key-1");
    }
}
