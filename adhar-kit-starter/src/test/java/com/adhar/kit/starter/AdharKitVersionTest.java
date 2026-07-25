package com.adhar.kit.starter;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AdharKitVersionTest {

    @Test
    void getVersion_resolvesRealProjectVersion_notTheOldHardcodedDefault() {
        String version = AdharKitVersion.getVersion();

        assertThat(version).isNotBlank();
        // The bug being fixed: AdharFacade/AdharKitAutoConfiguration used to hardcode
        // "1.0.0-SNAPSHOT" while the build is actually 0.1.0-SNAPSHOT.
        assertThat(version).isNotEqualTo("1.0.0-SNAPSHOT");
        assertThat(version).matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
    }

    @Test
    void getVersion_isCachedAcrossCalls() {
        assertThat(AdharKitVersion.getVersion()).isSameAs(AdharKitVersion.getVersion());
    }

    @Test
    void isUsable_rejectsNullBlankAndUnfilteredPlaceholders() throws Exception {
        Method isUsable = AdharKitVersion.class.getDeclaredMethod("isUsable", String.class);
        isUsable.setAccessible(true);

        assertThat((Boolean) isUsable.invoke(null, (Object) null)).isFalse();
        assertThat((Boolean) isUsable.invoke(null, "")).isFalse();
        assertThat((Boolean) isUsable.invoke(null, "   ")).isFalse();
        assertThat((Boolean) isUsable.invoke(null, "@project.version@")).isFalse();
        assertThat((Boolean) isUsable.invoke(null, "${project.version}")).isFalse();
        assertThat((Boolean) isUsable.invoke(null, "0.1.0-SNAPSHOT")).isTrue();
    }

    @Test
    void readProperty_returnsNullWhenResourceMissing() throws Exception {
        Method readProperty = AdharKitVersion.class.getDeclaredMethod("readProperty", String.class, String.class);
        readProperty.setAccessible(true);

        Object result = readProperty.invoke(null, "/META-INF/does-not-exist.properties", "version");
        assertThat(result).isNull();
    }

    @Test
    void readProperty_readsRealBuildInfoResource() throws Exception {
        Method readProperty = AdharKitVersion.class.getDeclaredMethod("readProperty", String.class, String.class);
        readProperty.setAccessible(true);

        Object result = readProperty.invoke(null, "/META-INF/adhar-kit.properties", "adhar-kit.version");
        assertThat((String) result).matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
    }

    @Test
    void resolve_producesUsableVersion() throws Exception {
        Method resolve = AdharKitVersion.class.getDeclaredMethod("resolve");
        resolve.setAccessible(true);

        String result = (String) resolve.invoke(null);
        assertThat(result).matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
    }
}
