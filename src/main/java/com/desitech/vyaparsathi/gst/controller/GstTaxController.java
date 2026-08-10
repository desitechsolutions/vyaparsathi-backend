package com.desitech.vyaparsathi.gst.controller;

import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.gst.dto.Gstr1ExportDto;
import com.desitech.vyaparsathi.gst.dto.Gstr3bSummaryDto;
import com.desitech.vyaparsathi.gst.service.GstTaxService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gst")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class GstTaxController {

    private final GstTaxService gstService;

    public GstTaxController(GstTaxService gstService) {
        this.gstService = gstService;
    }

    @GetMapping("/gstr1")
    public ResponseEntity<ApiResponse<Gstr1ExportDto>> getGstr1(
            @RequestParam int year,
            @RequestParam int month) {
        Gstr1ExportDto dto = gstService.generateGstr1(year, month);
        return ResponseEntity.ok(new ApiResponse<>("success", "GSTR-1 JSON generated successfully", dto));
    }

    @GetMapping("/gstr3b")
    public ResponseEntity<ApiResponse<Gstr3bSummaryDto>> getGstr3b(
            @RequestParam int year,
            @RequestParam int month) {
        Gstr3bSummaryDto dto = gstService.generateGstr3b(year, month);
        return ResponseEntity.ok(new ApiResponse<>("success", "GSTR-3B Tax Summary generated successfully", dto));
    }
}
