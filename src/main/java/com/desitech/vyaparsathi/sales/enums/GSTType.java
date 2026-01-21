package com.desitech.vyaparsathi.sales.enums;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum GSTType {
    GST_0(0),
    GST_5(5),
    GST_12(12),
    GST_18(18),
    GST_28(28);

    private static final Logger logger = LoggerFactory.getLogger(GSTType.class);

    private final int rate;

    GSTType(int rate) {
        this.rate = rate;
    }

    public int getRate() {
        return rate;
    }

    public static GSTType fromRate(Integer gstRate) {
        if (gstRate == null) {
            logger.warn("GST rate is null. Defaulting to GST_0.");
            return GST_0;
        }

        for (GSTType gstType : GSTType.values()) {
            if (gstType.getRate() == gstRate) {
                return gstType;
            }
        }

        logger.warn("Invalid GST rate: {}. Defaulting to GST_0.", gstRate);
        return GST_0;
    }
}
