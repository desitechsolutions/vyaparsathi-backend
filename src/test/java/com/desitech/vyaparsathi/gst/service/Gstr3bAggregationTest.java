package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.enums.SupplyType;
import com.desitech.vyaparsathi.gst.dto.Gstr3bSummaryDto;
import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoice;
import com.desitech.vyaparsathi.purchases.repository.PurchaseInvoiceRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.sales.enums.GSTType;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Gstr3bBuilder — aggregation logic")
class Gstr3bAggregationTest {

    private static final Long   SHOP_ID    = 1L;
    private static final String SHOP_GSTIN = "27ABCDE1234F1Z5";
    private static final String SHOP_CODE  = "27";
    private static final String POS_INTRA  = "27 Maharashtra";
    private static final String POS_INTER  = "29 Karnataka";
    private static final String POS_INTER_CODE = "29";

    @Mock private SaleRepository            saleRepo;
    @Mock private PurchaseInvoiceRepository purchaseRepo;
    @Mock private ShopRepository            shopRepo;
    @Mock private GstJurisdictionService    jurisdictionService;
    @Mock private GstOffsetService          offsetService;

    private Gstr3bBuilder builder;
    private Shop shop;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentShopId(SHOP_ID);
        shop = new Shop();
        shop.setId(SHOP_ID);
        shop.setGstin(SHOP_GSTIN);
        when(shopRepo.findById(SHOP_ID)).thenReturn(Optional.of(shop));
        lenient().when(jurisdictionService.resolveStateCode(shop)).thenReturn(Optional.of(SHOP_CODE));
        lenient().when(jurisdictionService.isIntraState(eq(SHOP_CODE), eq(SHOP_CODE))).thenReturn(true);
        lenient().when(jurisdictionService.isIntraState(eq(SHOP_CODE), eq(POS_INTER_CODE))).thenReturn(false);

        // Default: no purchases
        lenient().when(purchaseRepo.findAllByShopIdAndPurchaseDateBetween(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                 .thenReturn(Collections.emptyList());

        // Stub offsetService to return zero result by default so Section 6 doesn't NPE
        GstOffsetService.TaxComponents zero = GstOffsetService.TaxComponents.zero();
        lenient().when(offsetService.applyOffset(any(), any()))
                 .thenReturn(new GstOffsetService.OffsetResult(zero, zero, zero));

        builder = new Gstr3bBuilder(saleRepo, purchaseRepo, shopRepo, jurisdictionService, offsetService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Test 1 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("b2b intra-state sale → Section 3.1(a) outward taxable")
    void b2bIntraSale_appearsIn31a() {
        Sale sale = intraSale(bd("1000"), bd("90"), bd("90"), bd("0"));
        stubSales(sale);

        Gstr3bSummaryDto result = builder.buildSummary(2026, 9);

        assertThat(result.getOutwardTaxableSupplies().getCgst()).isEqualByComparingTo(bd("90"));
        assertThat(result.getZeroRatedExportSupplies().getTaxableValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Test 2 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("export WOPAY sale → Section 3.1(b) zero-rated export")
    void exportSale_wopay_appearsIn31b() {
        Sale sale = exportSale(SupplyType.EXPORT_WITHOUT_PAYMENT, bd("5000"), bd("0"), bd("0"), bd("0"));
        stubSales(sale);

        Gstr3bSummaryDto result = builder.buildSummary(2026, 9);

        assertThat(result.getZeroRatedExportSupplies().getTaxableValue()).isEqualByComparingTo(bd("5000"));
        assertThat(result.getOutwardTaxableSupplies().getTaxableValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Test 3 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("nil-rated sale → Section 3.1(c) nil/exempt")
    void nilRatedSale_appearsIn31c() {
        Sale sale = nilSale(bd("800"));
        stubSales(sale);

        Gstr3bSummaryDto result = builder.buildSummary(2026, 9);

        assertThat(result.getNilRatedExemptSupplies().getTaxableValue()).isEqualByComparingTo(bd("800"));
        assertThat(result.getOutwardTaxableSupplies().getTaxableValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Test 4 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("inter-state B2C sale → Table 3.2 POS grouping")
    void interStateB2cSale_appearsInTable32() {
        Sale sale = interStateSale(bd("2000"), bd("0"), bd("0"), bd("360"));
        sale.setPlaceOfSupply(POS_INTER);   // "29 Karnataka" — prefix "29"
        stubSales(sale);

        Gstr3bSummaryDto result = builder.buildSummary(2026, 9);

        assertThat(result.getInterStateUnregistered()).hasSize(1);
        Gstr3bSummaryDto.InterStatePosEntry entry = result.getInterStateUnregistered().get(0);
        assertThat(entry.getPos()).isEqualTo(POS_INTER_CODE);
        assertThat(entry.getTaxableValue()).isEqualByComparingTo(bd("2000"));
        assertThat(entry.getIgst()).isEqualByComparingTo(bd("360"));
    }

    // ── Test 5 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RCM purchase → 3.1(d) inward liability AND 4(A)(3) ISRC ITC")
    void rcmPurchase_appearsIn31d_and_4a3Isrc() {
        PurchaseInvoice rcm = buildPurchase(true, true, bd("90"), bd("90"), bd("0"), bd("1000"));
        stubPurchases(rcm);

        Gstr3bSummaryDto result = builder.buildSummary(2026, 9);

        assertThat(result.getInwardReverseChargeSupplies().getCgst()).isEqualByComparingTo(bd("90"));
        assertThat(result.getItcRcm().getCgst()).isEqualByComparingTo(bd("90"));
        assertThat(result.getItcOther().getCgst()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Test 6 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ineligible purchase → Section 4(D) itcIneligible")
    void ineligiblePurchase_appearsIn4d() {
        PurchaseInvoice inel = buildPurchase(false, false, bd("45"), bd("45"), bd("0"), bd("500"));
        stubPurchases(inel);

        Gstr3bSummaryDto result = builder.buildSummary(2026, 9);

        assertThat(result.getItcIneligible().getCgst()).isEqualByComparingTo(bd("45"));
        assertThat(result.getItcOther().getCgst()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getItcRcm().getCgst()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Test 7 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("eligible non-RCM purchase → Section 4(A)(5) OTH ITC")
    void eligibleNonRcmPurchase_appearsIn4a5Oth() {
        PurchaseInvoice oth = buildPurchase(false, true, bd("180"), bd("180"), bd("0"), bd("2000"));
        stubPurchases(oth);

        Gstr3bSummaryDto result = builder.buildSummary(2026, 9);

        assertThat(result.getItcOther().getCgst()).isEqualByComparingTo(bd("180"));
        assertThat(result.getItcRcm().getCgst()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getItcAvailable().getCgst()).isEqualByComparingTo(bd("180"));
    }

    // ── Test 8 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Section 6 offset populated from GstOffsetService")
    void section6Offset_populatedFromOffsetService() {
        Sale sale = intraSale(bd("5000"), bd("450"), bd("450"), bd("0"));
        stubSales(sale);

        // Stub offset service to return specific cash payable
        GstOffsetService.TaxComponents cash = GstOffsetService.TaxComponents.of(
                BigDecimal.ZERO, bd("200"), bd("200"), BigDecimal.ZERO, BigDecimal.ZERO);
        GstOffsetService.TaxComponents paid = GstOffsetService.TaxComponents.of(
                BigDecimal.ZERO, bd("250"), bd("250"), BigDecimal.ZERO, BigDecimal.ZERO);
        GstOffsetService.TaxComponents closing = GstOffsetService.TaxComponents.zero();
        when(offsetService.applyOffset(any(), any()))
                .thenReturn(new GstOffsetService.OffsetResult(paid, cash, closing));

        Gstr3bSummaryDto result = builder.buildSummary(2026, 9);

        assertThat(result.getNetTaxLiability().getCgstPayable()).isEqualByComparingTo(bd("200"));
        assertThat(result.getItcOffset().getPaidInCash().getCgst()).isEqualByComparingTo(bd("200"));
        assertThat(result.getItcOffset().getPaidThroughItc().getCgst()).isEqualByComparingTo(bd("250"));
    }

    // ── Test 9 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HELD sale → excluded from all sections")
    void heldSale_excludedFromAllSections() {
        Sale held = intraSale(bd("10000"), bd("900"), bd("900"), bd("0"));
        held.setStatus(SaleStatus.HELD);
        stubSales(held);

        Gstr3bSummaryDto result = builder.buildSummary(2026, 9);

        assertThat(result.getOutwardTaxableSupplies().getTaxableValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getOutwardTaxableSupplies().getCgst()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getInterStateUnregistered()).isEmpty();
    }

    // ── Fixture helpers ───────────────────────────────────────────────────────

    private Sale intraSale(BigDecimal txval, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {
        Sale sale = baseSale();
        sale.setPlaceOfSupply(POS_INTRA);
        SaleItem item = saleItem(GSTType.GST_18, txval, cgst, sgst, igst);
        sale.getSaleItems().add(item);
        return sale;
    }

    private Sale interStateSale(BigDecimal txval, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {
        Sale sale = baseSale();
        // no customer GSTIN — walk-in B2C
        sale.setSupplyType(SupplyType.INTERSTATE);
        SaleItem item = saleItem(GSTType.GST_18, txval, cgst, sgst, igst);
        sale.getSaleItems().add(item);
        return sale;
    }

    private Sale exportSale(SupplyType type, BigDecimal txval, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {
        Sale sale = baseSale();
        sale.setSupplyType(type);
        SaleItem item = saleItem(GSTType.GST_0, txval, cgst, sgst, igst);
        sale.getSaleItems().add(item);
        return sale;
    }

    private Sale nilSale(BigDecimal txval) {
        Sale sale = baseSale();
        SaleItem item = saleItem(GSTType.GST_0, txval, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        sale.getSaleItems().add(item);
        return sale;
    }

    private Sale baseSale() {
        Sale sale = new Sale();
        sale.setDate(LocalDateTime.of(2026, 9, 10, 12, 0));
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setIsGstRequired(true);
        sale.setSupplyType(SupplyType.INTRASTATE);
        return sale;
    }

    private SaleItem saleItem(GSTType gstType, BigDecimal txval, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {
        SaleItem item = new SaleItem();
        item.setGstType(gstType);
        item.setTaxableValue(txval);
        item.setCgstAmt(cgst);
        item.setSgstAmt(sgst);
        item.setIgstAmt(igst);
        return item;
    }

    private PurchaseInvoice buildPurchase(boolean rcm, boolean eligible,
                                          BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                                          BigDecimal taxable) {
        PurchaseInvoice p = new PurchaseInvoice();
        p.setReverseCharge(rcm);
        p.setIsItcEligible(eligible);
        p.setTotalCgst(cgst);
        p.setTotalSgst(sgst);
        p.setTotalIgst(igst);
        p.setTotalTaxableAmount(taxable);
        return p;
    }

    private void stubSales(Sale... sales) {
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(sales));
    }

    private void stubPurchases(PurchaseInvoice... purchases) {
        when(purchaseRepo.findAllByShopIdAndPurchaseDateBetween(eq(SHOP_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(purchases));
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
