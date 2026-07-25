package com.adhar.kit.graphql.security;

import com.adhar.kit.graphql.config.GraphQlProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GraphQlSecurityInterceptor}.
 */
class GraphQlSecurityInterceptorTest {

    private final WebGraphQlResponse mockResponse = mock(WebGraphQlResponse.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private WebGraphQlRequest request(String document) {
        WebGraphQlRequest request = mock(WebGraphQlRequest.class);
        when(request.getDocument()).thenReturn(document);
        return request;
    }

    private WebGraphQlInterceptor.Chain passThroughChain() {
        return req -> Mono.just(mockResponse);
    }

    @Test
    @DisplayName("blocks introspection query when introspection disabled")
    void blocksIntrospectionWhenDisabled() {
        GraphQlProperties props = new GraphQlProperties();
        props.setIntrospectionEnabled(false);
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request("query { __schema { types { name } } }"), passThroughChain());

        assertThatThrownBy(result::block)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("introspection is disabled");
    }

    @Test
    @DisplayName("blocks __type introspection query when introspection disabled")
    void blocksTypeIntrospection() {
        GraphQlProperties props = new GraphQlProperties();
        props.setIntrospectionEnabled(false);
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request("{ __type(name: \"User\") { name } }"), passThroughChain());

        assertThatThrownBy(result::block).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("allows introspection query when introspection enabled")
    void allowsIntrospectionWhenEnabled() {
        GraphQlProperties props = new GraphQlProperties();
        props.setIntrospectionEnabled(true);
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request("query { __schema { types { name } } }"), passThroughChain());

        assertThat(result.block()).isSameAs(mockResponse);
    }

    @Test
    @DisplayName("allows normal query through the chain")
    void allowsNormalQuery() {
        GraphQlProperties props = new GraphQlProperties();
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request("query { user(id: 1) { name } }"), passThroughChain());

        assertThat(result.block()).isSameAs(mockResponse);
    }

    @Test
    @DisplayName("blank document is not treated as introspection")
    void blankDocumentNotIntrospection() {
        GraphQlProperties props = new GraphQlProperties();
        props.setIntrospectionEnabled(false);
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);

        Mono<WebGraphQlResponse> result = interceptor.intercept(request("   "), passThroughChain());

        assertThat(result.block()).isSameAs(mockResponse);
    }

    @Test
    @DisplayName("blocks unauthenticated request when authentication required")
    void blocksUnauthenticated() {
        GraphQlProperties props = new GraphQlProperties();
        props.getSecurity().setRequireAuthentication(true);
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);
        SecurityContextHolder.clearContext();

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request("query { user { name } }"), passThroughChain());

        assertThatThrownBy(result::block)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Authentication is required");
    }

    @Test
    @DisplayName("blocks request with a non-authenticated token")
    void blocksNonAuthenticatedToken() {
        GraphQlProperties props = new GraphQlProperties();
        props.getSecurity().setRequireAuthentication(true);
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);

        Authentication notAuthenticated = new UsernamePasswordAuthenticationToken("user", "pw");
        // token is not authenticated until granted authorities are set
        SecurityContextHolder.getContext().setAuthentication(notAuthenticated);
        assertThat(notAuthenticated.isAuthenticated()).isFalse();

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request("query { user { name } }"), passThroughChain());

        assertThatThrownBy(result::block).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("does not flag a field/argument that merely contains '__schema' as text")
    void doesNotFalselyFlagTextContainingIntrospectionSubstring() {
        GraphQlProperties props = new GraphQlProperties();
        props.setIntrospectionEnabled(false);
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);

        // "mySchemaField" and a string literal containing "__schema" are not introspection
        // field selections; a naive substring check would incorrectly block this.
        Mono<WebGraphQlResponse> result = interceptor.intercept(
                request("query { user(filter: \"__schema\") { mySchemaField } }"), passThroughChain());

        assertThat(result.block()).isSameAs(mockResponse);
    }

    @Test
    @DisplayName("blocks introspection field referenced only through a named fragment")
    void blocksIntrospectionViaNamedFragment() {
        GraphQlProperties props = new GraphQlProperties();
        props.setIntrospectionEnabled(false);
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);

        String document = "query { user { ...Frag } } fragment Frag on User { __typename __schema { types { name } } }";
        Mono<WebGraphQlResponse> result = interceptor.intercept(request(document), passThroughChain());

        assertThatThrownBy(result::block).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("blocks introspection field nested inside an inline fragment")
    void blocksIntrospectionViaInlineFragment() {
        GraphQlProperties props = new GraphQlProperties();
        props.setIntrospectionEnabled(false);
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);

        String document = "query { user { ... on User { __type(name: \"X\") { name } } } }";
        Mono<WebGraphQlResponse> result = interceptor.intercept(request(document), passThroughChain());

        assertThatThrownBy(result::block).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("falls back to substring detection for an unparseable document")
    void fallsBackToSubstringForUnparseableDocument() {
        GraphQlProperties props = new GraphQlProperties();
        props.setIntrospectionEnabled(false);
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request("{{{ not valid graphql __schema"), passThroughChain());

        assertThatThrownBy(result::block).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("allows an unparseable document with no introspection substring through")
    void allowsUnparseableDocumentWithoutIntrospectionSubstring() {
        GraphQlProperties props = new GraphQlProperties();
        props.setIntrospectionEnabled(false);
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request("{{{ not valid graphql at all"), passThroughChain());

        assertThat(result.block()).isSameAs(mockResponse);
    }

    @Test
    @DisplayName("allows authenticated request when authentication required")
    void allowsAuthenticated() {
        GraphQlProperties props = new GraphQlProperties();
        props.getSecurity().setRequireAuthentication(true);
        GraphQlSecurityInterceptor interceptor = new GraphQlSecurityInterceptor(props);

        Authentication authenticated = new AnonymousAuthenticationToken(
                "key", "principal", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(authenticated);
        assertThat(authenticated.isAuthenticated()).isTrue();

        Mono<WebGraphQlResponse> result =
                interceptor.intercept(request("query { user { name } }"), passThroughChain());

        assertThat(result.block()).isSameAs(mockResponse);
    }
}
