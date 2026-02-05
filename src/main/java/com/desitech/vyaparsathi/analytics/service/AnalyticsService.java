package com.desitech.vyaparsathi.analytics.service;

import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.analytics.dto.*;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
// REMOVED: Obsolete repository
// import com.desitech.vyaparsathi.inventory.repository.StockEntryRepository;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    @Autowired private SaleRepository saleRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemVariantRepository itemVariantRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private PurchaseOrderItemRepository purchaseOrderItemRepository;

    public List<ItemDemandPredictionDto> predictItemDemand(Long itemId) {
        LocalDateTime now = LocalDateTime.now();
        // Use the new lightweight query here!
        Map<Long, Integer> currentMonth = getVolume(now.minusMonths(1), now);
        Map<Long, Integer> prevMonth = getVolume(now.minusMonths(2), now.minusMonths(1));

        return currentMonth.entrySet().stream()
                .filter(e -> itemId == null || e.getKey().equals(itemId))
                .map(entry -> {
                    ItemVariant v = itemVariantRepository.findById(entry.getKey()).orElse(null);
                    if (v == null) return null;
                    int cur = entry.getValue();
                    int prev = prevMonth.getOrDefault(entry.getKey(), 0);
                    String trend = (cur > prev) ? "Increasing" : (cur < prev ? "Decreasing" : "Stable");
                    return new ItemDemandPredictionDto(v.getId(), v.getItem().getName(), cur, trend);
                }).filter(Objects::nonNull).toList();
    }

    public List<CustomerTrendDto> getCustomerTrends(Long customerId) {
        logger.info("Calculating customer trends for customerId={}", customerId);
        List<Customer> customers = customerId == null ? customerRepository.findAll() :
                customerRepository.findById(customerId).map(List::of).orElse(List.of());

        return customers.stream().map(customer -> {
            List<Sale> sales = saleRepository.findByCustomerId(customer.getId(), org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
            Map<String, Long> itemCount = sales.stream()
                    .flatMap(sale -> sale.getSaleItems().stream())
                    .collect(Collectors.groupingBy(
                            item -> item.getItemVariant().getItem().getName(),
                            Collectors.summingLong(item -> item.getQty().longValue())
                    ));

            List<String> frequentItems = itemCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            String buyingPattern = sales.size() > 10 ? "frequent" : (sales.size() > 0 ? "occasional" : "none");
            return new CustomerTrendDto(customer.getId(), customer.getName(), buyingPattern, frequentItems);
        }).collect(Collectors.toList());
    }

    public List<PurchaseOrderSuggestionDto> suggestFuturePurchaseOrders() {
        List<ItemVariant> variants = itemVariantRepository.findAll();
        List<Long> variantIds = variants.stream().map(ItemVariant::getId).toList();

        // 1. Get Stock levels
        Map<Long, BigDecimal> stockMap = stockMovementRepository.findTotalQuantitiesByItemVariantIds(variantIds)
                .stream().collect(Collectors.toMap(s -> s.getVariantId(), s -> s.getTotalQuantity()));

        // 2. Optimization: Get the last known purchase price for each variant to estimate investment accurately
        // If you don't have a costPrice field, we look at the last Purchase Order
        return variants.stream()
                .filter(v -> v.getLowStockThreshold() != null)
                .map(v -> {
                    BigDecimal current = stockMap.getOrDefault(v.getId(), BigDecimal.ZERO);

                    if (current.compareTo(v.getLowStockThreshold()) < 0) {
                        // Calculate how many units to buy (Safety stock of 20%)
                        BigDecimal needed = v.getLowStockThreshold().multiply(BigDecimal.valueOf(1.2)).subtract(current);

                        // --- PRICE LOGIC FIX ---
                        // Try to get a cost price. If ItemVariant has a costPrice field, use that.
                        // Otherwise, we use the PurchaseOrder history.
                        BigDecimal actualCostPrice = getEstimatedCostPrice(v);

                        BigDecimal totalInvestment = needed.multiply(actualCostPrice);

                        return new PurchaseOrderSuggestionDto(v.getId(), v.getItem().getName(), needed.doubleValue(), totalInvestment);
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());
    }
    public List<TopItemDto> getTopRisingFallingItems() {
        logger.info("Calculating top rising/falling items");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastMonthStart = now.minusMonths(1);
        LocalDateTime prevMonthStart = now.minusMonths(2);

        Map<Long, Integer> lastMonthMap = getSalesVolumeByVariant(lastMonthStart, now);
        Map<Long, Integer> prevMonthMap = getSalesVolumeByVariant(prevMonthStart, lastMonthStart);

        Set<Long> allVariantIds = new HashSet<>(lastMonthMap.keySet());
        allVariantIds.addAll(prevMonthMap.keySet());

        if (allVariantIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, ItemVariant> variantMap = itemVariantRepository.findAllById(allVariantIds).stream()
                .collect(Collectors.toMap(ItemVariant::getId, Function.identity()));

        List<TopItemDto> result = allVariantIds.stream().map(id -> {
            int lastQty = lastMonthMap.getOrDefault(id, 0);
            int prevQty = prevMonthMap.getOrDefault(id, 0);
            if (lastQty == 0 && prevQty == 0) return null;

            double change = (prevQty == 0) ? 100.0 : ((double) (lastQty - prevQty) * 100.0 / prevQty);
            ItemVariant variant = variantMap.get(id);
            if (variant != null) {
                return new TopItemDto(id, variant.getItem().getName(), change, change >= 0);
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());

        result.sort(Comparator.comparing(
                dto -> Math.abs(dto.getChangePercent()),
                Comparator.reverseOrder()
        ));

        return result.stream().limit(10).collect(Collectors.toList());
    }
    private Map<Long, Integer> getSalesVolumeByVariant(LocalDateTime start, LocalDateTime end) {
        return saleRepository.findByDateBetween(start, end).stream()
                .flatMap(sale -> sale.getSaleItems().stream())
                .collect(Collectors.groupingBy(
                        item -> item.getItemVariant().getId(),
                        Collectors.summingInt(item -> item.getQty().intValue())
                ));
    }
    public List<SeasonalTrendDto> getSeasonalTrends() {
        logger.info("Calculating seasonal sales trends");
        Map<Integer, Long> monthSales = saleRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        sale -> sale.getDate().getMonthValue(),
                        Collectors.counting()
                ));

        List<SeasonalTrendDto> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            long count = monthSales.getOrDefault(month, 0L);
            String season = (month == 12 || month <= 2) ? "Winter" : (month <= 5 ? "Spring" : (month <= 8 ? "Summer" : "Autumn"));
            result.add(new SeasonalTrendDto(season + " (Month " + month + ")", "Sales: " + count));
        }
        return result;
    }

    public List<ChurnPredictionDto> predictChurn() {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        List<Object[]> summaries = saleRepository.getCustomerPurchaseSummaries();
        Map<Long, Object[]> summaryMap = summaries.stream()
                .collect(Collectors.toMap(obj -> (Long)obj[0], obj -> obj));

        return customerRepository.findAll().stream().map(customer -> {
            Object[] summary = summaryMap.get(customer.getId());
            LocalDateTime lastSale = (summary != null) ? (LocalDateTime)summary[1] : null;
            BigDecimal totalSpent = (summary != null) ? (BigDecimal)summary[2] : BigDecimal.ZERO;

            boolean isRisk = lastSale == null || lastSale.isBefore(threeMonthsAgo);
            // Professional Insight: Calculate average monthly loss if they leave
            BigDecimal monthlyRisk = isRisk ? totalSpent.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            return new ChurnPredictionDto(customer.getId(), customer.getName(), isRisk ? 0.9 : 0.1, monthlyRisk);
        }).collect(Collectors.toList());
    }

    private Map<Long, Integer> getVolume(LocalDateTime start, LocalDateTime end) {
        return saleRepository.findSalesForAnalytics(start, end).stream()
                .flatMap(s -> s.getSaleItems().stream())
                .collect(Collectors.groupingBy(i -> i.getItemVariant().getId(), Collectors.summingInt(i -> i.getQty().intValue())));
    }
    private BigDecimal getEstimatedCostPrice(ItemVariant v) {
        // 1. If your entity has costPrice, use it:
        // if (v.getCostPrice() != null) return v.getCostPrice();

        // 2. Fallback: Get the latest price from the Purchase Order items repository
        // This is more accurate than selling price
        return purchaseOrderItemRepository.findTopByItemVariantId(v.getId())
                .map(PurchaseOrderItem::getUnitCost)
                .orElseGet(() -> {
                    // 3. Ultimate Fallback: 70% of Selling Price (assuming a 30% margin)
                    // This prevents the "Investment" KPI from being gross selling value
                    return v.getPricePerUnit() != null ?
                            v.getPricePerUnit().multiply(BigDecimal.valueOf(0.7)) : BigDecimal.ZERO;
                });
    }
}