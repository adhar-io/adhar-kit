package com.adhar.kit.commons.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseDTOTest {

    static class PlainDTO extends BaseDTO {
    }

    static class StrictDTO extends BaseDTO {
        boolean failValidation;
        @Override
        public void validate() {
            if (failValidation) {
                throw new IllegalArgumentException("invalid");
            }
        }
    }

    @Test
    void defaultValidateDoesNotThrow() {
        assertDoesNotThrow(new PlainDTO()::validate);
    }

    @Test
    void isValidTrueWhenValidationPasses() {
        assertTrue(new PlainDTO().isValid());
        assertTrue(new StrictDTO().isValid());
    }

    @Test
    void isValidFalseWhenValidationThrows() {
        StrictDTO dto = new StrictDTO();
        dto.failValidation = true;
        assertFalse(dto.isValid());
        assertThrows(IllegalArgumentException.class, dto::validate);
    }
}
