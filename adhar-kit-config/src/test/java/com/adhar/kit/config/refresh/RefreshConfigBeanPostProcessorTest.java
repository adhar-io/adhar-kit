package com.adhar.kit.config.refresh;

import com.adhar.kit.config.annotation.RefreshConfig;
import com.adhar.kit.config.manager.ConfigManager;
import com.adhar.kit.config.source.ConfigSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshConfigBeanPostProcessorTest {

    private ConfigManager manager;
    private TestSource source;
    private RefreshConfigBeanPostProcessor processor;

    /** Simple controllable ConfigSource for tests. */
    static class TestSource implements ConfigSource {
        final Map<String, Object> data = new HashMap<>();

        @Override public String getType() { return "test"; }
        @Override public Map<String, Object> loadConfig() { return new HashMap<>(data); }
        @Override public Optional<Object> getProperty(String key) { return Optional.ofNullable(data.get(key)); }
        @Override public boolean supportsRefresh() { return true; }
        @Override public boolean refresh() { return true; }
    }

    static class WatchedBean {
        final AtomicInteger keyInvocations = new AtomicInteger();
        final AtomicInteger prefixInvocations = new AtomicInteger();
        final AtomicInteger anyChangeInvocations = new AtomicInteger();
        final AtomicInteger startupInvocations = new AtomicInteger();
        final AtomicReference<String> lastChangedKey = new AtomicReference<>();
        final AtomicReference<Object> lastNewValue = new AtomicReference<>();

        @RefreshConfig(keys = {"cache.ttl", "cache.maxSize"})
        public void onCacheKeysChanged() {
            keyInvocations.incrementAndGet();
        }

        @RefreshConfig(prefix = "api.")
        public void onApiPrefixChanged(String key, Object oldValue, Object newValue) {
            prefixInvocations.incrementAndGet();
            lastChangedKey.set(key);
            lastNewValue.set(newValue);
        }

        @RefreshConfig
        public void onAnyChange() {
            anyChangeInvocations.incrementAndGet();
        }

        @RefreshConfig(keys = "startup.key", refreshOnStartup = true)
        public void onStartup() {
            startupInvocations.incrementAndGet();
        }
    }

    static class InvalidSignatureBean {
        final AtomicInteger invocations = new AtomicInteger();

        @RefreshConfig(keys = "some.key")
        public void wrongArity(String onlyOneArg) {
            invocations.incrementAndGet();
        }
    }

    static class PlainBean {
        public void notAnnotated() {
        }
    }

    static class ThrowingBean {
        final AtomicInteger invocations = new AtomicInteger();

        @RefreshConfig(keys = "boom.key")
        public void explode() {
            invocations.incrementAndGet();
            throw new IllegalStateException("boom");
        }
    }

    @BeforeEach
    void setUp() {
        manager = new ConfigManager();
        source = new TestSource();
        source.data.put("cache.ttl", "300");
        source.data.put("api.timeout", "5000");
        source.data.put("other.key", "x");
        manager.addSource(source);
        processor = new RefreshConfigBeanPostProcessor(() -> manager);
    }

    @Test
    void postProcessReturnsSameBean() {
        WatchedBean bean = new WatchedBean();
        assertThat(processor.postProcessAfterInitialization(bean, "watchedBean")).isSameAs(bean);
        PlainBean plain = new PlainBean();
        assertThat(processor.postProcessAfterInitialization(plain, "plainBean")).isSameAs(plain);
    }

    @Test
    void keyWatchingMethodInvokedWhenWatchedKeyChanges() {
        WatchedBean bean = new WatchedBean();
        processor.postProcessAfterInitialization(bean, "watchedBean");

        source.data.put("cache.ttl", "600");
        manager.refreshAll();

        assertThat(bean.keyInvocations.get()).isEqualTo(1);
    }

    @Test
    void keyWatchingMethodNotInvokedForUnrelatedKey() {
        WatchedBean bean = new WatchedBean();
        processor.postProcessAfterInitialization(bean, "watchedBean");

        source.data.put("other.key", "y");
        manager.refreshAll();

        assertThat(bean.keyInvocations.get()).isZero();
        assertThat(bean.prefixInvocations.get()).isZero();
    }

    @Test
    void prefixWatchingMethodReceivesChangeDetails() {
        WatchedBean bean = new WatchedBean();
        processor.postProcessAfterInitialization(bean, "watchedBean");

        source.data.put("api.timeout", "10000");
        manager.refreshAll();

        assertThat(bean.prefixInvocations.get()).isEqualTo(1);
        assertThat(bean.lastChangedKey.get()).isEqualTo("api.timeout");
        assertThat(bean.lastNewValue.get()).isEqualTo("10000");
    }

    @Test
    void methodWithoutKeysOrPrefixInvokedOnAnyChange() {
        WatchedBean bean = new WatchedBean();
        processor.postProcessAfterInitialization(bean, "watchedBean");

        source.data.put("other.key", "y");
        manager.refreshAll();

        assertThat(bean.anyChangeInvocations.get()).isEqualTo(1);
    }

    @Test
    void refreshOnStartupInvokesMethodImmediately() {
        WatchedBean bean = new WatchedBean();
        processor.postProcessAfterInitialization(bean, "watchedBean");

        assertThat(bean.startupInvocations.get()).isEqualTo(1);
    }

    @Test
    void invalidSignatureMethodIsSkipped() {
        InvalidSignatureBean bean = new InvalidSignatureBean();
        processor.postProcessAfterInitialization(bean, "invalidBean");

        source.data.put("some.key", "changed");
        manager.refreshAll();

        assertThat(bean.invocations.get()).isZero();
    }

    @Test
    void listenerExceptionDoesNotBreakRefresh() {
        ThrowingBean bean = new ThrowingBean();
        processor.postProcessAfterInitialization(bean, "throwingBean");

        source.data.put("boom.key", "changed");
        manager.refreshAll();

        assertThat(bean.invocations.get()).isEqualTo(1);
        assertThat(manager.getProperty("boom.key")).isEqualTo("changed");
    }

    @Test
    void multipleChangesInvokeListenerPerMatchingKey() {
        WatchedBean bean = new WatchedBean();
        processor.postProcessAfterInitialization(bean, "watchedBean");

        source.data.put("cache.ttl", "600");
        source.data.put("cache.maxSize", "2000");
        manager.refreshAll();

        assertThat(bean.keyInvocations.get()).isEqualTo(2);
    }
}
