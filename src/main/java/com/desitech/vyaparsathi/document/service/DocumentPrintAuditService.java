package com.desitech.vyaparsathi.document.service;

import com.desitech.vyaparsathi.common.enums.DocumentType;
import com.desitech.vyaparsathi.document.entity.DocumentPrintAudit;
import com.desitech.vyaparsathi.document.repository.DocumentPrintAuditRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one row to {@code document_print_audit} for every PDF download /
 * print request. FIRE-AND-FORGET by design — this service is invoked from
 * PDF-generation code paths that MUST succeed regardless of audit outcome.
 *
 * <p><b>Two-layer exception isolation</b> because Spring's transaction
 * interceptor can throw {@code UnexpectedRollbackException} *after* the
 * inner method returns (during commit), which a plain in-method try/catch
 * cannot intercept:
 * <ol>
 *   <li>Public {@link #recordPrint} is NOT transactional; it wraps
 *       {@link #doRecord} in a broad try/catch so any exception —
 *       including one from Spring's commit stage — is absorbed here.</li>
 *   <li>Internal {@link #doRecord} is {@code @Transactional(REQUIRES_NEW)}
 *       so if the audit save fails (e.g. no {@code TenantContext} on a
 *       signed-URL PDF path), only the audit's own transaction rolls
 *       back — the caller's transaction is untouched.</li>
 * </ol>
 * The invocation goes through the Spring proxy via a {@link Lazy}
 * self-reference (self-invocation would bypass AOP).
 *
 * <p>Enable / disable via {@code document.print-audit.enabled} (default on).
 */
@Service
public class DocumentPrintAuditService {

    private static final Logger log = LoggerFactory.getLogger(DocumentPrintAuditService.class);

    private final DocumentPrintAuditRepository repository;
    private final EntityManager entityManager;

    @Value("${document.print-audit.enabled:true}")
    private boolean enabled;

    /** Self-reference so {@link #recordPrint} can hit {@link #doRecord}
     *  through the Spring proxy (needed for {@code @Transactional} to apply). */
    @Autowired
    @Lazy
    private DocumentPrintAuditService self;

    public DocumentPrintAuditService(DocumentPrintAuditRepository repository,
                                     EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    /**
     * Fire-and-forget entry point — absorbs every failure so a broken audit
     * write cannot break a PDF download.
     */
    public void recordPrint(DocumentType type, Long documentId,
                            String documentNumber, String documentHash) {
        recordPrint(type, documentId, documentNumber, documentHash, null);
    }

    /**
     * Overload that accepts an explicit {@code shopId} for paths where
     * {@code TenantContext} is unavailable (e.g. signed-URL / public PDF
     * endpoints). The shop is set directly on the row so the audit record
     * is correctly attributed to the right tenant.
     */
    public void recordPrint(DocumentType type, Long documentId,
                            String documentNumber, String documentHash,
                            Long shopId) {
        if (!enabled) return;
        if (type == null || documentId == null) return;
        try {
            self.doRecord(type, documentId, documentNumber, documentHash, shopId);
        } catch (Exception e) {
            // Catches BOTH exceptions thrown from doRecord's body AND
            // UnexpectedRollbackException thrown by Spring's proxy at commit.
            log.warn("DocumentPrintAudit: failed to record print for {}#{}: {}",
                    type, documentId, e.getMessage());
        }
    }

    /**
     * Isolated audit-write transaction. Public (not private) so Spring's
     * CGLIB proxy can intercept it — private methods are ignored by AOP.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void doRecord(DocumentType type, Long documentId,
                         String documentNumber, String documentHash,
                         Long shopId) {
        long prior = repository.countByDocumentTypeAndDocumentId(type, documentId);
        DocumentPrintAudit row = new DocumentPrintAudit();
        row.setDocumentType(type);
        row.setDocumentId(documentId);
        row.setDocumentNumber(documentNumber);
        row.setDocumentHashSnapshot(documentHash);
        row.setIsDuplicate(prior > 0);

        // If shopId is provided (e.g. from a signed-URL path where TenantContext
        // is empty), set the shop directly so the row is tenant-attributed.
        if (shopId != null) {
            row.setShop(entityManager.getReference(Shop.class, shopId));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null
                && !"anonymousUser".equals(auth.getName())) {
            row.setPrintedByName(auth.getName());
        }
        repository.save(row);
    }
}
