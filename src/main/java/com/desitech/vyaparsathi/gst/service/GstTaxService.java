package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.gst.dto.Gstr1ExportDto;
import com.desitech.vyaparsathi.gst.dto.Gstr3bExportDto;
import com.desitech.vyaparsathi.gst.dto.Gstr3bSummaryDto;
import com.desitech.vyaparsathi.gst.dto.Gstr9SummaryDto;
import com.desitech.vyaparsathi.gst.dto.HsnPreviewResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GstTaxService {

    private final Gstr1Builder  gstr1Builder;
    private final Gstr3bBuilder gstr3bBuilder;
    private final Gstr9Builder  gstr9Builder;

    public GstTaxService(Gstr1Builder gstr1Builder, Gstr3bBuilder gstr3bBuilder, Gstr9Builder gstr9Builder) {
        this.gstr1Builder  = gstr1Builder;
        this.gstr3bBuilder = gstr3bBuilder;
        this.gstr9Builder  = gstr9Builder;
    }

    /** Delegates to {@link Gstr1Builder} — all GSTR-1 logic lives there. */
    @Transactional(readOnly = true)
    public Gstr1ExportDto generateGstr1(int year, int month) {
        return gstr1Builder.build(year, month);
    }

    /** HSN Table 12 preview for the pre-download review widget. */
    @Transactional(readOnly = true)
    public HsnPreviewResponseDto buildHsnPreview(int year, int month) {
        return gstr1Builder.buildHsnPreview(year, month);
    }

    /** Delegates to {@link Gstr3bBuilder} — all GSTR-3B logic lives there. */
    @Transactional(readOnly = true)
    public Gstr3bSummaryDto generateGstr3b(int year, int month) {
        return gstr3bBuilder.buildSummary(year, month);
    }

    /** Builds the GSTN portal-filing JSON for GSTR-3B. */
    @Transactional(readOnly = true)
    public Gstr3bExportDto buildGstr3bExport(int year, int month) {
        return gstr3bBuilder.buildExport(year, month);
    }

    /** Delegates to {@link Gstr9Builder} — GSTR-9 annual return summary. */
    @Transactional(readOnly = true)
    public Gstr9SummaryDto generateGstr9(int fiscalYear) {
        return gstr9Builder.build(fiscalYear);
    }
}
