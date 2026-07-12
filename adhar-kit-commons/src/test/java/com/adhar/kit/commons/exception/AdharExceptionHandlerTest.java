package com.adhar.kit.commons.exception;

import com.adhar.kit.commons.model.ErrorResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdharExceptionHandlerTest {

    /** Concrete handler exposing the protected handle methods. */
    static class TestHandler extends AdharExceptionHandler {
        ErrorResponse adhar(AdharException e) { return handleAdharException(e); }
        ErrorResponse validation(ValidationException e) { return handleValidationException(e); }
        ErrorResponse notFound(ResourceNotFoundException e) { return handleResourceNotFoundException(e); }
        ErrorResponse service(ServiceException e) { return handleServiceException(e); }
        ErrorResponse generic(Exception e) { return handleGenericException(e); }
        ErrorResponse illegalArg(IllegalArgumentException e) { return handleIllegalArgumentException(e); }
        ErrorResponse npe(NullPointerException e) { return handleNullPointerException(e); }
    }

    private final TestHandler handler = new TestHandler();

    @Test
    void handlesAdharExceptionWithErrorCode() {
        ErrorResponse r = handler.adhar(new AdharException("MY_CODE", "boom"));
        assertEquals("MY_CODE", r.getCode());
        assertEquals("boom", r.getMessage());
        assertNotNull(r.getTimestamp());
    }

    @Test
    void handlesAdharExceptionDefaultCodeWhenNull() {
        ErrorResponse r = handler.adhar(new AdharException("boom"));
        assertEquals("ADHAR_ERROR", r.getCode());
    }

    @Test
    void handlesValidationExceptionWithErrors() {
        ValidationException ex = new ValidationException("invalid", List.of("e1", "e2"));
        ErrorResponse r = handler.validation(ex);
        assertEquals("VALIDATION_ERROR", r.getCode());
        assertEquals(List.of("e1", "e2"), r.getValidationErrors());
        assertNull(r.getFieldErrors());
    }

    @Test
    void handlesValidationExceptionWithFieldErrors() {
        ValidationException ex = new ValidationException("invalid", Map.of("name", "required"));
        ErrorResponse r = handler.validation(ex);
        assertEquals(Map.of("name", "required"), r.getFieldErrors());
    }

    @Test
    void handlesResourceNotFound() {
        ErrorResponse r = handler.notFound(new ResourceNotFoundException("User", 42));
        assertEquals("RESOURCE_NOT_FOUND", r.getCode());
        assertTrue(r.getMessage().contains("42"));
    }

    @Test
    void handlesServiceException() {
        ErrorResponse r = handler.service(new ServiceException("failed"));
        assertEquals("SERVICE_ERROR", r.getCode());
        assertEquals("failed", r.getMessage());
    }

    @Test
    void handlesGenericException() {
        ErrorResponse r = handler.generic(new Exception("details here"));
        assertEquals("INTERNAL_SERVER_ERROR", r.getCode());
        assertEquals("An unexpected error occurred", r.getMessage());
        assertEquals("details here", r.getDetails());
    }

    @Test
    void handlesIllegalArgument() {
        ErrorResponse r = handler.illegalArg(new IllegalArgumentException("bad arg"));
        assertEquals("BAD_REQUEST", r.getCode());
        assertEquals("Invalid argument", r.getMessage());
        assertEquals("bad arg", r.getDetails());
    }

    @Test
    void handlesNullPointer() {
        ErrorResponse r = handler.npe(new NullPointerException("npe msg"));
        assertEquals("INTERNAL_SERVER_ERROR", r.getCode());
        assertEquals("Null value encountered", r.getMessage());
        assertEquals("npe msg", r.getDetails());
    }
}
