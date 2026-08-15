package com.desitech.vyaparsathi.document.service;

import com.desitech.vyaparsathi.document.dto.EnterpriseDocumentDto;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Computes a SHA-256 hash over the canonical fields of an enterprise
 * document — used for tamper detection in the footer of every PDF.
 *
 * <p>Only stable, business-meaningful fields are hashed (doc number,
 * date, totals, line count). Cosmetic properties like watermark or
 * print count are deliberately excluded — the hash of a valid document
 * must not change just because someone reprinted it.
 */
@Service
public class DocumentIntegrityHashService {

    public String hash(EnterpriseDocumentDto doc) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(doc.getDocumentType() != null ? doc.getDocumentType().name() : "")
              .append('|')
              .append(doc.getDocumentNumber() != null ? doc.getDocumentNumber() : "")
              .append('|')
              .append(doc.getDocumentDate() != null ? doc.getDocumentDate() : "")
              .append('|')
              .append(doc.getRevisionNumber() != null ? doc.getRevisionNumber() : 0)
              .append('|')
              .append(doc.getIssuer() != null && doc.getIssuer().getGstin() != null ? doc.getIssuer().getGstin() : "")
              .append('|')
              .append(doc.getCounterparty() != null && doc.getCounterparty().getGstin() != null ? doc.getCounterparty().getGstin() : "")
              .append('|')
              .append(doc.getItems() != null ? doc.getItems().size() : 0)
              .append('|');
            if (doc.getTotals() != null) {
                sb.append(doc.getTotals().getSubtotal() != null ? doc.getTotals().getSubtotal().toPlainString() : "0").append('|')
                  .append(doc.getTotals().getCgstAmount() != null ? doc.getTotals().getCgstAmount().toPlainString() : "0").append('|')
                  .append(doc.getTotals().getSgstAmount() != null ? doc.getTotals().getSgstAmount().toPlainString() : "0").append('|')
                  .append(doc.getTotals().getIgstAmount() != null ? doc.getTotals().getIgstAmount().toPlainString() : "0").append('|')
                  .append(doc.getTotals().getGrandTotal() != null ? doc.getTotals().getGrandTotal().toPlainString() : "0");
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : out) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            // Non-fatal: hash is a diagnostic, not a functional feature.
            return null;
        }
    }
}
