package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.notification.service.EmailService;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingNotificationLog;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;
import com.desitech.vyaparsathi.receiving.repository.ReceivingNotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fans notifications out to email + a persistent audit log. Every send goes
 * through {@link #record(Receiving, Long, String, String, String, String)}
 * so a supervisor can always answer "was the manager pinged about that GRN?"
 * from a single table (mirrors the delivery-log pattern used elsewhere).
 *
 * <p>The email dispatch is best-effort: a failure is recorded on the log row
 * with the exception message, but never bubbles up to interrupt the business
 * flow that triggered the notification.
 */
@Service
public class ReceivingNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ReceivingNotificationService.class);

    private final ReceivingNotificationLogRepository logRepository;

    @Autowired(required = false)
    private EmailService emailService;

    @Value("${app.receiving.notify.manager-email:}")
    private String defaultManagerEmail;

    public ReceivingNotificationService(ReceivingNotificationLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Async
    @Transactional
    public void onGrnCreated(Receiving r) {
        String subject = "GRN " + r.getGrNumber() + " created";
        String body = "Goods Receipt Note " + r.getGrNumber()
                + " has been created against PO "
                + (r.getPurchaseOrder() != null ? r.getPurchaseOrder().getPoNumber() : "-")
                + ". Please review and approve when ready.";
        record(r, null, "GRN_CREATED", "EMAIL", defaultManagerEmail, subject, body);
    }

    @Async
    @Transactional
    public void onApprovalRequested(Receiving r, String approverEmail) {
        String recipient = approverEmail != null && !approverEmail.isBlank() ? approverEmail : defaultManagerEmail;
        String subject = "Approval requested for GRN " + r.getGrNumber();
        String body = "GRN " + r.getGrNumber() + " requires your approval. Please open the receiving module to review.";
        record(r, null, "APPROVAL_REQUESTED", "EMAIL", recipient, subject, body);
    }

    @Async
    @Transactional
    public void onTicketOpened(ReceivingTicket t) {
        String subject = "New dispute ticket #" + t.getId()
                + (t.getReason() != null ? " · " + t.getReason() : "");
        String body = "A new dispute ticket has been raised: " + t.getDescription();
        record(t.getReceiving(), t.getId(), "TICKET_OPENED", "EMAIL", defaultManagerEmail, subject, body);
    }

    /** Scheduled job hook: escalate any ticket that has been OPEN for > 24 h. */
    @Async
    @Transactional
    public void onAgingTicket(ReceivingTicket t) {
        String subject = "AGING: Ticket #" + t.getId() + " has been OPEN > 24h";
        String body = "Escalation: ticket #" + t.getId()
                + " raised on " + (t.getRaisedAt() != null ? t.getRaisedAt() : "-")
                + " is still unresolved.";
        record(t.getReceiving(), t.getId(), "TICKET_AGING", "EMAIL", defaultManagerEmail, subject, body);
    }

    private void record(Receiving r, Long ticketId, String event, String channel,
                        String recipient, String subject, String body) {
        ReceivingNotificationLog entry = new ReceivingNotificationLog();
        entry.setReceivingId(r != null ? r.getId() : null);
        entry.setTicketId(ticketId);
        entry.setEvent(event);
        entry.setChannel(channel);
        entry.setRecipient(recipient);
        entry.setSubject(subject);
        entry.setBody(body);
        entry.setSentAt(LocalDateTime.now());
        entry.setShop(r != null ? r.getShop() : null);

        try {
            if (emailService != null && recipient != null && !recipient.isBlank()) {
                emailService.sendEmail(recipient, subject, body);
                entry.setStatus("SENT");
            } else {
                entry.setStatus("SKIPPED");
                entry.setErrorNote("No recipient / email service unavailable");
            }
        } catch (Exception e) {
            entry.setStatus("FAILED");
            entry.setErrorNote(e.getMessage());
            log.warn("Notification for {} failed: {}", event, e.getMessage());
        }
        logRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<ReceivingNotificationLog> getLog(Long receivingId) {
        return logRepository.findByReceivingIdOrderBySentAtDesc(receivingId);
    }
}
