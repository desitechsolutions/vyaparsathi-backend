package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.accounting.dto.DebitNoteCreateDto;
import com.desitech.vyaparsathi.accounting.dto.DebitNoteDto;
import com.desitech.vyaparsathi.accounting.service.DebitNoteService;
import com.desitech.vyaparsathi.common.exception.BusinessValidationException;
import com.desitech.vyaparsathi.common.exception.ResourceNotFoundException;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.entity.ReceivingTicket;
import com.desitech.vyaparsathi.receiving.repository.ReceivingTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One-click debit-note issuance from a receiving dispute ticket. Financial
 * recovery for damaged / rejected units becomes:
 *
 * <pre>
 *   Ticket OPEN → issue debit note → auto-link back on ticket.debitNoteId
 * </pre>
 *
 * The taxable amount is computed as (damagedQty + rejectedQty) × unitCost
 * across the linked GRN's lines. GST split defaults to 0 (many suppliers
 * accept the debit inclusive of tax); the FE can override before submission.
 */
@Service
public class TicketDebitNoteService {

    private static final Logger log = LoggerFactory.getLogger(TicketDebitNoteService.class);

    private final ReceivingTicketRepository ticketRepository;
    private final DebitNoteService debitNoteService;

    public TicketDebitNoteService(ReceivingTicketRepository ticketRepository,
                                  DebitNoteService debitNoteService) {
        this.ticketRepository = ticketRepository;
        this.debitNoteService = debitNoteService;
    }

    @Transactional
    public DebitNoteDto issueDebitNoteForTicket(Long ticketId, String notes) {
        ReceivingTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        if (ticket.getDebitNoteId() != null) {
            throw new BusinessValidationException(
                    "A debit note (id=" + ticket.getDebitNoteId() + ") already exists for this ticket.");
        }

        Receiving r = ticket.getReceiving();
        if (r == null || r.getPurchaseOrder() == null || r.getPurchaseOrder().getSupplier() == null) {
            throw new BusinessValidationException("Ticket is not linked to a supplier — cannot issue debit note.");
        }

        BigDecimal taxable = BigDecimal.ZERO;
        if (r.getItems() != null) {
            for (ReceivingItem it : r.getItems()) {
                int rejectedUnits = Optional.ofNullable(it.getDamagedQty()).orElse(0)
                        + Optional.ofNullable(it.getRejectedQty()).orElse(0);
                if (rejectedUnits == 0) continue;
                PurchaseOrderItem poItem = it.getPurchaseOrderItem();
                BigDecimal cost = it.getUnitCost() != null
                        ? it.getUnitCost()
                        : (poItem != null ? Optional.ofNullable(poItem.getUnitCost()).orElse(BigDecimal.ZERO) : BigDecimal.ZERO);
                taxable = taxable.add(cost.multiply(BigDecimal.valueOf(rejectedUnits)));
            }
        }
        if (taxable.signum() <= 0) {
            throw new BusinessValidationException(
                    "Ticket has no damaged or rejected units — nothing to bill back to the supplier.");
        }

        DebitNoteCreateDto dto = new DebitNoteCreateDto();
        dto.setSupplierId(r.getPurchaseOrder().getSupplier().getId());
        dto.setDebitNoteDate(LocalDate.now());
        dto.setReason("Receiving dispute #" + ticket.getId()
                + (ticket.getReason() != null ? " · " + ticket.getReason() : ""));
        dto.setTaxableAmount(taxable.setScale(2, RoundingMode.HALF_UP));
        dto.setNotes(notes != null && !notes.isBlank() ? notes : ticket.getDescription());

        DebitNoteDto created = debitNoteService.createDebitNote(dto);
        if (created != null && created.getId() != null) {
            ticket.setDebitNoteId(created.getId());
            ticketRepository.save(ticket);
        }
        log.info("Issued debit note {} for ticket #{}", created != null ? created.getDebitNoteNo() : "?", ticket.getId());
        return created;
    }
}
