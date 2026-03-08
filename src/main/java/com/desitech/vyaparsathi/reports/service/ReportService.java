package com.desitech.vyaparsathi.reports.service;

import com.desitech.vyaparsathi.inventory.entity.Category;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.enums.DrugSchedule;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.service.StockService;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import com.desitech.vyaparsathi.receiving.entity.Receiving;
import com.desitech.vyaparsathi.receiving.entity.ReceivingItem;
import com.desitech.vyaparsathi.receiving.repository.ReceivingRepository;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Autowired
    private ItemVariantRepository itemVariantRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private ReceivingRepository receivingRepository;

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

    public byte[] generateAuditZip(LocalDate from, LocalDate to) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 1. Sales Register (GSTR-1 style)
            addToZip(zos, "Sales_Register_" + from + "_to_" + to + ".csv", generateSalesRegisterCsv(from, to));

            // 2. HSN Summary (Table 12 style)
            addToZip(zos, "HSN_Summary_" + from + "_to_" + to + ".csv", generateHsnSummaryCsv(from, to));

            // 3. Purchase Register (For ITC)
            addToZip(zos, "Purchase_ITC_Register.csv", generatePurchaseRegisterCsv(from, to));

            // 4. P&L Overview
            addToZip(zos, "Financial_Summary.txt", generatePnLReport(from, to));
        }
        return baos.toByteArray();
    }

    private void addToZip(ZipOutputStream zos, String fileName, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(fileName));
        zos.write(content.getBytes());
        zos.closeEntry();
    }

    private String generateSalesRegisterCsv(LocalDate from, LocalDate to) {
        List<Sale> sales = getSalesByDateRange(from, to);
        StringBuilder csv = new StringBuilder("Invoice No,Date,Customer Name,Customer GSTIN,POS,Type,Taxable,CGST,SGST,IGST,Total\n");

        for (Sale sale : sales) {
            BigDecimal txbl = sale.getSaleItems().stream().map(si -> si.getTaxableValue() != null ? si.getTaxableValue() : ZERO).reduce(ZERO, BigDecimal::add);
            BigDecimal cgst = sale.getSaleItems().stream().map(si -> si.getCgstAmt() != null ? si.getCgstAmt() : ZERO).reduce(ZERO, BigDecimal::add);
            BigDecimal sgst = sale.getSaleItems().stream().map(si -> si.getSgstAmt() != null ? si.getSgstAmt() : ZERO).reduce(ZERO, BigDecimal::add);
            BigDecimal igst = sale.getSaleItems().stream().map(si -> si.getIgstAmt() != null ? si.getIgstAmt() : ZERO).reduce(ZERO, BigDecimal::add);

            String gstin = (sale.getCustomer().getGstNumber() != null) ? sale.getCustomer().getGstNumber() : "";
            String type = gstin.isEmpty() ? "B2C" : "B2B";

            csv.append(String.format("%s,%s,\"%s\",%s,%s,%s,%s,%s,%s,%s,%s\n",
                    sale.getInvoiceNo(), sale.getDate().toLocalDate(), sale.getCustomer().getName(),
                    gstin, (sale.getCustomer().getState() != null ? sale.getCustomer().getState() : ""),
                    type, txbl, cgst, sgst, igst, sale.getTotalAmount()));
        }
        return csv.toString();
    }

    private String generateHsnSummaryCsv(LocalDate from, LocalDate to) {
        List<Sale> sales = getSalesByDateRange(from, to);
        Map<String, List<SaleItem>> hsnMap = sales.stream()
                .flatMap(s -> s.getSaleItems().stream())
                .collect(Collectors.groupingBy(si -> si.getItemVariant().getHsn() != null ? si.getItemVariant().getHsn() : "NA"));

        StringBuilder csv = new StringBuilder("HSN,Description,UQC,Qty,Taxable,IGST,CGST,SGST\n");
        hsnMap.forEach((hsn, items) -> {
            BigDecimal qty = items.stream().map(SaleItem::getQty).reduce(ZERO, BigDecimal::add);
            BigDecimal tx = items.stream().map(si -> si.getTaxableValue() != null ? si.getTaxableValue() : ZERO).reduce(ZERO, BigDecimal::add);
            csv.append(String.format("%s,\"%s\",NOS,%s,%s,%s,%s,%s\n",
                    hsn, items.get(0).getItemVariant().getItem().getName(), qty, tx,
                    items.stream().map(si -> si.getIgstAmt() != null ? si.getIgstAmt() : ZERO).reduce(ZERO, BigDecimal::add),
                    items.stream().map(si -> si.getCgstAmt() != null ? si.getCgstAmt() : ZERO).reduce(ZERO, BigDecimal::add),
                    items.stream().map(si -> si.getSgstAmt() != null ? si.getSgstAmt() : ZERO).reduce(ZERO, BigDecimal::add)));
        });
        return csv.toString();
    }

    private String generatePurchaseRegisterCsv(LocalDate from, LocalDate to) {
        List<Expense> expenses = getExpensesByDateRange(from, to).stream()
                .filter(e -> {
                    String t = e.getType() != null ? e.getType().toLowerCase() : "";
                    return t.contains("inventory") || t.contains("purchase");
                }).collect(Collectors.toList());

        StringBuilder csv = new StringBuilder("Expense ID,Date,Vendor/Description,Amount,Type\n");
        for (Expense e : expenses) {
            csv.append(String.format("%d,%s,\"%s\",%s,%s\n",
                    e.getId(), e.getDate().toLocalDate(), e.getNotes(), e.getAmount(), e.getType()));
        }
        return csv.toString();
    }

    private String generatePnLReport(LocalDate from, LocalDate to) {
        SalesSummaryDto s = getSalesSummary(from, to);
        return "VYAPARSATHI COMPLIANCE SUMMARY\nPeriod: " + from + " to " + to + "\n" +
                "---------------------------------\n" +
                "Net Sales: " + s.getNetRevenue() + "\n" +
                "Total COGS: " + s.getTotalCOGS() + "\n" +
                "Gross Profit: " + s.getNetRevenue().subtract(s.getTotalCOGS()) + "\n" +
                "Operational Expenses: " + calculateOperationalExpenses(getExpensesByDateRange(from, to)) + "\n" +
                "Estimated Net Profit: " + s.getNetProfit();
    }

    // -------------------------------------------------------------------------
    // PHARMACY-SPECIFIC REPORTS
    // -------------------------------------------------------------------------

    /**
     * Returns items whose expiry date falls within the next {@code days} days (including already-expired).
     * Powers GET /api/reports/expiry-report?days={days}
     */
    public List<ExpiryReportItemDto> getExpiryReport(int days) {
        LocalDate cutoffDate = LocalDate.now().plusDays(days);
        List<ItemVariant> expiringVariants = itemVariantRepository.findByExpiryDateOnOrBefore(cutoffDate);

        if (expiringVariants.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> variantIds = expiringVariants.stream()
                .map(ItemVariant::getId)
                .collect(Collectors.toList());

        Map<Long, BigDecimal> stockMap = stockService.getStocksForVariants(variantIds);
        LocalDate today = LocalDate.now();

        return expiringVariants.stream()
                .map(variant -> {
                    ExpiryReportItemDto dto = new ExpiryReportItemDto();
                    dto.setItemVariantId(variant.getId());
                    dto.setItemName(variant.getItem().getName());
                    dto.setComposition(variant.getItem().getComposition());
                    dto.setSku(variant.getSku());
                    dto.setBatchNumber(variant.getBatchNumber());
                    dto.setManufacturingDate(variant.getManufacturingDate());
                    dto.setExpiryDate(variant.getExpiryDate());
                    long daysToExpiry = ChronoUnit.DAYS.between(today, variant.getExpiryDate());
                    dto.setDaysToExpiry(daysToExpiry);
                    dto.setQuantity(stockMap.getOrDefault(variant.getId(), BigDecimal.ZERO));
                    dto.setUnit(variant.getUnit());
                    if (daysToExpiry < 0) {
                        dto.setAlertLevel("EXPIRED");
                    } else if (daysToExpiry <= 30) {
                        dto.setAlertLevel("CRITICAL");
                    } else {
                        dto.setAlertLevel("WARNING");
                    }
                    return dto;
                })
                .sorted(Comparator.comparing(ExpiryReportItemDto::getExpiryDate))
                .collect(Collectors.toList());
    }

    /**
     * Returns a register of sales for Schedule H, H1, and X (narcotic) drugs in a date range.
     * Powers GET /api/reports/narcotics-register?from={from}&amp;to={to}
     */
    public List<NarcoticsRegisterEntryDto> getNarcoticsRegister(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        List<Sale> sales = getSalesByDateRange(from, to);

        Set<String> controlledSchedules = Set.of(
                DrugSchedule.SCHEDULE_H.getValue(),
                DrugSchedule.SCHEDULE_H1.getValue(),
                DrugSchedule.SCHEDULE_X.getValue()
        );

        List<NarcoticsRegisterEntryDto> result = new ArrayList<>();
        for (Sale sale : sales) {
            for (SaleItem saleItem : sale.getSaleItems()) {
                ItemVariant variant = saleItem.getItemVariant();
                if (variant == null || variant.getItem() == null) continue;

                DrugSchedule schedule = variant.getItem().getDrugSchedule();
                if (schedule == null || !controlledSchedules.contains(schedule.getValue())) continue;

                NarcoticsRegisterEntryDto entry = new NarcoticsRegisterEntryDto();
                entry.setSaleDate(sale.getDate());
                entry.setInvoiceNo(sale.getInvoiceNo());
                entry.setItemName(variant.getItem().getName());
                entry.setComposition(variant.getItem().getComposition());
                entry.setDrugSchedule(schedule.getValue());
                entry.setQty(saleItem.getQty());
                entry.setUnit(variant.getUnit());
                entry.setCustomerName(sale.getCustomer() != null ? sale.getCustomer().getName() : null);
                entry.setDoctorName(sale.getDoctorName());
                entry.setPatientName(sale.getPatientName());
                entry.setBatchNumber(variant.getBatchNumber());
                result.add(entry);
            }
        }

        result.sort(Comparator.comparing(NarcoticsRegisterEntryDto::getSaleDate));
        return result;
    }

    /**
     * Returns a batch-wise purchase register for a date range.
     * Powers GET /api/reports/purchase-register?from={from}&amp;to={to}
     */
    public List<PurchaseRegisterEntryDto> getPurchaseRegister(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(23, 59, 59);

        List<Receiving> receivings = receivingRepository.findByReceivedAtBetween(start, end);

        List<PurchaseRegisterEntryDto> result = new ArrayList<>();
        for (Receiving receiving : receivings) {
            String poNumber = receiving.getPurchaseOrder() != null
                    ? receiving.getPurchaseOrder().getPoNumber() : null;
            String supplierName = (receiving.getPurchaseOrder() != null
                    && receiving.getPurchaseOrder().getSupplier() != null)
                    ? receiving.getPurchaseOrder().getSupplier().getName() : null;
            LocalDate receivedDate = receiving.getReceivedAt() != null
                    ? receiving.getReceivedAt().toLocalDate() : null;

            if (receiving.getItems() == null) continue;
            for (ReceivingItem item : receiving.getItems()) {
                if (item.getPurchaseOrderItem() == null
                        || item.getPurchaseOrderItem().getItemVariant() == null) continue;

                ItemVariant variant = item.getPurchaseOrderItem().getItemVariant();
                BigDecimal unitCost = item.getPurchaseOrderItem().getUnitCost() != null
                        ? item.getPurchaseOrderItem().getUnitCost() : BigDecimal.ZERO;
                int receivedQty = item.getReceivedQty() != null ? item.getReceivedQty() : 0;
                BigDecimal totalCost = unitCost.multiply(BigDecimal.valueOf(receivedQty));

                PurchaseRegisterEntryDto entry = new PurchaseRegisterEntryDto();
                entry.setReceivedDate(receivedDate);
                entry.setPoNumber(poNumber);
                entry.setSupplierName(supplierName);
                entry.setItemName(variant.getItem() != null ? variant.getItem().getName() : null);
                entry.setComposition(variant.getItem() != null ? variant.getItem().getComposition() : null);
                entry.setBatchNumber(item.getBatchNumber());
                entry.setManufacturingDate(item.getManufacturingDate());
                entry.setExpiryDate(item.getExpiryDate());
                entry.setReceivedQty(receivedQty);
                entry.setUnit(variant.getUnit());
                entry.setUnitCost(unitCost);
                entry.setTotalCost(totalCost);
                result.add(entry);
            }
        }

        result.sort(Comparator.comparing(PurchaseRegisterEntryDto::getReceivedDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }
}