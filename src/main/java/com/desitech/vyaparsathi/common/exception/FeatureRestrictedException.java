package com.desitech.vyaparsathi.common.exception;

import lombok.Getter;

@Getter
public class FeatureRestrictedException extends RuntimeException {

    private final String code = "FEATURE_RESTRICTED";
    private final String feature;
    private final boolean canStartTrial;
    private final int trialDays;

    public FeatureRestrictedException(String feature, String message, boolean canStartTrial, int trialDays) {
        super(message);
        this.feature = feature;
        this.canStartTrial = canStartTrial;
        this.trialDays = trialDays;
    }
}
