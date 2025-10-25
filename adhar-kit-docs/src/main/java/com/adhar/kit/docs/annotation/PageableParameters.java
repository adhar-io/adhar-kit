package com.adhar.kit.docs.annotation;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import java.lang.annotation.*;

/**
 * Annotation for pagination parameters in API documentation.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Parameter(name = "page", description = "Page number (0-based)", in = ParameterIn.QUERY, example = "0")
@Parameter(name = "size", description = "Page size", in = ParameterIn.QUERY, example = "20")
@Parameter(name = "sort", description = "Sort parameters (field,direction)", in = ParameterIn.QUERY, example = "createdAt,desc")
public @interface PageableParameters {
}

