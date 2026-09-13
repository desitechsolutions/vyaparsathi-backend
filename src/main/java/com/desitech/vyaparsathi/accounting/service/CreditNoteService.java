package com.desitech.vyaparsathi.accounting.service;

import com.desitech.vyaparsathi.accounting.dto.CreditNoteCreateDto;
import com.desitech.vyaparsathi.accounting.dto.CreditNoteDto;
import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.entity.CreditNoteItem;
import com.desitech.vyaparsathi.accounting.enums.CreditNoteStatus;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.common.annotations.LogAudit;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.compliance.exception.LockedPeriodException;
import com.desitech.vyaparsathi.compliance.service.PeriodLockService;
import com.desitech.vyaparsathi.customer.dto.CustomerDto;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CreditNoteService {

    private final CreditNoteRepository creditRepo;
    private final SaleRepository saleRepo;
    private final CustomerRepository customerRepo;
    private final CreditNoteNumberService creditNoteNumberService;
    private final PeriodLockService periodLockService;

    public CreditNoteService(CreditNoteRepository creditRepo,
                             SaleRepository saleRepo,
                             CustomerRepository customerRepo,
                             CreditNoteNumberService creditNoteNumberService,
                             PeriodLockService periodLockService) {
        this.creditRepo = creditRepo;
        this.saleRepo = saleRepo;
        this.customerRepo = customerRepo;
        this.creditNoteNumberService = creditNoteNumberService;
        this.periodLockService = periodLockService;
    }

    @Transactional
    @LogAudit(action = "CREATE_CREDIT_NOTE", entity = "CREDIT_NOTE")
    public CreditNoteDto createCreditNote(CreditNoteCreateDto createDto) {
        Long shopId = TenantUtils.getCurrentShopId();
        LocalDate noteDate = createDto.getCreditNoteDate() != null ? createDto.getCreditNoteDate() : LocalDate.now();
        if (periodLockService.isPeriodLocked(TenantContext.getCurrentShopId(), noteDate)) {
            throw new LockedPeriodException(
                    String.format("%02d-%d", noteDate.getMonthValue(), noteDate.getYear()));
        }

        Sale sale = null;
        if (createDto.getSaleId() != null) {
            sale = saleRepo.findById(createDto.getSaleId()).orElse(null);
        }

        Customer customer = null;
        if (createDto.getCustomerId() != null) {
            customer = customerRepo.findById(createDto.getCustomerId()).orElse(null);
        } else if (sale != null) {
            customer = sale.getCustomer();
        }

        CreditNote note = new CreditNote();
        note.setCreditNoteNo(creditNoteNumberService.nextCreditNoteNumber(shopId, noteDate));
        note.setSale(sale);
        note.setCustomer(customer);
        note.setCreditNoteDate(noteDate);
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
        note.setStatus(CreditNoteStatus.ISSUED);

        CreditNote saved = creditRepo.save(note);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<CreditNoteDto> getAllCreditNotes(Pageable pageable) {
        Long shopId = TenantUtils.getCurrentShopId();
        return creditRepo.findAllByShopId(shopId, pageable).map(this::toDto);
    }

    /**
     * Atomically creates a persisted {@link CreditNote} for a just-processed
     * sales return. Called from {@code SaleService.processSaleReturn}.
     *
     * <p>Each entry in {@code returnedQtys} maps a {@link SaleItem} to the
     * quantity being returned on this pass. Line-level tax amounts are
     * pro-rated from the original {@code SaleItem} using
     * {@code returnedQty / originalQty}. Header totals sum across lines.
     *
     * @return the persisted CreditNote with items attached
     */
    @Transactional
    public CreditNote createFromSaleReturn(Sale sale, Map<SaleItem, BigDecimal> returnedQtys, String reason) {
        Objects.requireNonNull(sale, "sale");
        Objects.requireNonNull(returnedQtys, "returnedQtys");
        Long shopId = sale.getShop() != null ? sale.getShop().getId() : TenantUtils.getCurrentShopId();
        LocalDate saleDate = sale.getDate() != null ? sale.getDate().toLocalDate() : LocalDate.now();
        if (periodLockService.isPeriodLocked(shopId, saleDate)) {
            throw new LockedPeriodException(
                    String.format("%02d-%d", saleDate.getMonthValue(), saleDate.getYear()));
        }

        CreditNote note = new CreditNote();
        note.setShop(sale.getShop());
        note.setSale(sale);
        note.setCustomer(sale.getCustomer());
        note.setCreditNoteDate(LocalDate.now());
        note.setCreditNoteNo(creditNoteNumberService.nextCreditNoteNumber(shopId, note.getCreditNoteDate()));
        note.setReason(reason != null && !reason.isBlank() ? reason : "Sales Return");
        note.setStatus(CreditNoteStatus.ISSUED);

        BigDecimal taxableTotal = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        BigDecimal igstTotal = BigDecimal.ZERO;

        for (Map.Entry<SaleItem, BigDecimal> entry : returnedQtys.entrySet()) {
            SaleItem si = entry.getKey();
            BigDecimal returnQty = entry.getValue();
            if (returnQty == null || returnQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal originalQty = si.getQty() != null ? si.getQty() : BigDecimal.ONE;
            BigDecimal ratio = originalQty.compareTo(BigDecimal.ZERO) > 0
                    ? returnQty.divide(originalQty, 6, RoundingMode.HALF_UP)
                    : BigDecimal.ONE;

            BigDecimal lineTaxable = safe(si.getTaxableValue()).multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineCgst = safe(si.getCgstAmt()).multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineSgst = safe(si.getSgstAmt()).multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineIgst = safe(si.getIgstAmt()).multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineTaxable.add(lineCgst).add(lineSgst).add(lineIgst);

            CreditNoteItem item = new CreditNoteItem();
            item.setShop(sale.getShop());
            item.setSaleItem(si);
            item.setItemName(lineName(si));
            item.setHsnSac(lineHsnSac(si));
            item.setUnit(lineUnit(si));
            item.setQty(returnQty);
            item.setUnitPrice(safe(si.getUnitPrice()));
            item.setDiscount(safe(si.getDiscount()).multiply(ratio).setScale(2, RoundingMode.HALF_UP));
            item.setTaxableValue(lineTaxable);
            item.setCgstAmt(lineCgst);
            item.setSgstAmt(lineSgst);
            item.setIgstAmt(lineIgst);
            item.setTotalAmount(lineTotal);
            note.addItem(item);

            taxableTotal = taxableTotal.add(lineTaxable);
            cgstTotal = cgstTotal.add(lineCgst);
            sgstTotal = sgstTotal.add(lineSgst);
            igstTotal = igstTotal.add(lineIgst);
        }

        note.setTaxableAmount(taxableTotal);
        note.setCgstAmount(cgstTotal);
        note.setSgstAmount(sgstTotal);
        note.setIgstAmount(igstTotal);
        note.setTotalAmount(taxableTotal.add(cgstTotal).add(sgstTotal).add(igstTotal));

        return creditRepo.save(note);
    }

    private static BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String lineName(SaleItem si) {
        if (si.getItemVariant() != null && si.getItemVariant().getItem() != null
                && si.getItemVariant().getItem().getName() != null) {
            return si.getItemVariant().getItem().getName();
        }
        return si.getCustomItemName() != null ? si.getCustomItemName() : "Item";
    }

    private static String lineHsnSac(SaleItem si) {
        if (si.getItemVariant() != null && si.getItemVariant().getHsn() != null) return si.getItemVariant().getHsn();
        return si.getCustomHsnSac();
    }

    private static String lineUnit(SaleItem si) {
        if (si.getItemVariant() != null && si.getItemVariant().getUnit() != null) return si.getItemVariant().getUnit();
        return si.getCustomUnit();
    }

    private CreditNoteDto toDto(CreditNote entity) {
        CreditNoteDto dto = new CreditNoteDto();
        dto.setId(entity.getId());
        dto.setCreditNoteNo(entity.getCreditNoteNo());
        dto.setSaleId(entity.getSale() != null ? entity.getSale().getId() : null);
        dto.setInvoiceNo(entity.getSale() != null ? entity.getSale().getInvoiceNo() : null);

        if (entity.getCustomer() != null) {
            CustomerDto cust = new CustomerDto();
            cust.setId(entity.getCustomer().getId());
            cust.setName(entity.getCustomer().getName());
            cust.setPhone(entity.getCustomer().getPhone());
            dto.setCustomer(cust);
        }

        dto.setCreditNoteDate(entity.getCreditNoteDate());
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
