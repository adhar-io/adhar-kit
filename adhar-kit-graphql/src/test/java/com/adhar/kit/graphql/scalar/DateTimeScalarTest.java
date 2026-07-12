package com.adhar.kit.graphql.scalar;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DateTimeScalar} and its internal coercing implementation.
 */
class DateTimeScalarTest {

    private static final LocalDateTime SAMPLE = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
    private static final String SAMPLE_ISO = "2024-01-15T10:30:00";

    @SuppressWarnings("unchecked")
    private Coercing<LocalDateTime, String> coercing() {
        return (Coercing<LocalDateTime, String>) DateTimeScalar.DATE_TIME.getCoercing();
    }

    private GraphQLContext ctx() {
        return GraphQLContext.newContext().build();
    }

    @Test
    @DisplayName("scalar type exposes expected name and description")
    void scalarMetadata() {
        assertThat(DateTimeScalar.DATE_TIME.getName()).isEqualTo("DateTime");
        assertThat(DateTimeScalar.DATE_TIME.getDescription()).contains("LocalDateTime");
    }

    // ---------------- serialize ----------------

    @Test
    @DisplayName("serialize formats a LocalDateTime to ISO string")
    void serializeLocalDateTime() {
        Object result = coercing().serialize(SAMPLE, ctx(), Locale.ENGLISH);
        assertThat(result).isEqualTo(SAMPLE_ISO);
    }

    @Test
    @DisplayName("serialize passes through a valid ISO string")
    void serializeValidString() {
        Object result = coercing().serialize(SAMPLE_ISO, ctx(), Locale.ENGLISH);
        assertThat(result).isEqualTo(SAMPLE_ISO);
    }

    @Test
    @DisplayName("serialize rejects an unparseable string")
    void serializeInvalidString() {
        assertThatThrownBy(() -> coercing().serialize("not-a-date", ctx(), Locale.ENGLISH))
                .isInstanceOf(CoercingSerializeException.class)
                .hasMessageContaining("Cannot serialize value 'not-a-date'");
    }

    @Test
    @DisplayName("serialize rejects an unsupported type")
    void serializeUnsupportedType() {
        assertThatThrownBy(() -> coercing().serialize(123, ctx(), Locale.ENGLISH))
                .isInstanceOf(CoercingSerializeException.class)
                .hasMessageContaining("Integer");
    }

    // ---------------- parseValue ----------------

    @Test
    @DisplayName("parseValue returns LocalDateTime unchanged")
    void parseValueLocalDateTime() {
        Object result = coercing().parseValue(SAMPLE, ctx(), Locale.ENGLISH);
        assertThat(result).isEqualTo(SAMPLE);
    }

    @Test
    @DisplayName("parseValue parses a valid ISO string")
    void parseValueValidString() {
        Object result = coercing().parseValue(SAMPLE_ISO, ctx(), Locale.ENGLISH);
        assertThat(result).isEqualTo(SAMPLE);
    }

    @Test
    @DisplayName("parseValue rejects an unparseable string")
    void parseValueInvalidString() {
        assertThatThrownBy(() -> coercing().parseValue("nope", ctx(), Locale.ENGLISH))
                .isInstanceOf(CoercingParseValueException.class)
                .hasMessageContaining("Cannot parse value 'nope'");
    }

    @Test
    @DisplayName("parseValue rejects an unsupported type")
    void parseValueUnsupportedType() {
        assertThatThrownBy(() -> coercing().parseValue(42, ctx(), Locale.ENGLISH))
                .isInstanceOf(CoercingParseValueException.class)
                .hasMessageContaining("Integer");
    }

    // ---------------- parseLiteral ----------------

    @Test
    @DisplayName("parseLiteral parses a StringValue literal")
    void parseLiteralValid() {
        StringValue literal = StringValue.newStringValue(SAMPLE_ISO).build();
        Object result = coercing().parseLiteral(literal, CoercedVariables.emptyVariables(), ctx(), Locale.ENGLISH);
        assertThat(result).isEqualTo(SAMPLE);
    }

    @Test
    @DisplayName("parseLiteral rejects a StringValue with bad content")
    void parseLiteralInvalidContent() {
        StringValue literal = StringValue.newStringValue("bad").build();
        assertThatThrownBy(() ->
                coercing().parseLiteral(literal, CoercedVariables.emptyVariables(), ctx(), Locale.ENGLISH))
                .isInstanceOf(CoercingParseLiteralException.class)
                .hasMessageContaining("Cannot parse literal 'bad'");
    }

    @Test
    @DisplayName("parseLiteral rejects a non-string AST literal")
    void parseLiteralNonString() {
        IntValue literal = IntValue.newIntValue(BigInteger.ONE).build();
        assertThatThrownBy(() ->
                coercing().parseLiteral(literal, CoercedVariables.emptyVariables(), ctx(), Locale.ENGLISH))
                .isInstanceOf(CoercingParseLiteralException.class)
                .hasMessageContaining("IntValue");
    }
}
