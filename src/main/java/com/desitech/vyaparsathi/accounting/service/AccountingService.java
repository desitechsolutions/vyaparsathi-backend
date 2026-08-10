package com.desitech.vyaparsathi.accounting.service;

import com.desitech.vyaparsathi.accounting.dto.*;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.common.util.TenantUtils;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoice;
import com.desitech.vyaparsathi.purchases.repository.PurchaseInvoiceRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import com.desitech.vyaparsathi.expense.entity.Expense;
import com.desitech.vyaparsathi.expense.repository.ExpenseRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AccountingService {

    private final CustomerRepository customerRepo;
    private final SupplierRepository supplierRepo;
    private final SaleRepository saleRepo;
    private final PurchaseInvoiceRepository purchaseRepo;
    private final ExpenseRepository expenseRepo;

    public AccountingService(CustomerRepository customerRepo,
                             SupplierRepository supplierRepo,
                             SaleRepository saleRepo,
                             PurchaseInvoiceRepository purchaseRepo,
                             ExpenseRepository expenseRepo) {
        this.customerRepo = customerRepo;
        this.supplierRepo = supplierRepo;
        this.saleRepo = saleRepo;
        this.purchaseRepo = purchaseRepo;
        this.expenseRepo = expenseRepo;
    }

    @Transactional(readOnly = true)
    public List<ReceivablesAgingDto> getReceivablesAging() {
        Long shopId = TenantUtils.getCurrentShopId();
        List<Customer> customers = customerRepo.findAllByShopId(shopId);
        LocalDate today = LocalDate.now();

        List<ReceivablesAgingDto> agingList = new ArrayList<>();

        for (Customer customer : customers) {
            List<Sale> sales = saleRepo.findAllByCustomerId(customer.getId());
            ReceivablesAgingDto dto = new ReceivablesAgingDto();
            dto.setCustomerId(customer.getId());
            dto.setCustomerName(customer.getName());
            dto.setPhone(customer.getPhone());

            BigDecimal total = BigDecimal.ZERO;

            for (Sale sale : sales) {
                if ("PAID".equalsIgnoreCase(sale.getPaymentStatus() != null ? sale.getPaymentStatus().name() : "")) {
                    continue;
                }

                long days = ChronoUnit.DAYS.between(sale.getDate().toLocalDate(), today);
                BigDecimal amount = sale.getGrandTotal();

                if (days <= 30) {
                    dto.setCurrent0To30Days(dto.getCurrent0To30Days().add(amount));
                } else if (days <= 60) {
                    dto.setDays31To60(dto.getDays31To60().add(amount));
                } else if (days <= 90) {
                    dto.setDays61To90(dto.getDays61To90().add(amount));
                } else {
                    dto.setOver90Days(dto.getOver90Days().add(amount));
                }
                total = total.add(amount);
            }

            dto.setTotalOutstanding(total);
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                agingList.add(dto);
            }
        }

        return agingList;
    }

    @Transactional(readOnly = true)
    public List<PayablesAgingDto> getPayablesAging() {
        Long shopId = TenantUtils.getCurrentShopId();
        List<Supplier> suppliers = supplierRepo.findAllByShopId(shopId);
        LocalDate today = LocalDate.now();

        List<PayablesAgingDto> agingList = new ArrayList<>();

        for (Supplier supplier : suppliers) {
            List<PurchaseInvoice> purchases = purchaseRepo.findAllByShopId(shopId, Pageable.unpaged())
                    .getContent()
                    .stream()
                    .filter(p -> p.getSupplier() != null && p.getSupplier().getId().equals(supplier.getId()))
                    .toList();

            PayablesAgingDto dto = new PayablesAgingDto();
            dto.setSupplierId(supplier.getId());
            dto.setSupplierName(supplier.getSupplierName());
            dto.setPhone(supplier.getPhone());

            BigDecimal total = BigDecimal.ZERO;

            for (PurchaseInvoice purchase : purchases) {
                if ("PAID".equalsIgnoreCase(purchase.getPaymentStatus())) {
                    continue;
                }

                BigDecimal unpaid = purchase.getTotalAmount().subtract(purchase.getPaidAmount());
                if (unpaid.compareTo(BigDecimal.ZERO) <= 0) continue;

                long days = ChronoUnit.DAYS.between(purchase.getPurchaseDate(), today);

                if (days <= 30) {
                    dto.setCurrent0To30Days(dto.getCurrent0To30Days().add(unpaid));
                } else if (days <= 60) {
                    dto.setDays31To60(dto.getDays31To60().add(unpaid));
                } else if (days <= 90) {
                    dto.setDays61To90(dto.getDays61To90().add(unpaid));
                } else {
                    dto.setOver90Days(dto.getOver90Days().add(unpaid));
                }
                total = total.add(unpaid);
            }

            dto.setTotalPayable(total);
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                agingList.add(dto);
            }
        }

        return agingList;
    }

    @Transactional(readOnly = true)
    public ProfitAndLossDto getProfitAndLoss(LocalDate startDate, LocalDate endDate) {
        Long shopId = TenantUtils.getCurrentShopId();

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        List<Sale> sales = saleRepo.findAllByShopIdAndDateBetween(shopId, start, end);

        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;

        for (Sale sale : sales) {
            grossSales = grossSales.add(sale.getGrandTotal());
            if (sale.getSaleItems() != null) {
                for (var item : sale.getSaleItems()) {
                    BigDecimal qty = item.getQty() != null ? item.getQty() : BigDecimal.ZERO;
                    BigDecimal cost = item.getItemVariant() != null && item.getItemVariant().getPricePerUnit() != null ?
                            item.getItemVariant().getPricePerUnit().multiply(new BigDecimal("0.7")) : BigDecimal.ZERO;
                    cogs = cogs.add(qty.multiply(cost));
                }
            }
        }

        ProfitAndLossDto pnl = new ProfitAndLossDto();
        pnl.setStartDate(startDate);
        pnl.setEndDate(endDate);
        pnl.setGrossSales(grossSales);
        pnl.setSalesReturns(BigDecimal.ZERO);
        pnl.setNetSales(grossSales);
        pnl.setCostOfGoodsSold(cogs);

        BigDecimal grossProfit = grossSales.subtract(cogs);
        pnl.setGrossProfit(grossProfit);

        // Sum actual recorded operating expenses for this shop and date range
        List<Expense> expenses = expenseRepo.findByDateBetweenAndDeletedFalse(start, end);
        BigDecimal operatingExpenses = expenses.stream()
                .filter(e -> e.getShop() != null && e.getShop().getId().equals(shopId))
                .map(Expense::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        pnl.setOperatingExpenses(operatingExpenses);

        BigDecimal netProfit = grossProfit.subtract(operatingExpenses);
        pnl.setNetProfit(netProfit);

        if (grossSales.compareTo(BigDecimal.ZERO) > 0) {
            double margin = netProfit.divide(grossSales, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
            pnl.setNetProfitMarginPercent(margin);
        }

        return pnl;
    }
}
