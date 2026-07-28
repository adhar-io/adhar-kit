package com.adhar.kit.persistence.diagnostics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("NPlusOneDetector Tests")
class NPlusOneDetectorTest {

    private static final String SQL = "select * from orders where customer_id = ?";

    private NPlusOneDetector detector;

    @AfterEach
    void cleanup() {
        if (detector != null) {
            detector.remove();
        }
    }

    @Test
    @DisplayName("inspect() returns the SQL unchanged")
    void inspectReturnsSqlUnchanged() {
        detector = new NPlusOneDetector(3);
        assertEquals(SQL, detector.inspect(SQL));
    }

    @Test
    @DisplayName("inspect() returns null for null input")
    void inspectHandlesNull() {
        detector = new NPlusOneDetector(3);
        assertNull(detector.inspect(null));
    }

    @Test
    @DisplayName("counts identical statements (trimmed) on the same thread")
    void countsIdenticalStatements() {
        detector = new NPlusOneDetector(3);

        detector.inspect(SQL);
        detector.inspect("  " + SQL + "  ");
        detector.inspect(SQL);

        assertEquals(3, detector.currentCount(SQL));
    }

    @Test
    @DisplayName("different statements are counted independently")
    void distinctStatementsCountedSeparately() {
        detector = new NPlusOneDetector(3);

        detector.inspect(SQL);
        detector.inspect("select * from products");

        assertEquals(1, detector.currentCount(SQL));
        assertEquals(1, detector.currentCount("select * from products"));
    }

    @Test
    @DisplayName("exceeding the threshold does not throw and keeps counting")
    void exceedingThresholdKeepsCounting() {
        detector = new NPlusOneDetector(2);

        for (int i = 0; i < 5; i++) {
            detector.inspect(SQL);
        }

        assertEquals(5, detector.currentCount(SQL));
    }

    @Test
    @DisplayName("reset() clears per-thread counters")
    void resetClearsCounters() {
        detector = new NPlusOneDetector(3);
        detector.inspect(SQL);
        detector.inspect(SQL);

        detector.reset();

        assertEquals(0, detector.currentCount(SQL));
    }

    @Test
    @DisplayName("threshold below 1 is clamped to 1")
    void thresholdClampedToOne() {
        detector = new NPlusOneDetector(0);
        assertEquals(1, detector.getThreshold());
    }

    @Test
    @DisplayName("currentCount() returns 0 for unknown or null statement")
    void currentCountUnknown() {
        detector = new NPlusOneDetector(3);
        assertEquals(0, detector.currentCount("never seen"));
        assertEquals(0, detector.currentCount(null));
    }

    @Test
    @DisplayName("counts are isolated per thread")
    void countsIsolatedPerThread() throws InterruptedException {
        detector = new NPlusOneDetector(3);
        detector.inspect(SQL);

        int[] otherThreadCount = new int[1];
        Thread other = new Thread(() -> {
            detector.inspect(SQL);
            otherThreadCount[0] = detector.currentCount(SQL);
            detector.remove();
        });
        other.start();
        other.join();

        assertEquals(1, otherThreadCount[0]);
        assertEquals(1, detector.currentCount(SQL));
    }
}
