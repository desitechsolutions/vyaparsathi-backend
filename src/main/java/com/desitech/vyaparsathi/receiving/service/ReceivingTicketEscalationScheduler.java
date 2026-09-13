package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;
import com.desitech.vyaparsathi.receiving.enums.ReceivingTicketStatus;
import com.desitech.vyaparsathi.receiving.repository.ReceivingTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hourly sweep for stale dispute tickets. Anything OPEN or IN_PROGRESS longer
 * than {@code app.receiving.ticket.escalate-hours} fires an escalation
 * notification via {@link ReceivingNotificationService}, then flags the ticket
 * so the same row isn't paged again for the current cycle.
 *
 * <p>Scheduling is enabled by the {@code @EnableScheduling} annotation on the
 * app main class — this bean is picked up automatically. The cron runs on the
 * top of every hour; the notification-log entry gives operators an audit trail
 * of every escalation.
 */
@Component
public class ReceivingTicketEscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReceivingTicketEscalationScheduler.class);

    @Value("${app.receiving.ticket.escalate-hours:24}")
    private long escalateHours;

    private final ReceivingTicketRepository ticketRepository;
    private final ReceivingNotificationService notificationService;

    public ReceivingTicketEscalationScheduler(ReceivingTicketRepository ticketRepository,
                                              ReceivingNotificationService notificationService) {
        this.ticketRepository = ticketRepository;
        this.notificationService = notificationService;
    }

    /** Every hour at :07 so it doesn't compete with the top-of-hour cron burst. */
    @Scheduled(cron = "0 7 * * * *")
    public void escalateStaleTickets() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(escalateHours);
        // Eagerly fetches receiving + shop so no lazy proxy is handed to the @Async thread.
        List<ReceivingTicket> stale = ticketRepository.findOpenTicketsOlderThan(cutoff);
        int fired = 0;
        for (ReceivingTicket t : stale) {
            try {
                notificationService.onAgingTicket(t);
                fired++;
            } catch (Exception e) {
                log.warn("Escalation for ticket {} failed: {}", t.getId(), e.getMessage());
            }
        }
        if (fired > 0) {
            log.info("Escalation cron fired {} notifications for tickets older than {}h", fired, escalateHours);
        }
    }
}
