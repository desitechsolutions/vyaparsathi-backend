package com.desitech.vyaparsathi.reports.service;

import com.desitech.vyaparsathi.inventory.entity.Category;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import com.desitech.vyaparsathi.reports.dto.*;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.expense.entity.Expense;
import com.desitech.vyaparsathi.expense.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private COGSCalculationService cogsCalculationService;

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("From date cannot be after to date");
        }
    }

    private List<Sale> getSalesByDateRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return saleRepository.findAll();
        }
        validateDateRange(from, to);
        assert from != null;
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(23, 59, 59);
        return saleRepository.findByDateBetween(start, end);
    }

    private List<Expense> getExpensesByDateRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return expenseRepository.findAll();
        }
        validateDateRange(from, to);
        assert from != null;
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(23, 59, 59);
        return expenseRepository.findByDateBetweenAndDeletedFalse(start, end);
    }

    private BigDecimal calculateOperationalExpenses(List<Expense> expenses) {
        return expenses.stream()
                .filter(e -> {
                    String type = (e.getType() != null) ? e.getType().toLowerCase() : "";
                    return !type.contains("inventory") && !type.contains("purchase");
                })
                .map(Expense::getAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal calculateGrowthPercent(BigDecimal previous, BigDecimal current) {
        if (previous.compareTo(ZERO) > 0) {
            return current.subtract(previous)
                    .multiply(HUNDRED)
                    .divide(previous, 2, RoundingMode.HALF_UP);
        } else if (current.compareTo(ZERO) > 0) {
            return HUNDRED;
        }
        return ZERO;
    }

    public DailyReportDto getDailyReport(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        List<Sale> todaySales = getSalesByDateRange(date, date);
        List<Expense> todayExpenses = getExpensesByDateRange(date, date);

        BigDecimal totalSalesToday = todaySales.stream()
                .map(Sale::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalOperationalExpenses = calculateOperationalExpenses(todayExpenses);

        Set<Long> saleIdsToday = todaySales.stream()
                .map(Sale::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> paidBySaleToday = paymentService.getTotalPaidBySaleIds(saleIdsToday);
        BigDecimal totalPaidToday = paidBySaleToday.values().stream().reduce(ZERO, BigDecimal::add);

        BigDecimal totalCOGS = cogsCalculationService.calculateCOGS(todaySales);

        BigDecimal totalRoundOffToday = todaySales.stream()
                .map(Sale::getRoundOff)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal netRevenue = totalSalesToday.subtract(totalRoundOffToday);
        BigDecimal outstanding = totalSalesToday.subtract(totalPaidToday);
        BigDecimal netProfitToday = totalSalesToday.subtract(totalCOGS).subtract(totalOperationalExpenses);

        LocalDate yesterday = date.minusDays(1);
        List<Sale> yesterdaySalesList = getSalesByDateRange(yesterday, yesterday);
        List<Expense> yesterdayExpenses = getExpensesByDateRange(yesterday, yesterday);

        BigDecimal totalSalesYesterday = yesterdaySalesList.stream()
                .map(Sale::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalCOGSYesterday = cogsCalculationService.calculateCOGS(yesterdaySalesList);
        BigDecimal totalOperationalExpensesYesterday = calculateOperationalExpenses(yesterdayExpenses);

        BigDecimal netProfitYesterday = totalSalesYesterday
                .subtract(totalCOGSYesterday)
                .subtract(totalOperationalExpensesYesterday);

        BigDecimal salesTrend = calculateGrowthPercent(totalSalesYesterday, totalSalesToday);
        BigDecimal profitTrend = calculateGrowthPercent(netProfitYesterday, netProfitToday);

        if (totalSalesYesterday.compareTo(ZERO) == 0 && totalSalesToday.compareTo(ZERO) > 0) {
            logger.info("No sales on {} → growth set to 100% for date {}", yesterday, date);
        }
        if (netProfitYesterday.compareTo(ZERO) == 0 && netProfitToday.compareTo(ZERO) > 0) {
            logger.info("No profit on {} → profit growth set to 100% for date {}", yesterday, date);
        }

        DailyReportDto dto = new DailyReportDto();
        dto.setDate(date);
        dto.setTotalSales(totalSalesToday);
        dto.setNumberOfSales(todaySales.size());
        dto.setNetRevenue(netRevenue);
        dto.setOutstandingReceivable(outstanding);
        dto.setTotalCOGS(totalCOGS);
        dto.setNetProfit(netProfitToday);
        dto.setSalesTrendPercent(salesTrend);
        dto.setProfitTrendPercent(profitTrend);
        dto.setYesterdaySales(totalSalesYesterday);
        dto.setYesterdayNetProfit(netProfitYesterday);

        return dto;
    }

    public SalesSummaryDto getSalesSummary(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Date range cannot be null");
        }
        validateDateRange(from, to);

        List<Sale> currentSales = getSalesByDateRange(from, to);
        List<Expense> currentExpenses = getExpensesByDateRange(from, to);

        BigDecimal totalSales = currentSales.stream()
                .map(Sale::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalTaxableValue = currentSales.stream()
                .flatMap(s -> s.getSaleItems().stream())
                .map(SaleItem::getTaxableValue)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalOperationalExpenses = calculateOperationalExpenses(currentExpenses);

        BigDecimal totalGstAmount = currentSales.stream()
                .flatMap(s -> s.getSaleItems().stream())
                .map(si -> {
                    BigDecimal cgst = si.getCgstAmt() != null ? si.getCgstAmt() : ZERO;
                    BigDecimal sgst = si.getSgstAmt() != null ? si.getSgstAmt() : ZERO;
                    BigDecimal igst = si.getIgstAmt() != null ? si.getIgstAmt() : ZERO;
                    return cgst.add(sgst).add(igst);
                })
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalRoundOff = currentSales.stream()
                .map(Sale::getRoundOff)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        Set<Long> saleIds = currentSales.stream()
                .map(Sale::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> paidBySale = paymentService.getTotalPaidBySaleIds(saleIds);
        BigDecimal totalPaid = paidBySale.values().stream().reduce(ZERO, BigDecimal::add);

        BigDecimal totalCOGS = cogsCalculationService.calculateCOGSForPeriod(currentSales, from, to);

        BigDecimal netRevenue = totalSales.subtract(totalRoundOff);
        BigDecimal outstanding = totalSales.subtract(totalPaid);
        BigDecimal netProfit = totalSales.subtract(totalCOGS).subtract(totalOperationalExpenses);

        long daysBetween = from.until(to).getDays() + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(daysBetween - 1);

        List<Sale> prevSales = getSalesByDateRange(prevFrom, prevTo);
        List<Expense> prevExpenses = getExpensesByDateRange(prevFrom, prevTo);

        BigDecimal prevTotalSales = prevSales.stream()
                .map(Sale::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        BigDecimal prevCOGS = cogsCalculationService.calculateCOGSForPeriod(prevSales, prevFrom, prevTo);
        BigDecimal prevExpensesTotal = calculateOperationalExpenses(prevExpenses);

        BigDecimal prevNetProfit = prevTotalSales.subtract(prevCOGS).subtract(prevExpensesTotal);

        BigDecimal salesGrowth = calculateGrowthPercent(prevTotalSales, totalSales);
        BigDecimal profitGrowth = calculateGrowthPercent(prevNetProfit, netProfit);

        if (prevTotalSales.compareTo(ZERO) == 0 && totalSales.compareTo(ZERO) > 0) {
            logger.info("No sales in previous period {} to {} → sales growth set to 100%", prevFrom, prevTo);
        }
        if (prevNetProfit.compareTo(ZERO) == 0 && netProfit.compareTo(ZERO) > 0) {
            logger.info("No profit in previous period {} to {} → profit growth set to 100%", prevFrom, prevTo);
        }

        SalesSummaryDto dto = new SalesSummaryDto();
        dto.setFromDate(from);
        dto.setToDate(to);
        dto.setTotalSales(totalSales);
        dto.setTotalSalesCount(currentSales.size());
        dto.setTotalTaxableValue(totalTaxableValue);
        dto.setTotalGstAmount(totalGstAmount);
        dto.setTotalRoundOff(totalRoundOff);
        dto.setTotalPaid(totalPaid);
        dto.setTotalCOGS(totalCOGS);
        dto.setNetRevenue(netRevenue);
        dto.setOutstandingReceivable(outstanding);
        dto.setNetProfit(netProfit);

        dto.setPreviousFromDate(prevFrom);
        dto.setPreviousToDate(prevTo);
        dto.setPreviousTotalSales(prevTotalSales);
        dto.setPreviousNetProfit(prevNetProfit);

        dto.setSalesGrowthPercent(salesGrowth);
        dto.setProfitGrowthPercent(profitGrowth);

        return dto;
    }

    public GstSummaryDto getGstSummary(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        List<Sale> sales = getSalesByDateRange(from, to);

        BigDecimal taxable = ZERO;
        BigDecimal cgst = ZERO;
        BigDecimal sgst = ZERO;
        BigDecimal igst = ZERO;

        for (Sale sale : sales) {
            for (SaleItem item : sale.getSaleItems()) {
                taxable = taxable.add(item.getTaxableValue() != null ? item.getTaxableValue() : ZERO);
                cgst = cgst.add(item.getCgstAmt() != null ? item.getCgstAmt() : ZERO);
                sgst = sgst.add(item.getSgstAmt() != null ? item.getSgstAmt() : ZERO);
                igst = igst.add(item.getIgstAmt() != null ? item.getIgstAmt() : ZERO);
            }
        }

        GstSummaryDto dto = new GstSummaryDto();
        dto.setTaxableValue(taxable);
        dto.setCgstTotal(cgst);
        dto.setSgstTotal(sgst);
        dto.setIgstTotal(igst);
        dto.setTotalGst(cgst.add(sgst).add(igst));
        return dto;
    }

    public List<GstBreakdownDto> getGstSummaryByRate(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        List<Sale> sales = getSalesByDateRange(from, to);

        Map<Integer, List<SaleItem>> itemsByGstRate = sales.stream()
                .flatMap(s -> s.getSaleItems().stream())
                .collect(Collectors.groupingBy(si -> si.getGstType() != null ? si.getGstType().getRate() : 0));

        return itemsByGstRate.entrySet().stream()
                .map(entry -> {
                    int gstRate = entry.getKey();
                    List<SaleItem> items = entry.getValue();

                    BigDecimal taxable = items.stream()
                            .map(SaleItem::getTaxableValue)
                            .filter(Objects::nonNull)
                            .reduce(ZERO, BigDecimal::add);

                    BigDecimal cgst = items.stream()
                            .map(SaleItem::getCgstAmt)
                            .filter(Objects::nonNull)
                            .reduce(ZERO, BigDecimal::add);

                    BigDecimal sgst = items.stream()
                            .map(SaleItem::getSgstAmt)
                            .filter(Objects::nonNull)
                            .reduce(ZERO, BigDecimal::add);

                    BigDecimal igst = items.stream()
                            .map(SaleItem::getIgstAmt)
                            .filter(Objects::nonNull)
                            .reduce(ZERO, BigDecimal::add);

                    GstBreakdownDto dto = new GstBreakdownDto();
                    dto.setGstRate(gstRate);
                    dto.setTaxableValue(taxable);
                    dto.setCgst(cgst);
                    dto.setSgst(sgst);
                    dto.setIgst(igst);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<ItemsSoldDto> getAllItemsSold(LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        List<Sale> sales = getSalesByDateRange(fromDate, toDate);
        Map<Long, ItemsSoldDto> itemMap = new HashMap<>();

        for (Sale sale : sales) {
            for (SaleItem item : sale.getSaleItems()) {
                Long itemId = item.getItemVariant().getId();
                ItemsSoldDto dto = itemMap.getOrDefault(itemId, new ItemsSoldDto(
                        itemId,
                        item.getItemVariant().getItem().getName(),
                        item.getItemVariant().getSku(),
                        0,
                        ZERO,
                        null
                ));
                dto.setTotalSold(dto.getTotalSold() + item.getQty().intValue());
                dto.setTotalSales(dto.getTotalSales().add(item.getUnitPrice().multiply(item.getQty())));
                LocalDate saleDate = sale.getDate().toLocalDate();
                if (dto.getLastSoldDate() == null || saleDate.isAfter(dto.getLastSoldDate())) {
                    dto.setLastSoldDate(saleDate);
                }
                itemMap.put(itemId, dto);
            }
        }
        return new ArrayList<>(itemMap.values());
    }

    public List<CategorySalesDto> getCategorySales(LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        List<Sale> sales = getSalesByDateRange(fromDate, toDate);
        Map<String, CategorySalesDto> categoryMap = new HashMap<>();

        for (Sale sale : sales) {
            for (SaleItem item : sale.getSaleItems()) {
                Category category = item.getItemVariant().getItem().getCategory();
                if (category == null) continue;
                CategorySalesDto dto = categoryMap.getOrDefault(category.getName(), new CategorySalesDto(
                        category.getName(),
                        0,
                        ZERO
                ));
                dto.setTotalSold(dto.getTotalSold() + item.getQty().intValue());
                dto.setTotalSales(dto.getTotalSales().add(item.getUnitPrice().multiply(item.getQty())));
                categoryMap.put(category.getName(), dto);
            }
        }
        return new ArrayList<>(categoryMap.values());
    }

    public List<CustomerSalesDto> getCustomerSales(LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        List<Sale> sales = getSalesByDateRange(fromDate, toDate);

        // Bulk fetch all payments for these sales
        Set<Long> saleIds = sales.stream()
                .map(Sale::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> paidBySale = paymentService.getTotalPaidBySaleIds(saleIds);

        Map<Long, CustomerSalesDto> customerMap = new HashMap<>();

        for (Sale sale : sales) {
            if (sale.getCustomer() == null) continue;
            Long customerId = sale.getCustomer().getId();
            CustomerSalesDto dto = customerMap.getOrDefault(customerId, new CustomerSalesDto(
                    customerId,
                    sale.getCustomer().getName(),
                    ZERO,
                    ZERO
            ));
            dto.setTotalSales(dto.getTotalSales().add(sale.getTotalAmount()));

            BigDecimal paid = paidBySale.getOrDefault(sale.getId(), ZERO);
            dto.setTotalDue(dto.getTotalDue().add(sale.getTotalAmount().subtract(paid)));
            customerMap.put(customerId, dto);
        }
        return new ArrayList<>(customerMap.values());
    }

    public ExpensesSummaryDto getExpensesSummary(LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        List<Expense> expenses = getExpensesByDateRange(fromDate, toDate);

        BigDecimal total = ZERO;
        BigDecimal operational = ZERO;
        BigDecimal inventory = ZERO;

        for (Expense e : expenses) {
            BigDecimal amount = e.getAmount() != null ? e.getAmount() : ZERO;
            total = total.add(amount);

            String type = (e.getType() != null) ? e.getType().toLowerCase() : "";
            if (type.contains("inventory") || type.contains("purchase")) {
                inventory = inventory.add(amount);
            } else {
                operational = operational.add(amount);
            }
        }

        return new ExpensesSummaryDto(total, operational, inventory);
    }

    public PaymentsSummaryDto getPaymentsSummary(LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        List<Sale> sales = getSalesByDateRange(fromDate, toDate);

        // Bulk fetch all payments once
        Set<Long> saleIds = sales.stream()
                .map(Sale::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> paidBySale = paymentService.getTotalPaidBySaleIds(saleIds);

        BigDecimal totalPayments = paidBySale.values().stream().reduce(ZERO, BigDecimal::add);
        int paymentCount = paidBySale.values().stream()
                .mapToInt(paid -> paid.compareTo(ZERO) > 0 ? 1 : 0) // rough count - adjust if needed
                .sum();

        return new PaymentsSummaryDto(totalPayments, paymentCount);
    }
}