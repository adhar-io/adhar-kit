package com.adhar.kit.analytics.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies ReportGenerator's stream-based CSV/Excel export (bug #3: reports
 * previously always wrote to a server-local file path, which isn't
 * stream-safe for controller downloads or multi-instance deployments).
 */
@DisplayName("ReportGenerator streaming Tests")
class ReportGeneratorStreamingTest {

    private ReportGenerator generator;
    private List<String> headers;
    private List<Map<String, Object>> data;

    /** Tracks whether close() was called, so we can assert streams are only flushed, not closed. */
    private static class TrackingOutputStream extends FilterOutputStream {
        boolean closed = false;

        TrackingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    @BeforeEach
    void setUp() {
        generator = new ReportGenerator();
        headers = List.of("name", "age");
        Map<String, Object> row1 = new HashMap<>();
        row1.put("name", "Alice");
        row1.put("age", 30);
        data = List.of(row1);
    }

    @Test
    @DisplayName("writeCsv() writes CSV content to the stream without closing it")
    void writeCsvDoesNotCloseStream() {
        TrackingOutputStream out = new TrackingOutputStream(new ByteArrayOutputStream());

        generator.writeCsv(headers, data, out);

        assertFalse(out.closed, "writeCsv should flush, not close, the caller's stream");
    }

    @Test
    @DisplayName("generateCsvReport(stream) returns metadata with a null filePath and writes CSV bytes")
    void generateCsvReportToStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Report report = generator.generateCsvReport("people", headers, data, (OutputStream) out);

        assertNotNull(report.getReportId());
        assertEquals(Report.ReportType.CSV, report.getType());
        assertNull(report.getFilePath());
        assertEquals("1", report.getMetadata().get("rows"));
        String content = out.toString(StandardCharsets.UTF_8);
        assertTrue(content.contains("Alice"));
        assertTrue(content.contains("name"));
    }

    @Test
    @DisplayName("generateCsvReportBytes() returns the CSV content as a byte array")
    void generateCsvReportBytes() {
        byte[] bytes = generator.generateCsvReportBytes(headers, data);

        String content = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(content.contains("Alice"));
        assertTrue(content.contains("age"));
    }

    @Test
    @DisplayName("writeExcel() writes a valid xlsx to the stream without closing it")
    void writeExcelDoesNotCloseStream() {
        TrackingOutputStream out = new TrackingOutputStream(new ByteArrayOutputStream());

        generator.writeExcel("metrics", headers, data, out);

        assertFalse(out.closed, "writeExcel should flush, not close, the caller's stream");
    }

    @Test
    @DisplayName("generateExcelReport(stream) returns metadata with a null filePath and non-empty bytes")
    void generateExcelReportToStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Report report = generator.generateExcelReport("metrics", headers, data, (OutputStream) out);

        assertEquals(Report.ReportType.EXCEL, report.getType());
        assertNull(report.getFilePath());
        assertTrue(out.size() > 0);
    }

    @Test
    @DisplayName("generateExcelReportBytes() returns non-empty xlsx bytes")
    void generateExcelReportBytes() {
        byte[] bytes = generator.generateExcelReportBytes("metrics", headers, data);
        assertTrue(bytes.length > 0);
        // xlsx files are zip archives; verify the local file header magic bytes.
        assertEquals(0x50, bytes[0] & 0xFF);
        assertEquals(0x4B, bytes[1] & 0xFF);
    }

    @Test
    @DisplayName("generateReport(stream) dispatches CSV and EXCEL")
    void generateReportStreamDispatch() {
        Report csv = generator.generateReport("r1", Report.ReportType.CSV, headers, data, (OutputStream) new ByteArrayOutputStream());
        assertEquals(Report.ReportType.CSV, csv.getType());

        Report excel = generator.generateReport("r2", Report.ReportType.EXCEL, headers, data, (OutputStream) new ByteArrayOutputStream());
        assertEquals(Report.ReportType.EXCEL, excel.getType());
    }

    @Test
    @DisplayName("generateReport(stream) rejects unsupported types")
    void generateReportStreamUnsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> generator.generateReport("r", Report.ReportType.PDF, headers, data, (OutputStream) new ByteArrayOutputStream()));
        assertThrows(UnsupportedOperationException.class,
                () -> generator.generateReport("r", Report.ReportType.JSON, headers, data, (OutputStream) new ByteArrayOutputStream()));
    }

    @Test
    @DisplayName("writeCsv() wraps stream failures in ReportGenerationException")
    void writeCsvWrapsIoFailures() throws IOException {
        OutputStream failing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };

        ReportGenerator.ReportGenerationException ex = assertThrows(
                ReportGenerator.ReportGenerationException.class,
                () -> generator.writeCsv(headers, data, failing));
        assertTrue(ex.getMessage().contains("CSV"));
        assertNotNull(ex.getCause());
    }
}
