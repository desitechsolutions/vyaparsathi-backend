package com.desitech.vyaparsathi.payroll.enums;

public enum SubmissionType {
    ECR("EPFO ECR"),
    ESIC_RETURN("ESIC Return"),
    PT_RETURN("Professional Tax Return"),
    TDS_RETURN("TDS Return");

    private final String label;

    SubmissionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
