package com.desitech.vyaparsathi.gst.controller;

import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.gst.dto.Gstr1ExportDto;
import com.desitech.vyaparsathi.gst.dto.Gstr3bExportDto;
import com.desitech.vyaparsathi.gst.dto.Gstr3bSummaryDto;
import com.desitech.vyaparsathi.gst.dto.Gstr9SummaryDto;
import com.desitech.vyaparsathi.gst.dto.HsnPreviewResponseDto;
import com.desitech.vyaparsathi.gst.dto.JurisdictionResolveRequest;
import com.desitech.vyaparsathi.gst.dto.JurisdictionResolveResponse;
import com.desitech.vyaparsathi.gst.service.GstJurisdictionService;
import com.desitech.vyaparsathi.gst.service.GstTaxService;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
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

    private final GstTaxService          gstService;
    private final GstJurisdictionService  jurisdictionService;
    private final ShopRepository          shopRepository;

    public GstTaxController(GstTaxService gstService,
                            GstJurisdictionService jurisdictionService,
                            ShopRepository shopRepository) {
        this.gstService          = gstService;
        this.jurisdictionService = jurisdictionService;
        this.shopRepository      = shopRepository;
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

    /** HSN Table 12 preview — used by the on-screen review widget before download. */
    @GetMapping("/gstr1/hsn-preview")
    public ResponseEntity<ApiResponse<HsnPreviewResponseDto>> hsnPreview(
            @RequestParam int year,
            @RequestParam int month) {
        HsnPreviewResponseDto dto = gstService.buildHsnPreview(year, month);
        return ResponseEntity.ok(new ApiResponse<>("success", "HSN preview generated", dto));
    }

    @GetMapping("/gstr3b")
    public ResponseEntity<ApiResponse<Gstr3bSummaryDto>> getGstr3b(
            @RequestParam int year,
            @RequestParam int month) {
        Gstr3bSummaryDto dto = gstService.generateGstr3b(year, month);
        return ResponseEntity.ok(new ApiResponse<>("success", "GSTR-3B Tax Summary generated successfully", dto));
    }

    /** Download GSTR-3B as GSTN portal-filing JSON. */
    @GetMapping("/gstr3b/download")
    public ResponseEntity<ApiResponse<Gstr3bExportDto>> downloadGstr3b(
            @RequestParam int year,
            @RequestParam int month) {
        Gstr3bExportDto dto = gstService.buildGstr3bExport(year, month);
        return ResponseEntity.ok(new ApiResponse<>("success", "GSTR-3B export JSON generated", dto));
    }

    /**
     * Server-authoritative jurisdiction resolver — returns whether a transaction
     * is intra-state or inter-state, the resolved state codes, and which GST
     * components apply (CGST+SGST, CGST+UTGST, or IGST).
     *
     * <p>The frontend should call this before rendering the line-item tax grid so
     * it shows the correct column headings without hard-coding jurisdiction logic.
     *
     * <p>The shop's state code is resolved from the JWT-scoped tenant; the
     * {@code shopId} field in the request body is advisory and ignored when the
     * JWT already identifies the tenant.
     */
    @GetMapping("/gstr9/summary")
    public ResponseEntity<ApiResponse<Gstr9SummaryDto>> getGstr9Summary(
            @RequestParam int fiscalYear) {
        Gstr9SummaryDto dto = gstService.generateGstr9(fiscalYear);
        return ResponseEntity.ok(new ApiResponse<>("success", "GSTR-9 annual summary generated", dto));
    }

    @PostMapping("/resolve-jurisdiction")
    public ResponseEntity<ApiResponse<JurisdictionResolveResponse>> resolveJurisdiction(
            @RequestBody JurisdictionResolveRequest request) {
        Long shopId = TenantContext.getCurrentShopId();
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + shopId));
        String shopStateCode = jurisdictionService.resolveStateCode(shop).orElse(null);
        JurisdictionResolveResponse response = jurisdictionService.resolveJurisdiction(shopStateCode, request);
        return ResponseEntity.ok(new ApiResponse<>("success", "Jurisdiction resolved", response));
    }
}
