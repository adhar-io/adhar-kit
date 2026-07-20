package com.adhar.kit.commons.web;

import com.adhar.kit.commons.constant.CommonConstants;
import com.adhar.kit.commons.context.CorrelationContext;
import com.adhar.kit.commons.exception.AdharException;
import com.adhar.kit.commons.exception.BusinessException;
import com.adhar.kit.commons.exception.IntegrationException;
import com.adhar.kit.commons.exception.ResourceNotFoundException;
import com.adhar.kit.commons.exception.ServiceException;
import com.adhar.kit.commons.exception.ValidationException;
import com.adhar.kit.commons.idempotency.DuplicateRequestException;
import com.adhar.kit.commons.model.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringGlobalExceptionHandlerTest {

    private final SpringGlobalExceptionHandler handler = new SpringGlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/1");

    @AfterEach
    void cleanup() {
        CorrelationContext.clear();
    }

    @Test
    void validationException_shouldMapTo400WithErrors() {
        ValidationException ex = new ValidationException("invalid",
            Map.of("username", "must not be empty"));
        ResponseEntity<ErrorResponse> response = handler.onValidationException(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo(CommonConstants.ERROR_VALIDATION);
        assertThat(response.getBody().getFieldErrors()).containsEntry("username", "must not be empty");
        assertThat(response.getBody().getPath()).isEqualTo("/api/orders/1");
    }

    @Test
    void validationException_withValidationErrorList_shouldIncludeList() {
        ValidationException ex = new ValidationException("invalid", List.of("e1", "e2"));
        ResponseEntity<ErrorResponse> response = handler.onValidationException(ex, request);
        assertThat(response.getBody().getValidationErrors()).containsExactly("e1", "e2");
    }

    @Test
    void resourceNotFound_shouldMapTo404() {
        ResponseEntity<ErrorResponse> response =
            handler.onResourceNotFound(new ResourceNotFoundException("User", 42), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo(CommonConstants.ERROR_RESOURCE_NOT_FOUND);
        assertThat(response.getBody().getMessage()).contains("42");
    }

    @Test
    void businessException_shouldMapTo422() {
        ResponseEntity<ErrorResponse> response =
            handler.onBusinessException(new BusinessException("BIZ001", "rule violated"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().getCode()).isEqualTo("BIZ001");
    }

    @Test
    void serviceException_shouldMapTo500() {
        ResponseEntity<ErrorResponse> response =
            handler.onServiceException(new ServiceException("failed"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getCode()).isEqualTo(CommonConstants.ERROR_SERVICE);
    }

    @Test
    void integrationException_shouldMapTo502() {
        ResponseEntity<ErrorResponse> response =
            handler.onIntegrationException(IntegrationException.serviceUnavailable("Kafka"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().getCode()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void adharExceptionFallback_shouldUseExceptionHttpStatus() {
        ResponseEntity<ErrorResponse> response =
            handler.onAdharException(new DuplicateRequestException("key-1"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo("DUPLICATE_REQUEST");
    }

    @Test
    void adharExceptionWithoutCode_shouldUseDefaultCodeAnd500() {
        ResponseEntity<ErrorResponse> response =
            handler.onAdharException(new AdharException("boom"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getCode()).isEqualTo("ADHAR_ERROR");
    }

    @Test
    void methodArgumentNotValid_shouldMapTo400WithFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "username", "must not be empty"));
        bindingResult.addError(new FieldError("request", "email", "invalid format"));
        MethodParameter parameter = new MethodParameter(
            getClass().getDeclaredMethod("sampleHandlerMethod", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.onMethodArgumentNotValid(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo(CommonConstants.ERROR_VALIDATION);
        assertThat(response.getBody().getFieldErrors())
            .containsEntry("username", "must not be empty")
            .containsEntry("email", "invalid format");
    }

    @Test
    void constraintViolation_shouldMapTo400WithFieldErrors() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(new StubPath("createUser.username"));
        when(violation.getMessage()).thenReturn("must not be null or empty");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ErrorResponse> response = handler.onConstraintViolation(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getFieldErrors())
            .containsEntry("createUser.username", "must not be null or empty");
    }

    @Test
    void genericException_shouldMapTo500WithoutLeakingMessage() {
        ResponseEntity<ErrorResponse> response =
            handler.onGenericException(new IllegalStateException("internal detail"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getCode()).isEqualTo(CommonConstants.ERROR_INTERNAL_SERVER);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getDetails()).isEqualTo("internal detail");
    }

    @Test
    void enrich_shouldPreferCorrelationContextRequestId() {
        CorrelationContext.setRequestId("ctx-req-1");
        ResponseEntity<ErrorResponse> response =
            handler.onServiceException(new ServiceException("failed"), request);
        assertThat(response.getBody().getRequestId()).isEqualTo("ctx-req-1");
    }

    @Test
    void enrich_shouldFallBackToRequestHeader() {
        request.addHeader(CommonConstants.HEADER_REQUEST_ID, "hdr-req-1");
        ResponseEntity<ErrorResponse> response =
            handler.onServiceException(new ServiceException("failed"), request);
        assertThat(response.getBody().getRequestId()).isEqualTo("hdr-req-1");
    }

    @Test
    void enrich_shouldTolerateNullRequest() {
        ResponseEntity<ErrorResponse> response =
            handler.onServiceException(new ServiceException("failed"), null);
        assertThat(response.getBody().getPath()).isNull();
        assertThat(response.getBody().getRequestId()).isNull();
    }

    /** Target for building a MethodParameter in the @Valid test. */
    @SuppressWarnings("unused")
    void sampleHandlerMethod(String body) {
        // no-op
    }

    /** Minimal Path implementation (Mockito cannot stub toString()). */
    private record StubPath(String value) implements Path {

        @Override
        public Iterator<Node> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
