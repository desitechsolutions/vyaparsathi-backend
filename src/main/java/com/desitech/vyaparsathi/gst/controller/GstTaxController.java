package com.desitech.vyaparsathi.gst.controller;

import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.gst.dto.Gstr1ExportDto;
import com.desitech.vyaparsathi.gst.dto.Gstr3bSummaryDto;
import com.desitech.vyaparsathi.gst.service.GstTaxService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    /** Preview GSTR-1 as a structured API response — used by the on-screen review UI. */
    @GetMapping("/gstr1")
    public ResponseEntity<ApiResponse<Gstr1ExportDto>> getGstr1(
            @RequestParam int year,
            @RequestParam int month) {
        Gstr1ExportDto dto = gstService.generateGstr1(year, month);
        return ResponseEntity.ok(new ApiResponse<>("success", "GSTR-1 JSON generated successfully", dto));
    }

    /**
     * Download GSTR-1 as raw JSON — served with application/json content type
     * and an attachment Content-Disposition. Field names on the DTO already
     * match the GSTN schema (gstin, fp, b2b, b2cl, b2cs, cdnr, hsn, etc.),
     * so Jackson's default serialization produces a filable JSON body.
     */
    @GetMapping(value = "/gstr1/download", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Gstr1ExportDto> downloadGstr1(
            @RequestParam int year,
            @RequestParam int month) {
        Gstr1ExportDto dto = gstService.generateGstr1(year, month);
        String filename = String.format("GSTR1_%02d_%d.json", month, year);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(dto);
    }

    @GetMapping("/gstr3b")
    public ResponseEntity<ApiResponse<Gstr3bSummaryDto>> getGstr3b(
            @RequestParam int year,
            @RequestParam int month) {
        Gstr3bSummaryDto dto = gstService.generateGstr3b(year, month);
        return ResponseEntity.ok(new ApiResponse<>("success", "GSTR-3B Tax Summary generated successfully", dto));
    }
}
