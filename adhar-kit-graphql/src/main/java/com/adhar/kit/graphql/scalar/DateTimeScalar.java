package com.adhar.kit.graphql.scalar;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Custom GraphQL scalar type for {@link LocalDateTime}.
 *
 * <p>Serializes and deserializes {@code LocalDateTime} values using ISO-8601 format
 * ({@code yyyy-MM-dd'T'HH:mm:ss}).</p>
 *
 * <p><b>Usage in GraphQL schema:</b></p>
 * <pre>{@code
 * scalar DateTime
 *
 * type Event {
 *   name: String!
 *   createdAt: DateTime!
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class DateTimeScalar {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private DateTimeScalar() {
        // Utility class
    }

    /**
     * The GraphQL {@code DateTime} scalar type backed by {@link LocalDateTime}.
     */
    public static final GraphQLScalarType DATE_TIME = GraphQLScalarType.newScalar()
            .name("DateTime")
            .description("A date-time scalar representing java.time.LocalDateTime in ISO-8601 format")
            .coercing(new DateTimeCoercing())
            .build();

    /**
     * Coercing implementation that converts between {@link LocalDateTime} and ISO-8601 strings.
     */
    private static final class DateTimeCoercing implements Coercing<LocalDateTime, String> {

        @Override
        public String serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale)
                throws CoercingSerializeException {
            return switch (dataFetcherResult) {
                case LocalDateTime ldt -> ldt.format(FORMATTER);
                case String s -> {
                    // Validate string is parseable, then return as-is
                    try {
                        LocalDateTime.parse(s, FORMATTER);
                        yield s;
                    } catch (DateTimeParseException e) {
                        throw new CoercingSerializeException(
                                "Cannot serialize value '%s' as DateTime: %s".formatted(s, e.getMessage()), e);
                    }
                }
                default -> throw new CoercingSerializeException(
                        "Cannot serialize value of type '%s' as DateTime".formatted(
                                dataFetcherResult.getClass().getSimpleName()));
            };
        }

        @Override
        public LocalDateTime parseValue(Object input, GraphQLContext graphQLContext, Locale locale)
                throws CoercingParseValueException {
            return switch (input) {
                case LocalDateTime ldt -> ldt;
                case String s -> {
                    try {
                        yield LocalDateTime.parse(s, FORMATTER);
                    } catch (DateTimeParseException e) {
                        throw new CoercingParseValueException(
                                "Cannot parse value '%s' as DateTime: %s".formatted(s, e.getMessage()), e);
                    }
                }
                default -> throw new CoercingParseValueException(
                        "Cannot parse value of type '%s' as DateTime".formatted(
                                input.getClass().getSimpleName()));
            };
        }

        @Override
        public LocalDateTime parseLiteral(Value<?> input, CoercedVariables variables,
                                          GraphQLContext graphQLContext, Locale locale)
                throws CoercingParseLiteralException {
            if (input instanceof StringValue stringValue) {
                try {
                    return LocalDateTime.parse(stringValue.getValue(), FORMATTER);
                } catch (DateTimeParseException e) {
                    throw new CoercingParseLiteralException(
                            "Cannot parse literal '%s' as DateTime: %s".formatted(
                                    stringValue.getValue(), e.getMessage()), e);
                }
            }
            throw new CoercingParseLiteralException(
                    "Cannot parse AST literal of type '%s' as DateTime".formatted(
                            input.getClass().getSimpleName()));
        }
    }
}
