package com.adhar.kit.batch.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResourcelessRepositoryGuard}: a repository that keeps nothing refuses to
 * start, unless a test asked for it.
 */
class ResourcelessRepositoryGuardTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JobRepository> provider = mock(ObjectProvider.class);

    private JobRepository proxied(JobRepository target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(JobRepository.class);
        return (JobRepository) factory.getProxy();
    }

    @Test
    @DisplayName("an in-memory repository fails the startup with exit code 2 and says how to fix it")
    void refusesInMemory() {
        when(provider.getIfAvailable()).thenReturn(new ResourcelessJobRepository());

        assertThatThrownBy(() -> new ResourcelessRepositoryGuard(provider, false).afterSingletonsInstantiated())
                .isInstanceOf(BatchRepositoryException.class)
                .hasMessageContaining("spring.datasource.url")
                .hasMessageContaining("adhar.batch.repository.allow-in-memory=true")
                .satisfies(failure -> assertThat(((BatchRepositoryException) failure).getExitCode()).isEqualTo(2));
    }

    @Test
    @DisplayName("an in-memory repository behind a proxy is recognised too")
    void seesThroughAProxy() {
        JobRepository proxy = proxied(new ResourcelessJobRepository());
        when(provider.getIfAvailable()).thenReturn(proxy);

        assertThat(ResourcelessRepositoryGuard.isResourceless(proxy)).isTrue();
        assertThatThrownBy(() -> new ResourcelessRepositoryGuard(provider, false).afterSingletonsInstantiated())
                .isInstanceOf(BatchRepositoryException.class);
    }

    @Test
    @DisplayName("a test may allow the in-memory repository")
    void allowedInMemory() {
        when(provider.getIfAvailable()).thenReturn(new ResourcelessJobRepository());

        assertThatCode(() -> new ResourcelessRepositoryGuard(provider, true).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a repository that persists, or none at all, passes")
    void othersPass() {
        JobRepository jdbc = mock(JobRepository.class);

        assertThat(ResourcelessRepositoryGuard.isResourceless(jdbc)).isFalse();
        when(provider.getIfAvailable()).thenReturn(jdbc);
        assertThatCode(() -> new ResourcelessRepositoryGuard(provider, false).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
        when(provider.getIfAvailable()).thenReturn(null);
        assertThatCode(() -> new ResourcelessRepositoryGuard(provider, false).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
    }
}
