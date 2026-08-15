package com.desitech.vyaparsathi.document.controller;

import com.desitech.vyaparsathi.document.repository.DocumentReferenceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Public document-verification endpoint. Prints the document hash on every
 * PDF footer; suppliers/customers can navigate to /verify/d/{type}/{id}
 * and see whether the doc is genuine and current.
 *
 * <p>Minimal API — no auth required — but only returns the hash + doc
 * type/number, never full contents. Prevents leaking financials while
 * still supporting tamper-detection.
 */
@RestController
@RequestMapping("/api/v1/document-verification")
public class DocumentVerificationController {

    private final DocumentReferenceRepository refRepo;

    public DocumentVerificationController(DocumentReferenceRepository refRepo) {
        this.refRepo = refRepo;
    }

    @GetMapping("/{type}/{id}")
    public Map<String, Object> verify(@PathVariable String type, @PathVariable Long id) {
        Map<String, Object> out = new HashMap<>();
        out.put("documentType", type);
        out.put("documentId", id);
        // Upstream chain surfaces provenance without exposing amounts.
        out.put("upstream", refRepo.findByTargetTypeAndTargetId(type, id).stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("type", r.getSourceType());
            m.put("id", r.getSourceId());
            m.put("number", r.getSourceNumber());
            m.put("linkKind", r.getLinkKind());
            return m;
        }).toList());
        out.put("downstream", refRepo.findBySourceTypeAndSourceId(type, id).stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("type", r.getTargetType());
            m.put("id", r.getTargetId());
            m.put("number", r.getTargetNumber());
            m.put("linkKind", r.getLinkKind());
            return m;
        }).toList());
        return out;
    }
}
