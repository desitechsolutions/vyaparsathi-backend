package com.desitech.vyaparsathi.document.controller;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.document.service.DocumentComplianceReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Reports the compliance monitoring surface consumes. All endpoints are
 * scoped to {@link TenantContext#getCurrentShopId}.
 */
@RestController
@RequestMapping("/api/v1/reports/compliance")
public class DocumentComplianceController {

    private final DocumentComplianceReportService svc;

    public DocumentComplianceController(DocumentComplianceReportService svc) {
        this.svc = svc;
    }

    @GetMapping("/sequence-gaps")
    public List<Map<String, Object>> sequenceGaps(
            @RequestParam String table,
            @RequestParam(defaultValue = "invoice_no") String numberCol,
            @RequestParam(required = false) String fiscalYear) {
        return svc.sequenceContinuity(TenantContext.getCurrentShopId(), table, numberCol, fiscalYear);
    }

    @GetMapping("/print-audit")
    public List<Map<String, Object>> printAudit(@RequestParam(defaultValue = "2") int minPrints) {
        return svc.printAudit(TenantContext.getCurrentShopId(), minPrints);
    }

    @GetMapping("/einvoice-coverage")
    public Map<String, Object> eInvoiceCoverage() {
        return svc.eInvoiceCoverage(TenantContext.getCurrentShopId());
    }

    @GetMapping("/eway-coverage")
    public Map<String, Object> eWayBillCoverage() {
        return svc.eWayBillCoverage(TenantContext.getCurrentShopId());
    }
}
