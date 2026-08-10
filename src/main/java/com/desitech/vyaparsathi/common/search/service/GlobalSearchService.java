package com.desitech.vyaparsathi.common.search.service;

import com.desitech.vyaparsathi.common.search.dto.GlobalSearchResponse;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.inventory.repository.ItemRepository;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GlobalSearchService {

    private final CustomerRepository customerRepo;
    private final SaleRepository saleRepo;
    private final ItemRepository itemRepo;

    public GlobalSearchService(CustomerRepository customerRepo, SaleRepository saleRepo, ItemRepository itemRepo) {
        this.customerRepo = customerRepo;
        this.saleRepo = saleRepo;
        this.itemRepo = itemRepo;
    }

    public List<GlobalSearchResponse> performSearch(String query) {
        List<GlobalSearchResponse> results = new ArrayList<>();

        for (var c : customerRepo.searchByCustomerPartial(query).stream().limit(5).toList()) {
            GlobalSearchResponse dto = new GlobalSearchResponse();
            dto.setId(c.getId() != null ? c.getId().toString() : "");
            dto.setTitle(c.getName());
            dto.setSubtitle("📞 " + (c.getPhone() != null ? c.getPhone() : "") + " | Balance: ₹" + c.getCreditBalance());
            dto.setType("CUSTOMER");
            dto.setRoute("/customer-details/" + c.getId() + "/dues");
            dto.setExtraInfo(Map.of(
                    "action", "PAYMENT",
                    "balance", c.getCreditBalance() != null ? c.getCreditBalance() : 0,
                    "phone", c.getPhone() != null ? c.getPhone() : ""
            ));
            results.add(dto);
        }

        for (var s : saleRepo.searchByInvoicePartial(query).stream().limit(5).toList()) {
            GlobalSearchResponse dto = new GlobalSearchResponse();
            dto.setId(s.getId() != null ? s.getId().toString() : "");
            dto.setTitle("Bill: " + s.getInvoiceNo());
            dto.setSubtitle("Date: " + (s.getDate() != null ? s.getDate().toLocalDate() : "") + " | Status: " + s.getPaymentStatus());
            dto.setType("SALE");
            dto.setRoute("/sales?tab=history&search=" + s.getInvoiceNo());
            dto.setExtraInfo(Map.of("total", s.getTotalAmount() != null ? s.getTotalAmount() : 0));
            results.add(dto);
        }

        for (var i : itemRepo.findByNameContainingIgnoreCase(query).stream().limit(5).toList()) {
            GlobalSearchResponse dto = new GlobalSearchResponse();
            dto.setId(i.getId() != null ? i.getId().toString() : "");
            dto.setTitle(i.getName());
            dto.setSubtitle("Category: " + (i.getCategory() != null ? i.getCategory().getName() : "General"));
            dto.setType("PRODUCT");
            dto.setRoute("/products?search=" + i.getName());
            results.add(dto);
        }

        return results;
    }
}