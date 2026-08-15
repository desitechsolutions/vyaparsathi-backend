package com.desitech.vyaparsathi.einvoice.controller;

import com.desitech.vyaparsathi.einvoice.entity.EInvoice;
import com.desitech.vyaparsathi.einvoice.entity.EWayBill;
import com.desitech.vyaparsathi.einvoice.repository.EInvoiceRepository;
import com.desitech.vyaparsathi.einvoice.repository.EWayBillRepository;
import com.desitech.vyaparsathi.einvoice.service.EWayBillService;
import com.desitech.vyaparsathi.einvoice.service.IrpService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Generate + cancel IRN / e-way bill for a printable document. The controller
 * accepts any {@code documentType} (TAX_INVOICE / DEBIT_NOTE / CREDIT_NOTE
 * / DELIVERY_CHALLAN) so a single UI action wires every doc category.
 */
@RestController
@RequestMapping("/api/v1/einvoice")
public class EInvoiceController {

    private final IrpService irpService;
    private final EWayBillService ewbService;
    private final EInvoiceRepository eInvoiceRepository;
    private final EWayBillRepository eWayBillRepository;

    public EInvoiceController(IrpService irpService, EWayBillService ewbService,
                              EInvoiceRepository eInvoiceRepository,
                              EWayBillRepository eWayBillRepository) {
        this.irpService = irpService;
        this.ewbService = ewbService;
        this.eInvoiceRepository = eInvoiceRepository;
        this.eWayBillRepository = eWayBillRepository;
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        String docType = str(body.get("documentType"));
        Long docId = num(body.get("documentId"));
        String docNumber = str(body.get("documentNumber"));
        if (docType == null || docId == null) return ResponseEntity.badRequest().build();
        EInvoice ei = irpService.generate(docType, docId, docNumber, body);
        return ResponseEntity.ok(toMap(ei));
    }

    @GetMapping("/document/{docType}/{docId}")
    public ResponseEntity<Map<String, Object>> current(@PathVariable String docType, @PathVariable Long docId) {
        return eInvoiceRepository.findFirstByDocumentTypeAndDocumentIdAndStatus(docType, docId, "GENERATED")
                .map(ei -> ResponseEntity.ok(toMap(ei)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@RequestBody Map<String, Object> body) {
        String irn = str(body.get("irn"));
        String reason = str(body.get("reason"));
        if (irn == null) return ResponseEntity.badRequest().build();
        EInvoice ei = irpService.cancel(irn, reason);
        return ResponseEntity.ok(toMap(ei));
    }

    @PostMapping("/eway/generate")
    public ResponseEntity<Map<String, Object>> generateEwb(@RequestBody Map<String, Object> body) {
        String docType = str(body.get("documentType"));
        Long docId = num(body.get("documentId"));
        String docNumber = str(body.get("documentNumber"));
        if (docType == null || docId == null) return ResponseEntity.badRequest().build();
        EWayBill w = ewbService.generate(docType, docId, docNumber, body);
        return ResponseEntity.ok(toMapEwb(w));
    }

    @GetMapping("/eway/document/{docType}/{docId}")
    public ResponseEntity<Map<String, Object>> currentEwb(@PathVariable String docType, @PathVariable Long docId) {
        return eWayBillRepository.findFirstByDocumentTypeAndDocumentIdAndStatus(docType, docId, "ACTIVE")
                .map(w -> ResponseEntity.ok(toMapEwb(w)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/eway/cancel")
    public ResponseEntity<Map<String, Object>> cancelEwb(@RequestBody Map<String, Object> body) {
        String ewb = str(body.get("ewbNumber"));
        String reason = str(body.get("reason"));
        if (ewb == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(toMapEwb(ewbService.cancel(ewb, reason)));
    }

    private Map<String, Object> toMap(EInvoice ei) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", ei.getId());
        m.put("documentType", ei.getDocumentType());
        m.put("documentId", ei.getDocumentId());
        m.put("documentNumber", ei.getDocumentNumber());
        m.put("irn", ei.getIrn());
        m.put("ackNumber", ei.getAckNumber());
        m.put("ackDate", ei.getAckDate());
        m.put("qrPayload", ei.getQrPayload());
        m.put("status", ei.getStatus());
        m.put("cancellationReason", ei.getCancellationReason());
        m.put("cancelledAt", ei.getCancelledAt());
        return m;
    }

    private Map<String, Object> toMapEwb(EWayBill w) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", w.getId());
        m.put("documentType", w.getDocumentType());
        m.put("documentId", w.getDocumentId());
        m.put("ewbNumber", w.getEwbNumber());
        m.put("generatedAt", w.getGeneratedAt());
        m.put("validTill", w.getValidTill());
        m.put("distanceKm", w.getDistanceKm());
        m.put("transporterName", w.getTransporterName());
        m.put("vehicleNumber", w.getVehicleNumber());
        m.put("status", w.getStatus());
        return m;
    }

    private String str(Object v) { return v == null ? null : v.toString(); }
    private Long num(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }
}
