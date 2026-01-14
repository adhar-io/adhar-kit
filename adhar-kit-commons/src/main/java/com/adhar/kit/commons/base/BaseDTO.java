package com.adhar.kit.commons.base;

import lombok.Data;
import java.io.Serializable;

/**
 * Base class for all Data Transfer Objects (DTOs).
 *
 * <p>Provides common structure for DTOs used in API communication.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Data
 * @EqualsAndHashCode(callSuper = true)
 * public class UserDTO extends BaseDTO {
 *
 *     private String userId;
 *     private String username;
 *     private String email;
 *     private List<String> roles;
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
public abstract class BaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Validates the DTO.
     *
     * <p>Override this method to add custom validation logic.</p>
     *
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        // Default implementation - override in subclasses
    }

    /**
     * Checks if this DTO has all required fields.
     *
     * @return true if all required fields are present
     */
    public boolean isValid() {
        try {
            validate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

