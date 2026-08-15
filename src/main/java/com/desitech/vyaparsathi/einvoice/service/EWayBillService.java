package com.desitech.vyaparsathi.einvoice.service;

import com.desitech.vyaparsathi.einvoice.entity.EWayBill;
import com.desitech.vyaparsathi.einvoice.repository.EWayBillRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mock e-Way Bill capture — same pattern as IRP. Real integrations replace
 * this bean with a client for the NIC EWB API. The mock is idempotent and
 * generates a random-ish 12-digit EWB number for demo/testing.
 */
@Service
public class EWayBillService {

    private final EWayBillRepository repository;

    public EWayBillService(EWayBillRepository repository) {
        this.repository = repository;
    }

    public EWayBill generate(String documentType, Long documentId, String documentNumber, Map<String, Object> payload) {
        return repository.findFirstByDocumentTypeAndDocumentIdAndStatus(documentType, documentId, "ACTIVE")
                .orElseGet(() -> {
                    EWayBill w = new EWayBill();
                    w.setDocumentType(documentType);
                    w.setDocumentId(documentId);
                    w.setDocumentNumber(documentNumber);
                    long stamp = System.currentTimeMillis() % 900000000000L;
                    w.setEwbNumber(String.valueOf(100000000000L + stamp));
                    w.setGeneratedAt(LocalDateTime.now());
                    w.setValidTill(LocalDateTime.now().plusDays(1));
                    if (payload != null) {
                        Object dist = payload.get("distanceKm");
                        if (dist instanceof Number n) w.setDistanceKm(n.intValue());
                        Object trGstin = payload.get("transporterGstin");
                        if (trGstin != null) w.setTransporterGstin(trGstin.toString());
                        Object trName = payload.get("transporterName");
                        if (trName != null) w.setTransporterName(trName.toString());
                        Object veh = payload.get("vehicleNumber");
                        if (veh != null) w.setVehicleNumber(veh.toString());
                        Object mode = payload.get("transportMode");
                        if (mode != null) w.setTransportMode(mode.toString());
                    }
                    w.setStatus("ACTIVE");
                    return repository.save(w);
                });
    }

    public EWayBill cancel(String ewbNumber, String reason) {
        return repository.findByEwbNumber(ewbNumber).map(w -> {
            if ("CANCELLED".equals(w.getStatus())) return w;
            w.setStatus("CANCELLED");
            return repository.save(w);
        }).orElseThrow(() -> new IllegalArgumentException("Unknown EWB: " + ewbNumber));
    }
}
