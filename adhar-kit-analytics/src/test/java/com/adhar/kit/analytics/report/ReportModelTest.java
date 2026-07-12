package com.adhar.kit.analytics.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Report Model Tests")
class ReportModelTest {

    @Test
    @DisplayName("all-args constructor, getters and setters")
    void constructorAndAccessors() {
        LocalDateTime now = LocalDateTime.now();
        Report report = new Report("id1", "name1", Report.ReportType.JSON,
                List.of(Map.of("a", 1)), List.of("a"), now, "/path", Map.of("k", "v"));

        assertEquals("id1", report.getReportId());
        assertEquals("name1", report.getName());
        assertEquals(Report.ReportType.JSON, report.getType());
        assertEquals(1, report.getData().size());
        assertEquals(List.of("a"), report.getHeaders());
        assertEquals(now, report.getGeneratedAt());
        assertEquals("/path", report.getFilePath());
        assertEquals("v", report.getMetadata().get("k"));

        Report empty = new Report();
        empty.setReportId("x");
        empty.setName("y");
        empty.setFilePath("/p");
        assertEquals("x", empty.getReportId());
        assertEquals("y", empty.getName());
        assertEquals("/p", empty.getFilePath());
    }

    @Test
    @DisplayName("equals, hashCode and toString")
    void equalsHashCodeToString() {
        Report a = new Report("id", "n", Report.ReportType.CSV, null, null, null, null, null);
        Report b = new Report("id", "n", Report.ReportType.CSV, null, null, null, null, null);
        Report c = new Report("other", "n", Report.ReportType.CSV, null, null, null, null, null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(null, a);
        assertTrue(a.toString().contains("Report"));
    }

    @Test
    @DisplayName("ReportType enum values")
    void reportTypeEnum() {
        assertEquals(4, Report.ReportType.values().length);
        assertEquals(Report.ReportType.PDF, Report.ReportType.valueOf("PDF"));
    }
}
