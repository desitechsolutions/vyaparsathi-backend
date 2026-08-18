package com.desitech.vyaparsathi.customer.controller;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.customer.dto.*;
import com.desitech.vyaparsathi.customer.entity.CustomerAudit;
import com.desitech.vyaparsathi.customer.service.*;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import com.desitech.vyaparsathi.quotation.entity.Quotation;
import com.desitech.vyaparsathi.quotation.repository.QuotationRepository;
import com.desitech.vyaparsathi.salesorder.entity.SalesOrder;
import com.desitech.vyaparsathi.salesorder.repository.SalesOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
// Class-level gate replaced by per-method @RequirePermission below.
public class CustomerController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerService customerService;
    private final CustomerTransactionService transactionService;
    private final CustomerStatementPdfService statementPdfService;
    private final CustomerEmailService emailService;
    private final CustomerAuditService auditService;
    private final CreditNoteRepository creditNoteRepo;
    private final PaymentRepository paymentRepo;
    private final QuotationRepository quotationRepo;
    private final SalesOrderRepository salesOrderRepo;

    public CustomerController(CustomerService customerService,
                              CustomerTransactionService transactionService,
                              CustomerStatementPdfService statementPdfService,
                              CustomerEmailService emailService,
                              CustomerAuditService auditService,
                              CreditNoteRepository creditNoteRepo,
                              PaymentRepository paymentRepo,
                              QuotationRepository quotationRepo,
                              SalesOrderRepository salesOrderRepo) {
        this.customerService = customerService;
        this.transactionService = transactionService;
        this.statementPdfService = statementPdfService;
        this.emailService = emailService;
        this.auditService = auditService;
        this.creditNoteRepo = creditNoteRepo;
        this.paymentRepo = paymentRepo;
        this.quotationRepo = quotationRepo;
        this.salesOrderRepo = salesOrderRepo;
    }

    @PostMapping
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("CUSTOMER_CREATE")
    public ResponseEntity<CustomerDto> addCustomer(@Valid @RequestBody CustomerDto dto) {
        try {
            CustomerDto result = customerService.addCustomer(dto);
            logger.info("Added customer with name={}", dto.getName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error adding customer with name={}: {}", dto.getName(), e.getMessage(), e);
            throw new ApplicationException("Failed to add customer: " + e.getMessage(), e);
        }
    }

    @PutMapping("/{id}")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("CUSTOMER_EDIT")
    public ResponseEntity<CustomerDto> updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerDto dto) {
        try {
            CustomerDto result = customerService.updateCustomer(id, dto);
            logger.info("Updated customer id={}", id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error updating customer id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to update customer: " + e.getMessage(), e);
        }
    }

    @GetMapping
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("CUSTOMER_VIEW")
    public ResponseEntity<List<CustomerDto>> listCustomers(@RequestParam(required = false) String name) {
        try {
            List<CustomerDto> result;
            if (name != null && !name.isEmpty()) {
                result = customerService.searchCustomers(name);
                logger.info("Searched customers by name={}", name);
            } else {
                result = customerService.listCustomers();
                logger.info("Listed all customers");
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error listing/searching customers: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to list/search customers", e);
        }
    }

    /**
     * V105 Enterprise: Server-side paginated and filtered customer listing.
     */
    @GetMapping("/paged")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("CUSTOMER_VIEW")
    public ResponseEntity<Page<CustomerDto>> listCustomersPaged(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String tags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        try {
            CustomerFilterDto filter = new CustomerFilterDto();
            filter.setSearch(search);
            filter.setActive(active);
            filter.setCustomerType(customerType);
            filter.setSource(source);
            filter.setCity(city);
            filter.setTags(tags);
            filter.setPage(page);
            filter.setSize(size);
            filter.setSortBy(sortBy);
            filter.setSortDir(sortDir);

            Page<CustomerDto> result = customerService.listCustomersPaged(filter);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching paged customers: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to fetch paged customers", e);
        }
    }

    /**
     * V105 Enterprise: Dashboard Header KPI metrics for Customer module.
     */
    @GetMapping("/kpis")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("CUSTOMER_VIEW")
    public ResponseEntity<CustomerKpiDto> getKpis() {
        try {
            return ResponseEntity.ok(customerService.getKpis());
        } catch (Exception e) {
            logger.error("Error fetching customer KPIs: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to fetch customer KPIs", e);
        }
    }

    @GetMapping("/{id}")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("CUSTOMER_VIEW")
    public ResponseEntity<CustomerDto> getCustomer(@PathVariable Long id) {
        try {
            CustomerDto customer = customerService.getCustomer(id);
            logger.info("Fetched customer id={}", id);
            return ResponseEntity.ok(customer);
        } catch (EntityNotFoundException e) {
            logger.warn("Customer not found id={}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error fetching customer id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch customer", e);
        }
    }

    @DeleteMapping("/{id}")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("CUSTOMER_DELETE")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        try {
            customerService.deleteCustomer(id);
            logger.info("Deleted customer id={}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting customer id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException(e.getMessage(), e);
        }
    }

    @PostMapping("/{id}/archive")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("CUSTOMER_DELETE")
    public ResponseEntity<CustomerDto> archiveCustomer(@PathVariable Long id) {
        try {
            CustomerDto result = customerService.archiveCustomer(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error archiving customer id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to archive customer", e);
        }
    }

    @GetMapping("/{id}/stats")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("CUSTOMER_VIEW")
    public ResponseEntity<CustomerStatsDto> getCustomerStats(@PathVariable Long id) {
        try {
            CustomerStatsDto stats = customerService.getStats(id);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error fetching stats for customerId={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch customer stats", e);
        }
    }

    @PostMapping("/{id}/toggle-active")
    @com.desitech.vyaparsathi.rbac.annotation.RequirePermission("CUSTOMER_EDIT")
    public ResponseEntity<CustomerDto> toggleActive(@PathVariable Long id) {
        try {
            CustomerDto result = customerService.toggleActive(id);
            return ResponseEntity.ok(result);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error toggling active for customerId={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to toggle customer active status", e);
        }
    }

    // ─── Bulk Operations ───────────────────────────────────────────────

    @PostMapping("/bulk-toggle-active")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkToggleActive(@RequestBody Map<String, Object> body) {
        try {
            List<?> rawIds = (List<?>) body.get("ids");
            List<Long> ids = rawIds != null ? rawIds.stream().map(o -> Long.valueOf(o.toString())).toList() : List.of();
            boolean active = Boolean.TRUE.equals(body.get("active"));
            int count = customerService.bulkToggleActive(ids, active);
            return ResponseEntity.ok(Map.of("updatedCount", count, "active", active));
        } catch (Exception e) {
            logger.error("Error in bulk toggle active: {}", e.getMessage(), e);
            throw new ApplicationException("Bulk active toggle failed", e);
        }
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkDelete(@RequestBody Map<String, Object> body) {
        try {
            List<?> rawIds = (List<?>) body.get("ids");
            List<Long> ids = rawIds != null ? rawIds.stream().map(o -> Long.valueOf(o.toString())).toList() : List.of();
            Map<String, Object> result = customerService.bulkDelete(ids);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error in bulk delete: {}", e.getMessage(), e);
            throw new ApplicationException("Bulk delete failed", e);
        }
    }

    // ─── CSV Export / Import ────────────────────────────────────────────

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv() {
        try {
            byte[] csvData = customerService.exportCsv();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"customers_export.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csvData);
        } catch (Exception e) {
            logger.error("Failed to export customer CSV: {}", e.getMessage(), e);
            throw new ApplicationException("CSV export failed", e);
        }
    }

    @PostMapping("/import.csv")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = customerService.importCsv(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to import customer CSV: {}", e.getMessage(), e);
            throw new ApplicationException("CSV import failed: " + e.getMessage(), e);
        }
    }

    // ─── Statement PDF & Email ──────────────────────────────────────────

    @GetMapping("/{id}/statement/pdf")
    public ResponseEntity<byte[]> getStatementPdf(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDateTime startDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime endDt = to != null ? to.atTime(23, 59, 59) : null;
        byte[] pdf = statementPdfService.generateStatementPdf(id, startDt, endDt);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"customer_statement_" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/{id}/statement/email")
    public ResponseEntity<Map<String, String>> sendStatementEmail(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        try {
            String fromStr = body != null ? body.get("from") : null;
            String toStr = body != null ? body.get("to") : null;
            String message = body != null ? body.get("message") : null;

            LocalDateTime startDt = fromStr != null && !fromStr.isBlank() ? LocalDate.parse(fromStr).atStartOfDay() : null;
            LocalDateTime endDt = toStr != null && !toStr.isBlank() ? LocalDate.parse(toStr).atTime(23, 59, 59) : null;

            emailService.sendStatementEmail(id, startDt, endDt, message);
            return ResponseEntity.ok(Map.of("message", "Statement emailed successfully"));
        } catch (Exception e) {
            logger.error("Error emailing customer statement id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to email statement: " + e.getMessage(), e);
        }
    }

    // ─── Transactions & Sub-resources ───────────────────────────────────

    @GetMapping("/{id}/transactions")
    public ResponseEntity<Page<CustomerTransactionDto>> getTransactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(transactionService.getTransactions(id, page, size));
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<CustomerAudit>> getAuditTrail(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.getAuditTrail(id));
    }

    @GetMapping("/{id}/credit-notes")
    public ResponseEntity<Page<CreditNote>> getCreditNotes(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long shopId = TenantContext.getCurrentShopId();
        return ResponseEntity.ok(creditNoteRepo.findByShopIdAndCustomerId(shopId, id, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<Page<Payment>> getPayments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(paymentRepo.findByCustomerId(id, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}/quotations")
    public ResponseEntity<Page<Quotation>> getQuotations(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long shopId = TenantContext.getCurrentShopId();
        return ResponseEntity.ok(quotationRepo.findByShopIdAndCustomer_Id(shopId, id, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}/sales-orders")
    public ResponseEntity<Page<SalesOrder>> getSalesOrders(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long shopId = TenantContext.getCurrentShopId();
        return ResponseEntity.ok(salesOrderRepo.findByShopIdAndCustomer_Id(shopId, id, PageRequest.of(page, size)));
    }
}