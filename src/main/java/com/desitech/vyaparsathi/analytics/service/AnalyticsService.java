package com.desitech.vyaparsathi.analytics.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderItem;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.analytics.config.AnalyticsCacheConfig;
import com.desitech.vyaparsathi.analytics.dto.*;
import com.desitech.vyaparsathi.analytics.model.AnalyticsRange;
import org.springframework.cache.annotation.Cacheable;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import com.desitech.vyaparsathi.inventory.repository.ItemVariantRepository;
import com.desitech.vyaparsathi.inventory.repository.StockMovementRepository;
import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
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
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MarginCalculator marginCalculator;

    public List<ItemDemandPredictionDto> predictItemDemand(Long itemId, AnalyticsRange range) {
        Long shopId = TenantContext.getCurrentShopId();
        AnalyticsRange prior = range.previousPeriod();
        Map<Long, Integer> currentPeriod = getVolume(shopId, range.startInclusive(), range.endInclusive());
        Map<Long, Integer> priorPeriod   = getVolume(shopId, prior.startInclusive(), prior.endInclusive());

        return currentPeriod.entrySet().stream()
                .filter(e -> itemId == null || e.getKey().equals(itemId))
                .map(entry -> {
                    ItemVariant v = itemVariantRepository.findById(entry.getKey()).orElse(null);
                    if (v == null) return null;
                    int cur  = entry.getValue();
                    int prev = priorPeriod.getOrDefault(entry.getKey(), 0);
                    String trend = (cur > prev) ? "Increasing" : (cur < prev ? "Decreasing" : "Stable");
                    return new ItemDemandPredictionDto(v.getId(), v.getItem().getName(), cur, trend);
                }).filter(Objects::nonNull).toList();
    }

    public List<CustomerTrendDto> getCustomerTrends(Long customerId, AnalyticsRange range) {
        logger.info("Calculating customer trends for customerId={} in range {}..{}",
                customerId, range.getFrom(), range.getTo());
        Long shopId = TenantContext.getCurrentShopId();
        List<Customer> customers = customerId == null
                ? customerRepository.findAllByShopId(shopId)
                : customerRepository.findById(customerId).map(List::of).orElse(List.of());

        LocalDateTime rangeStart = range.startInclusive();
        LocalDateTime rangeEnd = range.endInclusive();

        return customers.stream().map(customer -> {
            List<Sale> sales = saleRepository.findByCustomerId(
                            customer.getId(), org.springframework.data.domain.PageRequest.of(0, 100)).getContent()
                    .stream()
                    .filter(s -> s.getDate() != null
                            && !s.getDate().isBefore(rangeStart)
                            && !s.getDate().isAfter(rangeEnd))
                    .toList();
            Map<String, Long> itemCount = sales.stream()
                    .flatMap(sale -> sale.getSaleItems().stream())
                    .filter(item -> item.getItemVariant() != null) // exclude custom/service lines from behavior analytics
                    .collect(Collectors.groupingBy(
                            item -> item.getItemVariant().getItem().getName(),
                            Collectors.summingLong(item -> item.getQty().longValue())
                    ));

            List<String> frequentItems = itemCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            String buyingPattern = sales.size() > 10 ? "frequent" : (!sales.isEmpty() ? "occasional" : "none");
            return new CustomerTrendDto(customer.getId(), customer.getName(), buyingPattern, frequentItems);
        }).collect(Collectors.toList());
    }

    public List<PurchaseOrderSuggestionDto> suggestFuturePurchaseOrders() {
        Long shopId = TenantContext.getCurrentShopId();
        List<ItemVariant> variants = itemVariantRepository.findAllByShopId(shopId);
        List<Long> variantIds = variants.stream().map(ItemVariant::getId).toList();

        Map<Long, BigDecimal> stockMap = stockMovementRepository.findTotalQuantitiesByItemVariantIds(variantIds)
                .stream().collect(Collectors.toMap(s -> s.getVariantId(), s -> s.getTotalQuantity()));

        return variants.stream()
                .filter(v -> v.getLowStockThreshold() != null)
                .map(v -> {
                    BigDecimal current = stockMap.getOrDefault(v.getId(), BigDecimal.ZERO);
                    if (current.compareTo(v.getLowStockThreshold()) < 0) {
                        BigDecimal needed = v.getLowStockThreshold()
                                .multiply(BigDecimal.valueOf(1.2)).subtract(current);
                        BigDecimal cost = getEstimatedCostPrice(v);
                        return new PurchaseOrderSuggestionDto(
                                v.getId(), v.getItem().getName(), needed.doubleValue(), needed.multiply(cost));
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Cacheable(cacheNames = AnalyticsCacheConfig.CACHE_TOP_ITEMS,
            keyGenerator = AnalyticsCacheConfig.KEY_GEN_SHOP_SCOPED)
    public List<TopItemDto> getTopRisingFallingItems(AnalyticsRange range) {
        logger.info("Calculating top rising/falling items for range {}..{}", range.getFrom(), range.getTo());
        Long shopId = TenantContext.getCurrentShopId();
        AnalyticsRange prior = range.previousPeriod();

        Map<Long, Integer> lastMonthMap = getSalesVolumeByVariant(shopId, range.startInclusive(), range.endInclusive());
        Map<Long, Integer> prevMonthMap = getSalesVolumeByVariant(shopId, prior.startInclusive(), prior.endInclusive());

        Set<Long> allVariantIds = new HashSet<>(lastMonthMap.keySet());
        allVariantIds.addAll(prevMonthMap.keySet());

        if (allVariantIds.isEmpty()) return Collections.emptyList();

        Map<Long, ItemVariant> variantMap = itemVariantRepository.findAllById(allVariantIds).stream()
                .collect(Collectors.toMap(ItemVariant::getId, Function.identity()));

        List<TopItemDto> result = allVariantIds.stream().map(id -> {
            int lastQty = lastMonthMap.getOrDefault(id, 0);
            int prevQty = prevMonthMap.getOrDefault(id, 0);
            if (lastQty == 0 && prevQty == 0) return null;
            double change = (prevQty == 0) ? 100.0 : ((double)(lastQty - prevQty) * 100.0 / prevQty);
            ItemVariant variant = variantMap.get(id);
            if (variant != null) return new TopItemDto(id, variant.getItem().getName(), change, change >= 0);
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());

        result.sort(Comparator.comparing(dto -> Math.abs(dto.getChangePercent()), Comparator.reverseOrder()));
        return result.stream().limit(10).collect(Collectors.toList());
    }

    private Map<Long, Integer> getSalesVolumeByVariant(Long shopId, LocalDateTime start, LocalDateTime end) {
        return saleRepository.findSalesForAnalyticsByShop(shopId, start, end).stream()
                .flatMap(sale -> sale.getSaleItems().stream())
                .filter(item -> item.getItemVariant() != null)
                .collect(Collectors.groupingBy(
                        item -> item.getItemVariant().getId(),
                        Collectors.summingInt(item -> item.getQty().intValue())
                ));
    }

    @Cacheable(cacheNames = AnalyticsCacheConfig.CACHE_SEASONAL_TRENDS,
            keyGenerator = AnalyticsCacheConfig.KEY_GEN_SHOP_SCOPED)
    public List<SeasonalTrendDto> getSeasonalTrends(AnalyticsRange range) {
        logger.info("Calculating seasonal sales trends for range {}..{}", range.getFrom(), range.getTo());
        Long shopId = TenantContext.getCurrentShopId();

        List<Sale> sales = saleRepository.findSalesForAnalyticsByShop(
                shopId, range.startInclusive(), range.endInclusive());

        Map<Integer, Long> monthCount = sales.stream()
                .filter(s -> s.getDate() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getDate().getMonthValue(),
                        Collectors.counting()
                ));
        Map<Integer, BigDecimal> monthRevenue = sales.stream()
                .filter(s -> s.getDate() != null && s.getTotalAmount() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getDate().getMonthValue(),
                        Collectors.reducing(BigDecimal.ZERO, Sale::getTotalAmount, BigDecimal::add)
                ));

        List<SeasonalTrendDto> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            long count = monthCount.getOrDefault(month, 0L);
            BigDecimal revenue = monthRevenue.getOrDefault(month, BigDecimal.ZERO);
            result.add(new SeasonalTrendDto(
                    month,
                    java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH),
                    seasonForMonth(month),
                    count,
                    revenue));
        }
        return result;
    }

    private static String seasonForMonth(int month) {
        return (month == 12 || month <= 2) ? "Winter"
                : (month <= 5 ? "Spring" : (month <= 8 ? "Summer" : "Autumn"));
    }

    /** Default at-risk cutoff when the caller does not specify one — matches historical behavior. */
    public static final int DEFAULT_CHURN_THRESHOLD_DAYS = 90;

    @Cacheable(cacheNames = AnalyticsCacheConfig.CACHE_CHURN,
            keyGenerator = AnalyticsCacheConfig.KEY_GEN_SHOP_SCOPED)
    public List<ChurnPredictionDto> predictChurn(int churnThresholdDays) {
        if (churnThresholdDays <= 0) churnThresholdDays = DEFAULT_CHURN_THRESHOLD_DAYS;
        Long shopId = TenantContext.getCurrentShopId();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(churnThresholdDays);

        List<Object[]> summaries = saleRepository.getCustomerPurchaseSummariesByShop(shopId);
        Map<Long, Object[]> summaryMap = summaries.stream()
                .collect(Collectors.toMap(obj -> (Long) obj[0], obj -> obj));

        return customerRepository.findAllByShopId(shopId).stream().map(customer -> {
            Object[] summary = summaryMap.get(customer.getId());
            LocalDateTime lastSale  = (summary != null) ? (LocalDateTime) summary[1] : null;
            BigDecimal totalSpent   = (summary != null) ? (BigDecimal) summary[2] : BigDecimal.ZERO;

            boolean isRisk = lastSale == null || lastSale.isBefore(cutoff);
            BigDecimal monthlyRisk = isRisk
                    ? totalSpent.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            return new ChurnPredictionDto(customer.getId(), customer.getName(), isRisk ? 0.9 : 0.1, monthlyRisk);
        }).collect(Collectors.toList());
    }

    private Map<Long, Integer> getVolume(Long shopId, LocalDateTime start, LocalDateTime end) {
        return saleRepository.findSalesForAnalyticsByShop(shopId, start, end).stream()
                .flatMap(s -> s.getSaleItems().stream())
                .filter(i -> i.getItemVariant() != null)
                .collect(Collectors.groupingBy(
                        i -> i.getItemVariant().getId(),
                        Collectors.summingInt(i -> i.getQty().intValue())
                ));
    }

    private BigDecimal getEstimatedCostPrice(ItemVariant v) {
        return purchaseOrderItemRepository.findTopByItemVariantId(v.getId())
                .map(PurchaseOrderItem::getUnitCost)
                .orElseGet(() -> v.getPricePerUnit() != null
                        ? v.getPricePerUnit().multiply(BigDecimal.valueOf(0.7))
                        : BigDecimal.ZERO);
    }

    // ─────────────────────────────────────────────────────────────
    //  KPI summary — total revenue, sale count, AOV, unique customers
    //  paired with the equally-sized prior period.
    // ─────────────────────────────────────────────────────────────
    @Cacheable(cacheNames = AnalyticsCacheConfig.CACHE_KPI_SUMMARY,
            keyGenerator = AnalyticsCacheConfig.KEY_GEN_SHOP_SCOPED)
    public KpiSummaryDto getKpiSummary(AnalyticsRange range) {
        Long shopId = TenantContext.getCurrentShopId();
        AnalyticsRange prior = range.previousPeriod();

        Aggregates current = aggregate(saleRepository.findSalesForAnalyticsByShop(
                shopId, range.startInclusive(), range.endInclusive()));
        Aggregates previous = aggregate(saleRepository.findSalesForAnalyticsByShop(
                shopId, prior.startInclusive(), prior.endInclusive()));

        return new KpiSummaryDto(
                range.getFrom(), range.getTo(),
                prior.getFrom(), prior.getTo(),
                KpiValueDto.of(current.revenue, previous.revenue),
                KpiValueDto.ofLong(current.saleCount, previous.saleCount),
                KpiValueDto.of(current.avgOrderValue(), previous.avgOrderValue()),
                KpiValueDto.ofLong(current.uniqueCustomers, previous.uniqueCustomers)
        );
    }

    private static Aggregates aggregate(List<Sale> sales) {
        Aggregates a = new Aggregates();
        Set<Long> customers = new HashSet<>();
        for (Sale s : sales) {
            a.saleCount++;
            if (s.getTotalAmount() != null) a.revenue = a.revenue.add(s.getTotalAmount());
            if (s.getCustomer() != null && s.getCustomer().getId() != null) {
                customers.add(s.getCustomer().getId());
            }
        }
        a.uniqueCustomers = customers.size();
        return a;
    }

    /** Small mutable holder used inside {@link #getKpiSummary}. */
    private static final class Aggregates {
        long saleCount;
        BigDecimal revenue = BigDecimal.ZERO;
        long uniqueCustomers;
        BigDecimal avgOrderValue() {
            return saleCount == 0
                    ? BigDecimal.ZERO
                    : revenue.divide(BigDecimal.valueOf(saleCount), 2, RoundingMode.HALF_UP);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Revenue time series — dense buckets by day / week / month.
    // ─────────────────────────────────────────────────────────────
    @Cacheable(cacheNames = AnalyticsCacheConfig.CACHE_REVENUE_TIMESERIES,
            keyGenerator = AnalyticsCacheConfig.KEY_GEN_SHOP_SCOPED)
    public RevenueTimeSeriesDto getRevenueTimeSeries(AnalyticsRange range) {
        Long shopId = TenantContext.getCurrentShopId();
        AnalyticsRange.Granularity granularity = range.getGranularity();

        List<Sale> sales = saleRepository.findSalesForAnalyticsByShop(
                shopId, range.startInclusive(), range.endInclusive());

        // Bucket sales by their period start.
        Map<LocalDate, RevenueBucketDto> byBucket = new HashMap<>();
        for (Sale s : sales) {
            if (s.getDate() == null) continue;
            LocalDate key = bucketStart(s.getDate().toLocalDate(), granularity);
            RevenueBucketDto b = byBucket.computeIfAbsent(key,
                    k -> new RevenueBucketDto(k, BigDecimal.ZERO, 0L));
            if (s.getTotalAmount() != null) b.setRevenue(b.getRevenue().add(s.getTotalAmount()));
            b.setSaleCount(b.getSaleCount() + 1);
        }

        // Dense fill from range.from → range.to.
        List<RevenueBucketDto> dense = new ArrayList<>();
        LocalDate cursor = bucketStart(range.getFrom(), granularity);
        LocalDate end = range.getTo();
        while (!cursor.isAfter(end)) {
            dense.add(byBucket.getOrDefault(cursor,
                    new RevenueBucketDto(cursor, BigDecimal.ZERO, 0L)));
            cursor = nextBucket(cursor, granularity);
        }

        return new RevenueTimeSeriesDto(range.getFrom(), range.getTo(), granularity, dense);
    }

    private static LocalDate bucketStart(LocalDate d, AnalyticsRange.Granularity g) {
        return switch (g) {
            case DAY   -> d;
            case WEEK  -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> d.withDayOfMonth(1);
        };
    }

    private static LocalDate nextBucket(LocalDate d, AnalyticsRange.Granularity g) {
        return switch (g) {
            case DAY   -> d.plusDays(1);
            case WEEK  -> d.plusWeeks(1);
            case MONTH -> d.plusMonths(1);
        };
    }

    // ─────────────────────────────────────────────────────────────
    //  Payment mix — share of revenue by tender method.
    //  Uses actual Payment records (not sale.paymentMethod) so
    //  multi-tender/partial payments are reflected correctly.
    // ─────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────
    //  Gross margin — revenue minus cost-of-goods over a range.
    // ─────────────────────────────────────────────────────────────
    @Cacheable(cacheNames = AnalyticsCacheConfig.CACHE_GROSS_MARGIN,
            keyGenerator = AnalyticsCacheConfig.KEY_GEN_SHOP_SCOPED)
    public GrossMarginDto getGrossMargin(AnalyticsRange range) {
        Long shopId = TenantContext.getCurrentShopId();
        List<Sale> sales = saleRepository.findSalesForAnalyticsByShop(
                shopId, range.startInclusive(), range.endInclusive());
        return marginCalculator.compute(range, sales);
    }

    @Cacheable(cacheNames = AnalyticsCacheConfig.CACHE_PAYMENT_MIX,
            keyGenerator = AnalyticsCacheConfig.KEY_GEN_SHOP_SCOPED)
    public PaymentMixDto getPaymentMix(AnalyticsRange range) {
        Long shopId = TenantContext.getCurrentShopId();
        List<Object[]> rows = paymentRepository.sumSalePaymentsByMethodForShop(
                shopId, range.startInclusive(), range.endInclusive());

        BigDecimal total = BigDecimal.ZERO;
        long totalTxn = 0L;
        List<PaymentMethodShareDto> methods = new ArrayList<>();
        for (Object[] r : rows) {
            PaymentMethod method = (PaymentMethod) r[0];
            BigDecimal amount = (r[1] == null) ? BigDecimal.ZERO : (BigDecimal) r[1];
            long count = (r[2] == null) ? 0L : ((Number) r[2]).longValue();
            methods.add(new PaymentMethodShareDto(method, amount, count, 0.0));
            total = total.add(amount);
            totalTxn += count;
        }

        // Second pass to fill percentages once we know the total.
        for (PaymentMethodShareDto m : methods) {
            if (total.signum() == 0) {
                m.setPercentage(0.0);
            } else {
                m.setPercentage(m.getAmount()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, RoundingMode.HALF_UP)
                        .doubleValue());
            }
        }
        methods.sort(Comparator.comparing(PaymentMethodShareDto::getAmount).reversed());

        return new PaymentMixDto(range.getFrom(), range.getTo(), total, totalTxn, methods);
    }
}
