package com.adhar.kit.health.indicator;

import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DiskSpaceHealthIndicator}.
 */
class DiskSpaceHealthIndicatorTest {

    @Test
    void getName_returnsDiskSpace() {
        DiskSpaceHealthIndicator indicator = new DiskSpaceHealthIndicator(0, ".");
        assertThat(indicator.getName()).isEqualTo("diskSpace");
    }

    @Test
    void check_whenFreeSpaceAboveThreshold_returnsUp() {
        // Threshold of 0 bytes is always satisfied.
        DiskSpaceHealthIndicator indicator = new DiskSpaceHealthIndicator(0L, ".");

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getComponent()).isEqualTo("diskSpace");
        assertThat(health.getDetails())
            .containsKey("total")
            .containsKey("free")
            .containsKey("usable")
            .containsKey("threshold")
            .containsEntry("path", ".");
    }

    @Test
    void check_whenFreeSpaceBelowThreshold_returnsDown() {
        // Threshold of Long.MAX_VALUE can never be met by real free space.
        DiskSpaceHealthIndicator indicator = new DiskSpaceHealthIndicator(Long.MAX_VALUE, ".");

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails())
            .containsEntry("message", "Free disk space below threshold")
            .containsKey("free")
            .containsKey("threshold");
    }

    @Test
    void check_whenPathIsNull_returnsDown() {
        // new File((String) null) throws NullPointerException, exercising the catch branch.
        DiskSpaceHealthIndicator indicator = new DiskSpaceHealthIndicator(0, null);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getComponent()).isEqualTo("diskSpace");
    }

    @Test
    void formatBytes_coversAllUnitBranches() throws Exception {
        Method m = DiskSpaceHealthIndicator.class.getDeclaredMethod("formatBytes", long.class);
        m.setAccessible(true);
        DiskSpaceHealthIndicator indicator = new DiskSpaceHealthIndicator(0, ".");

        assertThat((String) m.invoke(indicator, 5L * 1024 * 1024 * 1024)).contains("GB");
        assertThat((String) m.invoke(indicator, 5L * 1024 * 1024)).contains("MB");
        assertThat((String) m.invoke(indicator, 5L * 1024)).contains("KB");
        assertThat((String) m.invoke(indicator, 512L)).isEqualTo("512 bytes");
    }
}
