package com.desitech.vyaparsathi.accounting.service;

import com.desitech.vyaparsathi.accounting.dto.DebitNoteCreateDto;
import com.desitech.vyaparsathi.accounting.dto.DebitNoteDto;
import com.desitech.vyaparsathi.accounting.entity.DebitNote;
import com.desitech.vyaparsathi.accounting.repository.DebitNoteRepository;
import com.desitech.vyaparsathi.common.annotations.LogAudit;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoice;
import com.desitech.vyaparsathi.purchases.repository.PurchaseInvoiceRepository;
import com.desitech.vyaparsathi.supplier.dto.SupplierDto;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import com.desitech.vyaparsathi.supplier.service.SupplierLedgerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class DebitNoteService {

    private final DebitNoteRepository debitRepo;
    private final PurchaseInvoiceRepository purchaseRepo;
    private final SupplierRepository supplierRepo;
    private final SupplierLedgerService ledgerService;

    public DebitNoteService(DebitNoteRepository debitRepo,
                            PurchaseInvoiceRepository purchaseRepo,
                            SupplierRepository supplierRepo,
                            SupplierLedgerService ledgerService) {
        this.debitRepo = debitRepo;
        this.purchaseRepo = purchaseRepo;
        this.supplierRepo = supplierRepo;
        this.ledgerService = ledgerService;
    }

    @Transactional
    @LogAudit(action = "CREATE_DEBIT_NOTE", entity = "DEBIT_NOTE")
    public DebitNoteDto createDebitNote(DebitNoteCreateDto createDto) {
        Long shopId = TenantUtils.getCurrentShopId();

        PurchaseInvoice purchase = null;
        if (createDto.getPurchaseInvoiceId() != null) {
            purchase = purchaseRepo.findById(createDto.getPurchaseInvoiceId()).orElse(null);
        }

        Supplier supplier = null;
        if (createDto.getSupplierId() != null) {
            supplier = supplierRepo.findById(createDto.getSupplierId()).orElse(null);
        } else if (purchase != null) {
            supplier = purchase.getSupplier();
        }

        if (supplier == null) {
            throw new IllegalArgumentException("Supplier is required to issue a Debit Note");
        }

        DebitNote note = new DebitNote();
        note.setDebitNoteNo("DN/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/" + (System.currentTimeMillis() % 100000));
        note.setPurchaseInvoice(purchase);
        note.setSupplier(supplier);
        note.setDebitNoteDate(createDto.getDebitNoteDate() != null ? createDto.getDebitNoteDate() : LocalDate.now());
        note.setReason(createDto.getReason());

        BigDecimal taxable = createDto.getTaxableAmount();
        BigDecimal cgst = createDto.getCgstAmount() != null ? createDto.getCgstAmount() : BigDecimal.ZERO;
        BigDecimal sgst = createDto.getSgstAmount() != null ? createDto.getSgstAmount() : BigDecimal.ZERO;
        BigDecimal igst = createDto.getIgstAmount() != null ? createDto.getIgstAmount() : BigDecimal.ZERO;
        BigDecimal total = taxable.add(cgst).add(sgst).add(igst);

        note.setTaxableAmount(taxable);
        note.setCgstAmount(cgst);
        note.setSgstAmount(sgst);
        note.setIgstAmount(igst);
        note.setTotalAmount(total);
        note.setNotes(createDto.getNotes());
        note.setStatus("ISSUED");

        DebitNote saved = debitRepo.save(note);

        // Automatically record Debit Entry in Supplier Ledger (reduces vendor payable)
        ledgerService.recordEntry(supplier, "DEBIT_NOTE", saved.getDebitNoteNo(),
                total, BigDecimal.ZERO, "Debit Note: " + saved.getReason());

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<DebitNoteDto> getAllDebitNotes(Pageable pageable) {
        Long shopId = TenantUtils.getCurrentShopId();
        return debitRepo.findAllByShopId(shopId, pageable).map(this::toDto);
    }

    private DebitNoteDto toDto(DebitNote entity) {
        DebitNoteDto dto = new DebitNoteDto();
        dto.setId(entity.getId());
        dto.setDebitNoteNo(entity.getDebitNoteNo());
        dto.setPurchaseInvoiceId(entity.getPurchaseInvoice() != null ? entity.getPurchaseInvoice().getId() : null);
        dto.setPurchaseInvoiceNo(entity.getPurchaseInvoice() != null ? entity.getPurchaseInvoice().getPurchaseInvoiceNo() : null);

        if (entity.getSupplier() != null) {
            SupplierDto supp = new SupplierDto();
            supp.setId(entity.getSupplier().getId());
            supp.setSupplierName(entity.getSupplier().getSupplierName());
            supp.setPhone(entity.getSupplier().getPhone());
            supp.setGstNumber(entity.getSupplier().getGstNumber());
            dto.setSupplier(supp);
        }

        dto.setDebitNoteDate(entity.getDebitNoteDate());
        dto.setReason(entity.getReason());
        dto.setTaxableAmount(entity.getTaxableAmount());
        dto.setCgstAmount(entity.getCgstAmount());
        dto.setSgstAmount(entity.getSgstAmount());
        dto.setIgstAmount(entity.getIgstAmount());
        dto.setTotalAmount(entity.getTotalAmount());
        dto.setStatus(entity.getStatus());
        dto.setNotes(entity.getNotes());

        return dto;
    }
}
