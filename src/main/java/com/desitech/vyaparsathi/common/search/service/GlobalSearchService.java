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
        String searchPattern = "%" + query + "%";

        results.addAll(customerRepo.searchByCustomerPartial(query).stream()
                .limit(5)
                .map(c -> GlobalSearchResponse.builder()
                        .id(c.getId().toString())
                        .title(c.getName())
                        .subtitle("📞 " + c.getPhone() + " | Balance: ₹" + c.getCreditBalance())
                        .type("CUSTOMER")
                        .route("/customer-details/" + c.getId() + "/dues")
                        .extraInfo(Map.of(
                                "action", "PAYMENT",
                                "balance", c.getCreditBalance(),
                                "phone", c.getPhone()
                        ))
                        .build())
                .toList());

        results.addAll(saleRepo.searchByInvoicePartial(query).stream()
                .limit(5)
                .map(s -> GlobalSearchResponse.builder()
                        .id(s.getId().toString())
                        .title("Bill: " + s.getInvoiceNo())
                        .subtitle("Date: " + s.getDate().toLocalDate() + " | Status: " + s.getPaymentStatus())
                        .type("SALE")
                        .route("/sales?tab=history&search=" + s.getInvoiceNo())
                        .extraInfo(Map.of("total", s.getTotalAmount()))
                        .build())
                .toList());

        results.addAll(itemRepo.findByNameContainingIgnoreCase(query).stream()
                .limit(5)
                .map(i -> GlobalSearchResponse.builder()
                        .id(i.getId().toString())
                        .title(i.getName())
                        .subtitle("Category: " + (i.getCategory() != null ? i.getCategory().getName() : "General"))
                        .type("PRODUCT")
                        .route("/products?search=" + i.getName())
                        .build())
                .toList());

        return results;
    }
}