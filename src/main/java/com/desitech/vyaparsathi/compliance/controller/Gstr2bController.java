package com.desitech.vyaparsathi.compliance.controller;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.gst.dto.Gstr2bEntryDto;
import com.desitech.vyaparsathi.gst.dto.Gstr2bReconciliationSummaryDto;
import com.desitech.vyaparsathi.gst.service.Gstr2bReconciler;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/compliance/gstr2b")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class Gstr2bController {

    private final Gstr2bReconciler reconciler;

    public Gstr2bController(Gstr2bReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Gstr2bReconciliationSummaryDto>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam int year,
            @RequestParam int month) {
        Long shopId = TenantContext.getCurrentShopId();
        Gstr2bReconciliationSummaryDto result = reconciler.reconcile(file, shopId, year, month);
        return ResponseEntity.ok(new ApiResponse<>("success", "GSTR-2B reconciliation complete", result));
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<ApiResponse<Gstr2bReconciliationSummaryDto>> getReconciliation(
            @RequestParam int year,
            @RequestParam int month) {
        Long shopId = TenantContext.getCurrentShopId();
        Gstr2bReconciliationSummaryDto result = reconciler.getReconciliation(shopId, year, month);
        if (result == null) {
            return ResponseEntity.ok(new ApiResponse<>("success", "No reconciliation found for period", null));
        }
        return ResponseEntity.ok(new ApiResponse<>("success", "Reconciliation data retrieved", result));
    }

    @PostMapping("/{entryId}/manual-match")
    public ResponseEntity<ApiResponse<Gstr2bEntryDto>> manualMatch(
            @PathVariable Long entryId,
            @RequestParam Long purchaseId) {
        Gstr2bEntryDto result = reconciler.manualMatch(entryId, purchaseId);
        return ResponseEntity.ok(new ApiResponse<>("success", "Manual match applied", result));
    }

    @PostMapping("/{entryId}/accept-mismatch")
    public ResponseEntity<ApiResponse<Gstr2bEntryDto>> acceptMismatch(
            @PathVariable Long entryId) {
        Gstr2bEntryDto result = reconciler.acceptMismatch(entryId);
        return ResponseEntity.ok(new ApiResponse<>("success", "Mismatch accepted", result));
    }
}
