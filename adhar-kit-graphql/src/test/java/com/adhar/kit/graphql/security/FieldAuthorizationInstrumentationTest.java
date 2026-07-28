package com.adhar.kit.graphql.security;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit and integration tests for {@link FieldAuthorizationInstrumentation}.
 */
class FieldAuthorizationInstrumentationTest {

    private static final String SDL = """
            directive @auth(roles: [String!]) on FIELD_DEFINITION
            type Query {
              publicField: String
              adminField: String @auth(roles: ["ADMIN"])
              anyAuthedField: String @auth(roles: [])
            }
            """;

    private GraphQL graphQl;

    @BeforeEach
    void setUp() {
        TypeDefinitionRegistry registry = new SchemaParser().parse(SDL);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("publicField", env -> "public")
                        .dataFetcher("adminField", env -> "secret")
                        .dataFetcher("anyAuthedField", env -> "member"))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        graphQl = GraphQL.newGraphQL(schema)
                .instrumentation(new FieldAuthorizationInstrumentation())
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(String... authorities) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pw", AuthorityUtils.createAuthorityList(authorities));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("unguarded field is always accessible")
    void unguardedFieldAccessible() {
        ExecutionResult result = graphQl.execute("{ publicField }");
        assertThat(result.getErrors()).isEmpty();
        assertThat((String) ((java.util.Map<?, ?>) result.getData()).get("publicField")).isEqualTo("public");
    }

    @Test
    @DisplayName("guarded field is denied for an unauthenticated caller")
    void guardedFieldDeniedWhenUnauthenticated() {
        ExecutionResult result = graphQl.execute("{ adminField }");
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(((java.util.Map<?, ?>) result.getData()).get("adminField")).isNull();
    }

    @Test
    @DisplayName("guarded field is denied when the caller lacks the required role")
    void guardedFieldDeniedWithoutRole() {
        authenticateWith("ROLE_USER");
        ExecutionResult result = graphQl.execute("{ adminField }");
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    @DisplayName("guarded field is allowed when the caller has the required role (ROLE_ prefix tolerated)")
    void guardedFieldAllowedWithRole() {
        authenticateWith("ROLE_ADMIN");
        ExecutionResult result = graphQl.execute("{ adminField }");
        assertThat(result.getErrors()).isEmpty();
        assertThat(((java.util.Map<?, ?>) result.getData()).get("adminField")).isEqualTo("secret");
    }

    @Test
    @DisplayName("field requiring an empty role list only needs an authenticated caller")
    void emptyRolesRequiresAuthenticationOnly() {
        ExecutionResult denied = graphQl.execute("{ anyAuthedField }");
        assertThat(denied.getErrors()).isNotEmpty();

        authenticateWith("ROLE_USER");
        ExecutionResult allowed = graphQl.execute("{ anyAuthedField }");
        assertThat(allowed.getErrors()).isEmpty();
        assertThat(((java.util.Map<?, ?>) allowed.getData()).get("anyAuthedField")).isEqualTo("member");
    }

    @Test
    @DisplayName("isAuthorized returns false without authentication and true with a matching role")
    void isAuthorizedDirect() {
        FieldAuthorizationInstrumentation instrumentation = new FieldAuthorizationInstrumentation();
        assertThat(instrumentation.isAuthorized(List.of("ADMIN"))).isFalse();

        authenticateWith("ROLE_ADMIN");
        assertThat(instrumentation.isAuthorized(List.of("ADMIN"))).isTrue();
        assertThat(instrumentation.isAuthorized(List.of("OTHER"))).isFalse();
        assertThat(instrumentation.isAuthorized(List.of())).isTrue();
    }
}
