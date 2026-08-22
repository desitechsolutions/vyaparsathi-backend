package com.desitech.vyaparsathi.payroll.enums;

public enum TaxRegime {
    NEW_REGIME("New Regime"),
    OLD_REGIME("Old Regime");

    private final String label;

    TaxRegime(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
