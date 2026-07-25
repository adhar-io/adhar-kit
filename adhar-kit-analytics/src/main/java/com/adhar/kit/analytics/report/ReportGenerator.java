package com.adhar.kit.analytics.report;

import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for generating reports in various formats.
 *
 * <p>Reports are built directly against a provided {@link OutputStream} (or
 * returned as an in-memory {@code byte[]}), so a controller can stream a
 * download straight from the HTTP response body instead of the generator
 * writing to a server-local file path - important for stream-safety in
 * multi-instance/containerized deployments where the local filesystem is not
 * shared or durable. The stream-based methods only {@code flush()} the
 * caller-supplied stream; they never close it, so the caller (e.g. a Spring
 * {@code StreamingResponseBody} or an HTTP response) retains control of its
 * lifecycle. File-writing overloads remain available as a convenience for
 * callers that do want a report on local disk (e.g. batch jobs, tests).</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class ReportGenerator {

    // ==================== CSV ====================

    /**
     * Writes a CSV report directly to the given stream. The stream is
     * flushed, not closed.
     */
    public void writeCsv(List<String> headers, List<Map<String, Object>> data, OutputStream out) {
        try {
            CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writer.writeNext(headers.toArray(new String[0]));
            for (Map<String, Object> row : data) {
                String[] rowData = headers.stream()
                        .map(header -> row.get(header) != null ? row.get(header).toString() : "")
                        .toArray(String[]::new);
                writer.writeNext(rowData);
            }
            writer.flush();
        } catch (IOException e) {
            log.error("Failed to generate CSV report", e);
            throw new ReportGenerationException("Failed to generate CSV report", e);
        }
    }

    /**
     * Generates a CSV report to the given stream and returns its metadata
     * (the returned {@link Report#getFilePath()} is {@code null} since no
     * file was written).
     */
    public Report generateCsvReport(String name, List<String> headers, List<Map<String, Object>> data, OutputStream out) {
        writeCsv(headers, data, out);
        log.info("Generated CSV report '{}' ({} rows) to stream", name, data.size());
        return buildReport(name, Report.ReportType.CSV, data, headers, null);
    }

    /**
     * Generates a CSV report and returns it as an in-memory byte array.
     */
    public byte[] generateCsvReportBytes(List<String> headers, List<Map<String, Object>> data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeCsv(headers, data, out);
        return out.toByteArray();
    }

    /**
     * Convenience overload that writes the CSV report to a file under {@code outputPath}.
     */
    public Report generateCsvReport(String name, List<String> headers, List<Map<String, Object>> data, String outputPath) {
        String filePath = outputPath + "/" + name + ".csv";
        try (OutputStream out = new FileOutputStream(filePath)) {
            writeCsv(headers, data, out);
        } catch (IOException e) {
            log.error("Failed to generate CSV report", e);
            throw new ReportGenerationException("Failed to generate CSV report", e);
        }
        log.info("Generated CSV report: {}", filePath);
        return buildReport(name, Report.ReportType.CSV, data, headers, filePath);
    }

    // ==================== Excel ====================

    /**
     * Writes an Excel (.xlsx) report directly to the given stream. The
     * stream is flushed, not closed.
     */
    public void writeExcel(String name, List<String> headers, List<Map<String, Object>> data, OutputStream out) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(name);

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.createCell(i);
                    Object value = rowData.get(headers.get(i));
                    if (value != null) {
                        if (value instanceof Number) {
                            cell.setCellValue(((Number) value).doubleValue());
                        } else {
                            cell.setCellValue(value.toString());
                        }
                    }
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            out.flush();
        } catch (IOException e) {
            log.error("Failed to generate Excel report", e);
            throw new ReportGenerationException("Failed to generate Excel report", e);
        }
    }

    /**
     * Generates an Excel report to the given stream and returns its metadata
     * (the returned {@link Report#getFilePath()} is {@code null} since no
     * file was written).
     */
    public Report generateExcelReport(String name, List<String> headers, List<Map<String, Object>> data, OutputStream out) {
        writeExcel(name, headers, data, out);
        log.info("Generated Excel report '{}' ({} rows) to stream", name, data.size());
        return buildReport(name, Report.ReportType.EXCEL, data, headers, null);
    }

    /**
     * Generates an Excel report and returns it as an in-memory byte array.
     */
    public byte[] generateExcelReportBytes(String name, List<String> headers, List<Map<String, Object>> data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeExcel(name, headers, data, out);
        return out.toByteArray();
    }

    /**
     * Convenience overload that writes the Excel report to a file under {@code outputPath}.
     */
    public Report generateExcelReport(String name, List<String> headers, List<Map<String, Object>> data, String outputPath) {
        String filePath = outputPath + "/" + name + ".xlsx";
        try (OutputStream out = new FileOutputStream(filePath)) {
            writeExcel(name, headers, data, out);
        } catch (IOException e) {
            log.error("Failed to generate Excel report", e);
            throw new ReportGenerationException("Failed to generate Excel report", e);
        }
        log.info("Generated Excel report: {}", filePath);
        return buildReport(name, Report.ReportType.EXCEL, data, headers, filePath);
    }

    // ==================== Dispatch ====================

    /**
     * Generates a report of the given type to a stream.
     */
    public Report generateReport(String name, Report.ReportType type, List<String> headers,
                                  List<Map<String, Object>> data, OutputStream out) {
        return switch (type) {
            case CSV -> generateCsvReport(name, headers, data, out);
            case EXCEL -> generateExcelReport(name, headers, data, out);
            default -> throw new UnsupportedOperationException("Report type not supported: " + type);
        };
    }

    /**
     * Generate report based on type, writing to a server-local file path.
     */
    public Report generateReport(String name, Report.ReportType type, List<String> headers,
                                List<Map<String, Object>> data, String outputPath) {
        return switch (type) {
            case CSV -> generateCsvReport(name, headers, data, outputPath);
            case EXCEL -> generateExcelReport(name, headers, data, outputPath);
            default -> throw new UnsupportedOperationException("Report type not supported: " + type);
        };
    }

    private Report buildReport(String name, Report.ReportType type, List<Map<String, Object>> data,
                                List<String> headers, String filePath) {
        return new Report(
                UUID.randomUUID().toString(),
                name,
                type,
                data,
                headers,
                LocalDateTime.now(),
                filePath,
                Map.of("rows", String.valueOf(data.size()))
        );
    }

    public static class ReportGenerationException extends RuntimeException {
        public ReportGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
