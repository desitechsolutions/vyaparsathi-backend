package com.desitech.vyaparsathi.receiving.controller;

import com.desitech.vyaparsathi.receiving.entity.QcSample;
import com.desitech.vyaparsathi.receiving.entity.TemperatureLog;
import com.desitech.vyaparsathi.receiving.service.QcSampleService;
import com.desitech.vyaparsathi.receiving.service.TemperatureLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * QC sample + temperature log endpoints. Both are receipt-adjacent — the FE
 * surfaces them as tabs on the GRN detail page. Kept in a dedicated controller
 * so the main ReceivingController doesn't balloon further.
 */
@RestController
@RequestMapping("/api/receiving")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
public class QcTemperatureController {

    private final QcSampleService qcSampleService;
    private final TemperatureLogService temperatureLogService;

    public QcTemperatureController(QcSampleService qcSampleService,
                                   TemperatureLogService temperatureLogService) {
        this.qcSampleService = qcSampleService;
        this.temperatureLogService = temperatureLogService;
    }

    // ── QC samples ─────────────────────────────────────────────────────

    @PostMapping("/qc-samples")
    public ResponseEntity<QcSample> recordQc(@RequestBody Map<String, Object> body) {
        QcSample s = new QcSample();
        s.setReceivingId(body.get("receivingId") != null ? Long.valueOf(body.get("receivingId").toString()) : null);
        s.setReceivingItemId(body.get("receivingItemId") != null ? Long.valueOf(body.get("receivingItemId").toString()) : null);
        s.setItemVariantId(Long.valueOf(body.get("itemVariantId").toString()));
        s.setBatchNumber(body.get("batchNumber") != null ? body.get("batchNumber").toString() : null);
        s.setSampleSize(Integer.parseInt(body.get("sampleSize").toString()));
        s.setDefectsFound(body.get("defectsFound") != null ? Integer.parseInt(body.get("defectsFound").toString()) : 0);
        s.setInspectedBy(body.get("inspectedBy") != null ? body.get("inspectedBy").toString() : null);
        s.setNotes(body.get("notes") != null ? body.get("notes").toString() : null);
        BigDecimal threshold = body.get("aqlThresholdPct") != null
                ? new BigDecimal(body.get("aqlThresholdPct").toString()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(qcSampleService.record(s, threshold));
    }

    @GetMapping("/{receivingId}/qc-samples")
    public ResponseEntity<List<QcSample>> listForReceiving(@PathVariable Long receivingId) {
        return ResponseEntity.ok(qcSampleService.listForReceiving(receivingId));
    }

    // ── Temperature logs ───────────────────────────────────────────────

    @PostMapping("/temperature-logs")
    public ResponseEntity<TemperatureLog> recordTemp(@RequestBody Map<String, Object> body) {
        TemperatureLog t = new TemperatureLog();
        t.setReceivingId(body.get("receivingId") != null ? Long.valueOf(body.get("receivingId").toString()) : null);
        t.setTemperatureC(new BigDecimal(body.get("temperatureC").toString()));
        if (body.get("humidityPct") != null) t.setHumidityPct(new BigDecimal(body.get("humidityPct").toString()));
        if (body.get("location") != null) t.setLocation(body.get("location").toString());
        if (body.get("loggedBy") != null) t.setLoggedBy(body.get("loggedBy").toString());
        if (body.get("notes") != null) t.setNotes(body.get("notes").toString());
        BigDecimal minC = body.get("minC") != null ? new BigDecimal(body.get("minC").toString()) : null;
        BigDecimal maxC = body.get("maxC") != null ? new BigDecimal(body.get("maxC").toString()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(temperatureLogService.record(t, minC, maxC));
    }

    @GetMapping("/{receivingId}/temperature-logs")
    public ResponseEntity<List<TemperatureLog>> listTemp(@PathVariable Long receivingId) {
        return ResponseEntity.ok(temperatureLogService.listForReceiving(receivingId));
    }
}
