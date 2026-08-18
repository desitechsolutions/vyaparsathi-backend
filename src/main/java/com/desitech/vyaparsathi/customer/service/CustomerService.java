package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.customer.dto.CustomerDto;
import com.desitech.vyaparsathi.customer.dto.CustomerFilterDto;
import com.desitech.vyaparsathi.customer.dto.CustomerKpiDto;
import com.desitech.vyaparsathi.customer.dto.CustomerStatsDto;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.enums.CustomerType;
import com.desitech.vyaparsathi.customer.mapper.CustomerMapper;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.customer.repository.CustomerSpecification;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.persistence.EntityNotFoundException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CustomerService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final CustomerMapper mapper;
    private final ShopRepository shopRepository;
    private final CustomerStatsService customerStatsService;
    private final CustomerAuditService auditService;

    public CustomerService(CustomerRepository customerRepository,
                           CustomerMapper mapper,
                           ShopRepository shopRepository,
                           CustomerStatsService customerStatsService,
                           CustomerAuditService auditService) {
        this.customerRepository = customerRepository;
        this.mapper = mapper;
        this.shopRepository = shopRepository;
        this.customerStatsService = customerStatsService;
        this.auditService = auditService;
    }

    @Transactional
    public CustomerDto addCustomer(CustomerDto dto) {
        Customer customer = mapper.toEntity(dto);
        if (customer.getCreditBalance() == null) {
            customer.setCreditBalance(BigDecimal.ZERO);
        }
        if (customer.getActive() == null) {
            customer.setActive(true);
        }
        customer.setShop(getCurrentShop());

        customer = customerRepository.save(customer);
        auditService.recordAction(customer.getId(), "CREATED", "Customer profile created: " + customer.getName(), null);
        return mapper.toDto(customer);
    }

    @Transactional
    public CustomerDto updateCustomer(Long id, CustomerDto dto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with id=" + id));
        mapper.updateEntityFromDto(dto, customer);
        customer = customerRepository.save(customer);
        auditService.recordAction(customer.getId(), "UPDATED", "Customer profile updated: " + customer.getName(), null);
        return mapper.toDto(customer);
    }

    @Transactional(readOnly = true)
    public List<CustomerDto> listCustomers() {
        return customerRepository.findAll().stream()
                .map(mapper::toDto)
                .toList();
    }

    /**
     * Server-side paginated and dynamically filtered customer list.
     */
    @Transactional(readOnly = true)
    public Page<CustomerDto> listCustomersPaged(CustomerFilterDto filter) {
        Sort.Direction direction = "desc".equalsIgnoreCase(filter.getSortDir()) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortBy = filter.getSortBy() != null && !filter.getSortBy().isBlank() ? filter.getSortBy() : "name";
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by(direction, sortBy));

        Specification<Customer> spec = CustomerSpecification.withFilters(
                filter.getSearch(),
                filter.getActive(),
                filter.getCustomerType(),
                filter.getSource(),
                filter.getCity(),
                filter.getTags()
        );

        return customerRepository.findAll(spec, pageable).map(mapper::toDto);
    }

    /**
     * Header KPI summary for the Customer module.
     */
    @Transactional(readOnly = true)
    public CustomerKpiDto getKpis() {
        Long shopId = TenantContext.getCurrentShopId();
        CustomerKpiDto kpi = new CustomerKpiDto();
        if (shopId == null) return kpi;

        long activeCount = customerRepository.countByActiveAndShop_Id(true, shopId);
        long inactiveCount = customerRepository.countByActiveAndShop_Id(false, shopId);
        long total = activeCount + inactiveCount;
        long businessCount = customerRepository.countByCustomerTypeAndShop_Id(CustomerType.BUSINESS, shopId);
        long individualCount = customerRepository.countByCustomerTypeAndShop_Id(CustomerType.INDIVIDUAL, shopId);

        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        long newThisMonth = customerRepository.countNewSince(shopId, startOfMonth);

        // Sum negative credit balance (where customer owes the shop)
        List<Customer> allCustomers = customerRepository.findAllByShopId(shopId);
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        for (Customer c : allCustomers) {
            if (c.getCreditBalance() != null && c.getCreditBalance().compareTo(BigDecimal.ZERO) < 0) {
                totalOutstanding = totalOutstanding.add(c.getCreditBalance().abs());
            }
        }

        kpi.setTotalCustomers(total);
        kpi.setActiveCustomers(activeCount);
        kpi.setInactiveCustomers(inactiveCount);
        kpi.setBusinessCustomers(businessCount);
        kpi.setIndividualCustomers(individualCount);
        kpi.setNewThisMonth(newThisMonth);
        kpi.setTotalOutstanding(totalOutstanding);

        return kpi;
    }

    @Transactional(readOnly = true)
    public CustomerDto getCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with id=" + id));
        return mapper.toDto(customer);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with id=" + id));

        // Foreign key guard
        if (customerRepository.hasSales(id)) {
            throw new ApplicationException("Cannot hard-delete customer with linked sales/invoices. Use archive instead.");
        }
        if (customerRepository.hasLedgerEntries(id)) {
            throw new ApplicationException("Cannot hard-delete customer with existing ledger transactions. Use archive instead.");
        }

        auditService.recordAction(id, "DELETED", "Customer hard-deleted: " + customer.getName(), null);
        customerRepository.delete(customer);
    }

    @Transactional
    public CustomerDto archiveCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with id=" + id));
        customer.setActive(false);
        customer = customerRepository.save(customer);
        auditService.recordAction(id, "ARCHIVED", "Customer archived / deactivated: " + customer.getName(), null);
        return mapper.toDto(customer);
    }

    @Transactional
    public CustomerDto toggleActive(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with id=" + id));
        boolean newActive = !Boolean.TRUE.equals(customer.getActive());
        customer.setActive(newActive);
        customer = customerRepository.save(customer);
        auditService.recordAction(id, newActive ? "ACTIVATED" : "DEACTIVATED",
                "Customer " + (newActive ? "activated" : "deactivated"), null);
        return mapper.toDto(customer);
    }

    @Transactional
    public int bulkToggleActive(List<Long> ids, boolean active) {
        if (ids == null || ids.isEmpty()) return 0;
        int count = 0;
        for (Long id : ids) {
            Optional<Customer> opt = customerRepository.findById(id);
            if (opt.isPresent()) {
                Customer c = opt.get();
                c.setActive(active);
                customerRepository.save(c);
                auditService.recordAction(c.getId(), active ? "ACTIVATED" : "DEACTIVATED", "Bulk " + (active ? "activated" : "deactivated"), null);
                count++;
            }
        }
        return count;
    }

    @Transactional
    public Map<String, Object> bulkDelete(List<Long> ids) {
        Map<String, Object> result = new LinkedHashMap<>();
        int deleted = 0;
        List<String> failedReasons = new ArrayList<>();

        if (ids != null) {
            for (Long id : ids) {
                Optional<Customer> opt = customerRepository.findById(id);
                if (opt.isPresent()) {
                    Customer c = opt.get();
                    if (customerRepository.hasSales(id) || customerRepository.hasLedgerEntries(id)) {
                        failedReasons.add("Customer '" + c.getName() + "' (ID " + id + ") has linked transactions — archived instead of deleted.");
                        c.setActive(false);
                        customerRepository.save(c);
                    } else {
                        customerRepository.delete(c);
                        deleted++;
                    }
                }
            }
        }
        result.put("deletedCount", deleted);
        result.put("notes", failedReasons);
        return result;
    }

    @Transactional(readOnly = true)
    public List<CustomerDto> searchCustomers(String name) {
        return customerRepository.findByNameContainingIgnoreCase(name).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with id=" + id));
    }

    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    @Transactional
    public Customer createCustomer(Customer customer) {
        customer.setShop(getCurrentShop());
        if (customer.getCreditBalance() == null) {
            customer.setCreditBalance(BigDecimal.ZERO);
        }
        if (customer.getActive() == null) {
            customer.setActive(true);
        }
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public CustomerStatsDto getStats(Long id) {
        customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with id=" + id));
        return customerStatsService.compute(id);
    }

    /**
     * Generates CSV export for all customers of the current tenant.
     */
    @Transactional(readOnly = true)
    public byte[] exportCsv() {
        List<Customer> customers = customerRepository.findAll();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("ID", "Name", "Trade Name", "Phone", "Email", "Customer Type", "GSTIN", "PAN",
                             "Address", "City", "State", "Pincode", "Credit Limit", "Credit Balance", "Active", "Source", "Tags")
                     .build())) {

            for (Customer c : customers) {
                printer.printRecord(
                        c.getId(),
                        c.getName(),
                        c.getTradeName(),
                        c.getPhone(),
                        c.getEmail(),
                        c.getCustomerType() != null ? c.getCustomerType().name() : "",
                        c.getGstNumber(),
                        c.getPanNumber(),
                        c.getAddressLine1(),
                        c.getCity(),
                        c.getState(),
                        c.getPostalCode(),
                        c.getCreditLimit(),
                        c.getCreditBalance(),
                        Boolean.TRUE.equals(c.getActive()) ? "ACTIVE" : "INACTIVE",
                        c.getSource(),
                        c.getTags()
                );
            }
            printer.flush();
            return out.toByteArray();
        } catch (IOException e) {
            logger.error("Failed to export customer CSV: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to export customer CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Imports customers from uploaded CSV file.
     */
    @Transactional
    public Map<String, Object> importCsv(MultipartFile file) {
        Shop shop = getCurrentShop();
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build())) {

            for (CSVRecord record : csvParser) {
                try {
                    String name = record.isMapped("Name") ? record.get("Name") : null;
                    if (name == null || name.isBlank()) {
                        skipped++;
                        errors.add("Row " + record.getRecordNumber() + ": Missing required customer Name.");
                        continue;
                    }

                    Customer customer = new Customer();
                    customer.setShop(shop);
                    customer.setName(name.trim());
                    if (record.isMapped("Trade Name")) customer.setTradeName(record.get("Trade Name"));
                    if (record.isMapped("Phone")) customer.setPhone(record.get("Phone"));
                    if (record.isMapped("Email")) customer.setEmail(record.get("Email"));
                    if (record.isMapped("Customer Type")) {
                        customer.setCustomerType(CustomerType.fromString(record.get("Customer Type")));
                    }
                    if (record.isMapped("GSTIN")) customer.setGstNumber(record.get("GSTIN"));
                    if (record.isMapped("PAN")) customer.setPanNumber(record.get("PAN"));
                    if (record.isMapped("Address")) customer.setAddressLine1(record.get("Address"));
                    if (record.isMapped("City")) customer.setCity(record.get("City"));
                    if (record.isMapped("State")) customer.setState(record.get("State"));
                    if (record.isMapped("Pincode")) customer.setPostalCode(record.get("Pincode"));
                    if (record.isMapped("Credit Limit")) {
                        try {
                            customer.setCreditLimit(new BigDecimal(record.get("Credit Limit")));
                        } catch (Exception ignored) {}
                    }
                    customer.setCreditBalance(BigDecimal.ZERO);
                    customer.setActive(true);
                    if (record.isMapped("Source")) customer.setSource(record.get("Source"));
                    if (record.isMapped("Tags")) customer.setTags(record.get("Tags"));

                    customerRepository.save(customer);
                    imported++;
                } catch (Exception ex) {
                    skipped++;
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse customer CSV: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to parse CSV file: " + e.getMessage(), e);
        }


        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("importedCount", imported);
        resp.put("skippedCount", skipped);
        resp.put("errors", errors);
        return resp;
    }

    private Shop getCurrentShop() {
        Long shopId = TenantContext.getCurrentShopId();
        if (shopId == null) {
            throw new ApplicationException("No shop in TenantContext");
        }
        return shopRepository.findById(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Shop not found with id=" + shopId));
    }
}
