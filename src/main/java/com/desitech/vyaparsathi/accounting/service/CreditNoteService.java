package com.desitech.vyaparsathi.accounting.service;

import com.desitech.vyaparsathi.accounting.dto.CreditNoteCreateDto;
import com.desitech.vyaparsathi.accounting.dto.CreditNoteDto;
import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.common.annotations.LogAudit;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.customer.dto.CustomerDto;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class CreditNoteService {

    private final CreditNoteRepository creditRepo;
    private final SaleRepository saleRepo;
    private final CustomerRepository customerRepo;

    public CreditNoteService(CreditNoteRepository creditRepo, SaleRepository saleRepo, CustomerRepository customerRepo) {
        this.creditRepo = creditRepo;
        this.saleRepo = saleRepo;
        this.customerRepo = customerRepo;
    }

    @Transactional
    @LogAudit(action = "CREATE_CREDIT_NOTE", entity = "CREDIT_NOTE")
    public CreditNoteDto createCreditNote(CreditNoteCreateDto createDto) {
        Long shopId = TenantUtils.getCurrentShopId();

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
        note.setCreditNoteNo("CN/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM")) + "/" + (System.currentTimeMillis() % 100000));
        note.setSale(sale);
        note.setCustomer(customer);
        note.setCreditNoteDate(createDto.getCreditNoteDate() != null ? createDto.getCreditNoteDate() : LocalDate.now());
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

        CreditNote saved = creditRepo.save(note);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<CreditNoteDto> getAllCreditNotes(Pageable pageable) {
        Long shopId = TenantUtils.getCurrentShopId();
        return creditRepo.findAllByShopId(shopId, pageable).map(this::toDto);
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
        dto.setStatus(entity.getStatus());
        dto.setNotes(entity.getNotes());

        return dto;
    }
}
