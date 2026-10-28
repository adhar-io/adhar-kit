package com.adhar.kit.commons.web;

import com.adhar.kit.commons.context.CorrelationContext;
import com.adhar.kit.commons.model.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ProblemDetails}.
 */
class ProblemDetailsTest {

    @AfterEach
    void clear() {
        CorrelationContext.clear();
    }

    @Test
    void aProblemCarriesThePathAndTheRequestId() {
        CorrelationContext.setRequestId("req-1");
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, "no",
                new MockHttpServletRequest("GET", "/api/v1/pets"));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getInstance()).hasToString("/api/v1/pets");
        assertThat(problem.getProperties()).containsEntry("requestId", "req-1");
    }

    @Test
    void anErrorResponseBecomesAProblemWithoutLeakingServerDetails() {
        ErrorResponse error = ErrorResponse.builder()
                .code("VALIDATION_ERROR").message("Validation failed").details("check name")
                .fieldErrors(Map.of("name", "must not be blank")).validationErrors(List.of("bad"))
                .requestId("req-2").path("/api/v1/pets").timestamp(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();

        Map<String, Object> members = ProblemDetails.toMap(ProblemDetails.of(400, error));

        assertThat(members).containsKeys("title", "status", "detail", "instance", "code", "details",
                "fieldErrors", "errors", "requestId", "timestamp");
        assertThat(List.copyOf(members.keySet()).subList(0, 4))
                .containsExactly("title", "status", "detail", "instance");
        // No type member: RFC 9457 reads its absence as about:blank.
        assertThat(members).doesNotContainKey("type").containsEntry("title", "Bad Request")
                .containsEntry("status", 400).containsEntry("detail", "Validation failed");
        assertThat(members.get("timestamp")).asString().startsWith("2026-09-01T10:00");

        ProblemDetail typed = ProblemDetails.of(HttpStatus.CONFLICT, "taken", null);
        typed.setType(java.net.URI.create("https://adhar.io/problems/taken"));
        assertThat(ProblemDetails.toMap(typed)).containsEntry("type", "https://adhar.io/problems/taken");

        ErrorResponse failure = ErrorResponse.builder().code("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred").details("NullPointerException in OrderRepository").build();
        assertThat(ProblemDetails.toMap(ProblemDetails.of(500, failure))).doesNotContainKey("details");
    }

    @Test
    void problemsAreWrittenAsProblemJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "quote \" and \\ and \n");
        problem.setProperty("list", List.of(1, true));
        problem.setProperty("nothing", null);

        ProblemDetails.write(response, problem);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).isEqualTo("{\"title\":\"Bad Request\","
                + "\"status\":400,\"detail\":\"quote \\\" and \\\\ and \\n\",\"list\":[1,true],\"nothing\":null}");
        assertThat(ProblemDetails.toJson("\u0001")).isEqualTo("\"\\u0001\"");
    }
}
