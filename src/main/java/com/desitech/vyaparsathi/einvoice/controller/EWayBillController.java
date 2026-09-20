package com.desitech.vyaparsathi.einvoice.controller;

import com.desitech.vyaparsathi.einvoice.entity.EWayBill;
import com.desitech.vyaparsathi.einvoice.service.EWayBillService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for E-Way Bill operations matching frontend /api/v1/ewaybill routes.
 */
@RestController
@RequestMapping("/api/v1/ewaybill")
public class EWayBillController {

    private final EWayBillService ewbService;

    public EWayBillController(EWayBillService ewbService) {
        this.ewbService = ewbService;
    }

    /**
     * Standard statutory E-Way Bill threshold in INR (Rs. 50,000 national standard).
     */
    @GetMapping("/threshold")
    public ResponseEntity<Map<String, Object>> getThreshold() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("threshold", 50000);
        resp.put("data", 50000);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        String docType = body.get("documentType") != null ? body.get("documentType").toString() : "TAX_INVOICE";
        Long docId = body.get("documentId") != null ? ((Number) body.get("documentId")).longValue() : null;
        String docNumber = body.get("documentNumber") != null ? body.get("documentNumber").toString() : null;
        if (docId == null) {
            docId = body.get("saleId") != null ? ((Number) body.get("saleId")).longValue() : null;
        }
        if (docId == null) return ResponseEntity.badRequest().build();

        EWayBill w = ewbService.generate(docType, docId, docNumber, body);
        Map<String, Object> out = new HashMap<>();
        out.put("id", w.getId());
        out.put("ewbNumber", w.getEwbNumber());
        out.put("generatedAt", w.getGeneratedAt());
        out.put("validTill", w.getValidTill());
        out.put("status", w.getStatus());
        return ResponseEntity.ok(Map.of("data", out));
    }
}
