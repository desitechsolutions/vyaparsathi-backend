package com.desitech.vyaparsathi.analytics.service;

public enum AnalyticsExportFormat {
    CSV, EXCEL, PDF;

    public static AnalyticsExportFormat fromString(String format) {
        if (format == null) return CSV;
        switch (format.toLowerCase()) {
            case "excel":
            case "xlsx":
                return EXCEL;
            case "pdf":
                return PDF;
            default:
                return CSV;
        }
    }
}
