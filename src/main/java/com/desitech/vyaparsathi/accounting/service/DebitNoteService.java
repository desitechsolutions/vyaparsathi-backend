package com.desitech.vyaparsathi.accounting.service;

import com.desitech.vyaparsathi.accounting.dto.DebitNoteCreateDto;
import com.desitech.vyaparsathi.accounting.dto.DebitNoteDto;
import com.desitech.vyaparsathi.accounting.entity.DebitNote;
import com.desitech.vyaparsathi.accounting.entity.DebitNoteItem;
import com.desitech.vyaparsathi.accounting.enums.DebitNoteStatus;
import com.desitech.vyaparsathi.accounting.repository.DebitNoteRepository;
import com.desitech.vyaparsathi.common.annotations.LogAudit;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.compliance.exception.LockedPeriodException;
import com.desitech.vyaparsathi.compliance.service.PeriodLockService;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturn;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturnItem;
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
import java.util.Objects;

@Service
public class DebitNoteService {

    private final DebitNoteRepository debitRepo;
    private final PurchaseInvoiceRepository purchaseRepo;
    private final SupplierRepository supplierRepo;
    private final SupplierLedgerService ledgerService;
    private final DebitNoteNumberService debitNoteNumberService;
    private final PeriodLockService periodLockService;

    public DebitNoteService(DebitNoteRepository debitRepo,
                            PurchaseInvoiceRepository purchaseRepo,
                            SupplierRepository supplierRepo,
                            SupplierLedgerService ledgerService,
                            DebitNoteNumberService debitNoteNumberService,
                            PeriodLockService periodLockService) {
        this.debitRepo = debitRepo;
        this.purchaseRepo = purchaseRepo;
        this.supplierRepo = supplierRepo;
        this.ledgerService = ledgerService;
        this.debitNoteNumberService = debitNoteNumberService;
        this.periodLockService = periodLockService;
    }

    @Transactional
    @LogAudit(action = "CREATE_DEBIT_NOTE", entity = "DEBIT_NOTE")
    public DebitNoteDto createDebitNote(DebitNoteCreateDto createDto) {
        Long shopId = TenantUtils.getCurrentShopId();
        LocalDate noteDate = createDto.getDebitNoteDate() != null ? createDto.getDebitNoteDate() : LocalDate.now();
        if (periodLockService.isPeriodLocked(TenantContext.getCurrentShopId(), noteDate)) {
            throw new LockedPeriodException(
                    String.format("%02d-%d", noteDate.getMonthValue(), noteDate.getYear()));
        }

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
        note.setDebitNoteNo(debitNoteNumberService.nextDebitNoteNumber(shopId, noteDate));
        note.setPurchaseInvoice(purchase);
        note.setSupplier(supplier);
        note.setDebitNoteDate(noteDate);
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
        note.setStatus(DebitNoteStatus.ISSUED);

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

    /**
     * Atomically creates a persisted {@link DebitNote} for a just-approved
     * purchase return, with one {@link DebitNoteItem} per returned line.
     * Also records the ledger entry that reduces the supplier's payable.
     *
     * <p>Note: {@link PurchaseReturnItem} carries only pre-tax cost — no GST
     * breakdown — so the item's CGST/SGST/IGST default to zero and
     * {@code taxableValue = totalCost}. A future migration will add GST
     * columns to purchase-side lines.
     */
    @Transactional
    public DebitNote createFromPurchaseReturn(PurchaseReturn purchaseReturn) {
        Objects.requireNonNull(purchaseReturn, "purchaseReturn");
        Supplier supplier = purchaseReturn.getSupplier();
        if (supplier == null) {
            throw new IllegalArgumentException("PurchaseReturn " + purchaseReturn.getId() + " has no supplier — cannot issue debit note");
        }

        Long shopId = purchaseReturn.getShop() != null ? purchaseReturn.getShop().getId() : TenantUtils.getCurrentShopId();
        LocalDate returnDate = purchaseReturn.getReturnDate() != null
                ? purchaseReturn.getReturnDate().toLocalDate() : LocalDate.now();
        if (periodLockService.isPeriodLocked(shopId, returnDate)) {
            throw new LockedPeriodException(
                    String.format("%02d-%d", returnDate.getMonthValue(), returnDate.getYear()));
        }

        DebitNote note = new DebitNote();
        note.setShop(purchaseReturn.getShop());
        note.setSupplier(supplier);
        note.setPurchaseReturn(purchaseReturn);
        note.setDebitNoteDate(LocalDate.now());
        note.setDebitNoteNo(debitNoteNumberService.nextDebitNoteNumber(shopId, note.getDebitNoteDate()));
        note.setReason("Purchase Return - " + purchaseReturn.getReturnNo());
        note.setStatus(DebitNoteStatus.ISSUED);

        BigDecimal taxableTotal = BigDecimal.ZERO;

        if (purchaseReturn.getItems() != null) {
            for (PurchaseReturnItem pri : purchaseReturn.getItems()) {
                BigDecimal qty = pri.getQuantity() != null ? BigDecimal.valueOf(pri.getQuantity()) : BigDecimal.ZERO;
                BigDecimal unitCost = pri.getUnitCost() != null ? pri.getUnitCost() : BigDecimal.ZERO;
                BigDecimal lineTotal = pri.getTotalCost() != null ? pri.getTotalCost() : unitCost.multiply(qty);

                DebitNoteItem item = new DebitNoteItem();
                item.setShop(purchaseReturn.getShop());
                item.setPurchaseReturnItem(pri);
                item.setItemName(lineName(pri));
                item.setHsnSac(lineHsnSac(pri));
                item.setBatchNumber(pri.getBatchNumber());
                item.setQty(qty);
                item.setUnitCost(unitCost);
                item.setTaxableValue(lineTotal);
                item.setTotalAmount(lineTotal);
                note.addItem(item);

                taxableTotal = taxableTotal.add(lineTotal);
            }
        }

        note.setTaxableAmount(taxableTotal);
        note.setTotalAmount(taxableTotal);

        DebitNote saved = debitRepo.save(note);

        // Reduce supplier payable
        ledgerService.recordEntry(supplier, "DEBIT_NOTE", saved.getDebitNoteNo(),
                saved.getTotalAmount(), BigDecimal.ZERO,
                "Debit Note: " + saved.getDebitNoteNo() + " (Return " + purchaseReturn.getReturnNo() + ")");

        return saved;
    }

    private static String lineName(PurchaseReturnItem pri) {
        if (pri.getItemVariant() != null && pri.getItemVariant().getItem() != null
                && pri.getItemVariant().getItem().getName() != null) {
            return pri.getItemVariant().getItem().getName();
        }
        return "Item";
    }

    private static String lineHsnSac(PurchaseReturnItem pri) {
        return pri.getItemVariant() != null ? pri.getItemVariant().getHsn() : null;
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
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        dto.setNotes(entity.getNotes());
        dto.setAppliedAmount(entity.getAppliedAmount());

        return dto;
    }
}
