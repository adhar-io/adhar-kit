package com.adhar.kit.analytics.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents a generated report.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    /**
     * Report ID.
     */
    private String reportId;

    /**
     * Report name.
     */
    private String name;

    /**
     * Report type (CSV, PDF, EXCEL).
     */
    private ReportType type;

    /**
     * Report data.
     */
    private List<Map<String, Object>> data;

    /**
     * Column headers.
     */
    private List<String> headers;

    /**
     * Generation timestamp.
     */
    private LocalDateTime generatedAt;

    /**
     * File path where report is saved.
     */
    private String filePath;

    /**
     * Report metadata.
     */
    private Map<String, String> metadata;

    public enum ReportType {
        CSV,
        PDF,
        EXCEL,
        JSON
    }
}

