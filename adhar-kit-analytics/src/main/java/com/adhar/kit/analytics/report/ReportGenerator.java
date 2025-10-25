package com.adhar.kit.analytics.report;

import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for generating reports in various formats.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class ReportGenerator {

    /**
     * Generate CSV report.
     */
    public Report generateCsvReport(String name, List<String> headers, List<Map<String, Object>> data, String outputPath) {
        try {
            String filePath = outputPath + "/" + name + ".csv";

            try (CSVWriter writer = new CSVWriter(new FileWriter(filePath))) {
                // Write headers
                writer.writeNext(headers.toArray(new String[0]));

                // Write data
                for (Map<String, Object> row : data) {
                    String[] rowData = headers.stream()
                            .map(header -> row.get(header) != null ? row.get(header).toString() : "")
                            .toArray(String[]::new);
                    writer.writeNext(rowData);
                }
            }

            log.info("Generated CSV report: {}", filePath);

            return new Report(
                    UUID.randomUUID().toString(),
                    name,
                    Report.ReportType.CSV,
                    data,
                    headers,
                    LocalDateTime.now(),
                    filePath,
                    Map.of("rows", String.valueOf(data.size()))
            );

        } catch (Exception e) {
            log.error("Failed to generate CSV report", e);
            throw new ReportGenerationException("Failed to generate CSV report", e);
        }
    }

    /**
     * Generate Excel report.
     */
    public Report generateExcelReport(String name, List<String> headers, List<Map<String, Object>> data, String outputPath) {
        try {
            String filePath = outputPath + "/" + name + ".xlsx";

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet(name);

                // Create header row
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

                // Create data rows
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

                // Auto-size columns
                for (int i = 0; i < headers.size(); i++) {
                    sheet.autoSizeColumn(i);
                }

                // Write to file
                try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
                    workbook.write(outputStream);
                }
            }

            log.info("Generated Excel report: {}", filePath);

            return new Report(
                    UUID.randomUUID().toString(),
                    name,
                    Report.ReportType.EXCEL,
                    data,
                    headers,
                    LocalDateTime.now(),
                    filePath,
                    Map.of("rows", String.valueOf(data.size()))
            );

        } catch (Exception e) {
            log.error("Failed to generate Excel report", e);
            throw new ReportGenerationException("Failed to generate Excel report", e);
        }
    }

    /**
     * Generate report based on type.
     */
    public Report generateReport(String name, Report.ReportType type, List<String> headers,
                                List<Map<String, Object>> data, String outputPath) {
        return switch (type) {
            case CSV -> generateCsvReport(name, headers, data, outputPath);
            case EXCEL -> generateExcelReport(name, headers, data, outputPath);
            default -> throw new UnsupportedOperationException("Report type not supported: " + type);
        };
    }

    public static class ReportGenerationException extends RuntimeException {
        public ReportGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

