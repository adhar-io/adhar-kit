package com.adhar.kit.analytics.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReportGenerator Tests")
class ReportGeneratorTest {

    @TempDir
    Path tempDir;

    private ReportGenerator generator;
    private List<String> headers;
    private List<Map<String, Object>> data;

    @BeforeEach
    void setUp() {
        generator = new ReportGenerator();
        headers = List.of("name", "age", "score");
        Map<String, Object> row1 = new HashMap<>();
        row1.put("name", "Alice");
        row1.put("age", 30);
        row1.put("score", 95.5);
        Map<String, Object> row2 = new HashMap<>();
        row2.put("name", "Bob");
        row2.put("age", null); // exercise null cell branch
        data = List.of(row1, row2);
    }

    @Test
    @DisplayName("generateCsvReport writes file and returns metadata")
    void generateCsvReport() throws IOException {
        Report report = generator.generateCsvReport("people", headers, data, tempDir.toString());

        assertNotNull(report.getReportId());
        assertEquals("people", report.getName());
        assertEquals(Report.ReportType.CSV, report.getType());
        assertEquals(headers, report.getHeaders());
        assertNotNull(report.getGeneratedAt());
        assertEquals("2", report.getMetadata().get("rows"));

        Path file = tempDir.resolve("people.csv");
        assertTrue(Files.exists(file));
        String content = Files.readString(file);
        assertTrue(content.contains("Alice"));
        assertTrue(content.contains("name"));
    }

    @Test
    @DisplayName("generateExcelReport writes xlsx with numeric and string cells")
    void generateExcelReport() {
        Report report = generator.generateExcelReport("metrics", headers, data, tempDir.toString());

        assertEquals(Report.ReportType.EXCEL, report.getType());
        assertEquals("metrics", report.getName());
        Path file = tempDir.resolve("metrics.xlsx");
        assertTrue(Files.exists(file));
    }

    @Test
    @DisplayName("generateReport dispatches CSV and EXCEL")
    void generateReportDispatch() {
        Report csv = generator.generateReport("r1", Report.ReportType.CSV, headers, data, tempDir.toString());
        assertEquals(Report.ReportType.CSV, csv.getType());

        Report excel = generator.generateReport("r2", Report.ReportType.EXCEL, headers, data, tempDir.toString());
        assertEquals(Report.ReportType.EXCEL, excel.getType());
    }

    @Test
    @DisplayName("generateReport rejects unsupported types")
    void generateReportUnsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> generator.generateReport("r", Report.ReportType.PDF, headers, data, tempDir.toString()));
        assertThrows(UnsupportedOperationException.class,
                () -> generator.generateReport("r", Report.ReportType.JSON, headers, data, tempDir.toString()));
    }

    @Test
    @DisplayName("generateCsvReport wraps IO failures in ReportGenerationException")
    void generateCsvReportFailure() {
        // Non-existent directory path causes FileWriter to fail.
        String badPath = tempDir.resolve("does-not-exist").resolve("nested").toString();
        ReportGenerator.ReportGenerationException ex = assertThrows(
                ReportGenerator.ReportGenerationException.class,
                () -> generator.generateCsvReport("bad", headers, data, badPath));
        assertNotNull(ex.getCause());
        assertTrue(ex.getMessage().contains("CSV"));
    }

    @Test
    @DisplayName("generateExcelReport wraps IO failures in ReportGenerationException")
    void generateExcelReportFailure() {
        String badPath = tempDir.resolve("nope").resolve("deep").toString();
        ReportGenerator.ReportGenerationException ex = assertThrows(
                ReportGenerator.ReportGenerationException.class,
                () -> generator.generateExcelReport("bad", headers, data, badPath));
        assertTrue(ex.getMessage().contains("Excel"));
    }
}
