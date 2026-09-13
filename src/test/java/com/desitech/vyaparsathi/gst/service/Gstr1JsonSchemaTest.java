package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.accounting.entity.CreditNote;
import com.desitech.vyaparsathi.accounting.entity.CreditNoteItem;
import com.desitech.vyaparsathi.accounting.repository.CreditNoteRepository;
import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.enums.SupplyType;
import com.desitech.vyaparsathi.customer.entity.Customer;
import com.desitech.vyaparsathi.gst.dto.Gstr1ExportDto;
import com.desitech.vyaparsathi.gst.enums.NoteType;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.entity.SaleItem;
import com.desitech.vyaparsathi.sales.enums.GSTType;
import com.desitech.vyaparsathi.sales.enums.SaleStatus;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Gstr1Builder — GSTN V3.2 schema conformance")
class Gstr1JsonSchemaTest {

    private static final Long   SHOP_ID    = 1L;
    private static final String SHOP_GSTIN = "27ABCDE1234F1Z5";
    private static final String SHOP_CODE  = "27";
    private static final String CUST_GSTIN = "29XYZAB5678G1Z3";

    // Numeric POS prefix → used by resolvePosCode + isIntraState path in builder
    private static final String POS_INTRA = "27 Maharashtra";
    private static final String POS_INTER = "29 Karnataka";

    @Mock private SaleRepository         saleRepo;
    @Mock private CreditNoteRepository   creditRepo;
    @Mock private ShopRepository         shopRepo;
    @Mock private GstJurisdictionService jurisdictionService;

    private Gstr1Builder builder;
    private Shop         shop;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentShopId(SHOP_ID);

        shop = new Shop();
        shop.setId(SHOP_ID);
        shop.setGstin(SHOP_GSTIN);

        when(shopRepo.findById(SHOP_ID)).thenReturn(Optional.of(shop));
        lenient().when(jurisdictionService.resolveStateCode(shop)).thenReturn(Optional.of(SHOP_CODE));
        lenient().when(jurisdictionService.isIntraState(eq(SHOP_CODE), eq(SHOP_CODE))).thenReturn(true);
        lenient().when(jurisdictionService.isIntraState(eq(SHOP_CODE), eq("29"))).thenReturn(false);

        // Most tests have no credit notes
        lenient().when(creditRepo.findAllByShopIdAndCreditNoteDateBetween(eq(SHOP_ID), any(), any()))
                 .thenReturn(Collections.emptyList());

        builder = new Gstr1Builder(saleRepo, creditRepo, shopRepo, jurisdictionService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── B2B tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B2B: sale with registered GSTIN appears in b2b table with correct ctin")
    void b2bSale_appearsInB2bTable() {
        Sale sale = buildSale("INV-001", customerWithGstin(CUST_GSTIN), POS_INTRA,
                              SaleStatus.COMPLETED, null, bd("10000"), List.of(item18(bd("10000"))));
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(sale));

        Gstr1ExportDto dto = builder.build(2026, 9);

        assertThat(dto.getB2b()).hasSize(1);
        assertThat(dto.getB2b().get(0).getCtin()).isEqualTo(CUST_GSTIN);
        assertThat(dto.getB2b().get(0).getInv()).hasSize(1);
        assertThat(dto.getB2b().get(0).getInv().get(0).getInum()).isEqualTo("INV-001");
        assertThat(dto.getB2cs()).isEmpty();
        assertThat(dto.getB2cl()).isEmpty();
    }

    @Test
    @DisplayName("B2B: two sales to same GSTIN are grouped into one B2bPartyGroup")
    void b2bSales_groupedByCtin() {
        Sale s1 = buildSale("INV-001", customerWithGstin(CUST_GSTIN), POS_INTRA,
                            SaleStatus.COMPLETED, null, bd("10000"), List.of(item18(bd("10000"))));
        Sale s2 = buildSale("INV-002", customerWithGstin(CUST_GSTIN), POS_INTRA,
                            SaleStatus.COMPLETED, null, bd("8000"), List.of(item18(bd("8000"))));
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(s1, s2));

        Gstr1ExportDto dto = builder.build(2026, 9);

        assertThat(dto.getB2b()).hasSize(1);
        assertThat(dto.getB2b().get(0).getCtin()).isEqualTo(CUST_GSTIN);
        assertThat(dto.getB2b().get(0).getInv()).hasSize(2);
    }

    // ── B2CL tests ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B2CL: inter-state sale over ₹2.5L with no GSTIN appears in b2cl")
    void b2clSale_appearsInB2clTable() {
        Sale sale = buildSale("INV-100", customerWithoutGstin(), POS_INTER,
                              SaleStatus.COMPLETED, null, bd("300000"), List.of(item18(bd("300000"))));
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(sale));

        Gstr1ExportDto dto = builder.build(2026, 9);

        assertThat(dto.getB2cl()).hasSize(1);
        assertThat(dto.getB2cl().get(0).getPos()).isEqualTo("29");
        assertThat(dto.getB2cl().get(0).getInv()).hasSize(1);
        assertThat(dto.getB2cs()).isEmpty();
    }

    @Test
    @DisplayName("B2CL: two inter-state large sales with same POS grouped into one B2clPosGroup")
    void b2clSales_groupedByPos() {
        Sale s1 = buildSale("INV-101", customerWithoutGstin(), POS_INTER,
                            SaleStatus.COMPLETED, null, bd("300000"), List.of(item18(bd("300000"))));
        Sale s2 = buildSale("INV-102", customerWithoutGstin(), POS_INTER,
                            SaleStatus.COMPLETED, null, bd("400000"), List.of(item18(bd("400000"))));
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(s1, s2));

        Gstr1ExportDto dto = builder.build(2026, 9);

        assertThat(dto.getB2cl()).hasSize(1);
        assertThat(dto.getB2cl().get(0).getInv()).hasSize(2);
    }

    // ── B2CS tests ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B2CS: two intra-state sales at same POS+rate aggregate into one summary row")
    void b2csSales_aggregatedByPosRate() {
        Sale s1 = buildSale("INV-200", customerWithoutGstin(), POS_INTRA,
                            SaleStatus.COMPLETED, null, bd("5000"), List.of(item18(bd("5000"))));
        Sale s2 = buildSale("INV-201", customerWithoutGstin(), POS_INTRA,
                            SaleStatus.COMPLETED, null, bd("3000"), List.of(item18(bd("3000"))));
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(s1, s2));

        Gstr1ExportDto dto = builder.build(2026, 9);

        assertThat(dto.getB2cs()).hasSize(1);
        assertThat(dto.getB2cs().get(0).getTxval()).isEqualByComparingTo(bd("8000"));
    }

    @Test
    @DisplayName("B2CL threshold: grossTotal > 2.5L but reportingTotal (originalTotalAmount) <= 2.5L routes to B2CS")
    void b2clThreshold_grossOverButNetUnder_routesToB2cs() {
        // Items sum to ₹3L, but originalTotalAmount (post-discount snapshot) = ₹2L
        Sale sale = buildSale("INV-300", customerWithoutGstin(), POS_INTER,
                              SaleStatus.COMPLETED, null, bd("200000"),   // reportingTotal = ₹2L
                              List.of(item18(bd("300000"))));              // item taxable = ₹3L
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(sale));

        Gstr1ExportDto dto = builder.build(2026, 9);

        // ₹2L < ₹2.5L floor → must go to B2CS, not B2CL
        assertThat(dto.getB2cl()).isEmpty();
        assertThat(dto.getB2cs()).hasSize(1);
    }

    // ── EXP tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("EXP: EXPORT_WITHOUT_PAYMENT routes to exp[WOPAY]")
    void exportSale_wopay_appearsInExpTable() {
        Sale sale = buildSale("EXP-001", customerWithoutGstin(), POS_INTER,
                              SaleStatus.COMPLETED, SupplyType.EXPORT_WITHOUT_PAYMENT,
                              bd("50000"), List.of(item18(bd("50000"))));
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(sale));

        Gstr1ExportDto dto = builder.build(2026, 9);

        assertThat(dto.getExp()).hasSize(1);
        assertThat(dto.getExp().get(0).getExpTyp()).isEqualTo("WOPAY");
        assertThat(dto.getExp().get(0).getInv()).hasSize(1);
        assertThat(dto.getB2cs()).isEmpty();
        assertThat(dto.getB2cl()).isEmpty();
    }

    @Test
    @DisplayName("EXP: EXPORT_WITH_PAYMENT routes to exp[WPAY]")
    void exportSale_wpay_appearsInExpTable() {
        Sale sale = buildSale("EXP-002", customerWithoutGstin(), POS_INTER,
                              SaleStatus.COMPLETED, SupplyType.EXPORT_WITH_PAYMENT,
                              bd("60000"), List.of(item18(bd("60000"))));
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(sale));

        Gstr1ExportDto dto = builder.build(2026, 9);

        assertThat(dto.getExp()).hasSize(1);
        assertThat(dto.getExp().get(0).getExpTyp()).isEqualTo("WPAY");
    }

    // ── CDNR / CDNUR tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("CDNR: credit note for registered customer appears in cdnr table")
    void cdnrNote_appearsInCdnrTable() {
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(Collections.emptyList());

        CreditNote note = buildNote(NoteType.CDNR, CUST_GSTIN, "CN-001",
                                    List.of(noteItem18(bd("1000"))));
        when(creditRepo.findAllByShopIdAndCreditNoteDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(note));

        Gstr1ExportDto dto = builder.build(2026, 9);

        assertThat(dto.getCdnr()).hasSize(1);
        assertThat(dto.getCdnr().get(0).getCtin()).isEqualTo(CUST_GSTIN);
        assertThat(dto.getCdnr().get(0).getNt()).hasSize(1);
        assertThat(dto.getCdnur()).isEmpty();
    }

    @Test
    @DisplayName("CDNUR: credit note with CDNUR noteType appears in cdnur table")
    void cdnurNote_appearsInCdnurTable() {
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(Collections.emptyList());

        CreditNote note = buildNote(NoteType.CDNUR, CUST_GSTIN, "CN-002",
                                    List.of(noteItem18(bd("1500"))));
        when(creditRepo.findAllByShopIdAndCreditNoteDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(note));

        Gstr1ExportDto dto = builder.build(2026, 9);

        assertThat(dto.getCdnur()).hasSize(1);
        assertThat(dto.getCdnr()).isEmpty();
    }

    // ── HSN tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HSN: same HSN code at different rates produces two separate HsnEntry rows")
    void hsnTable_separateRowsPerRate() {
        SaleItem item5  = item(GSTType.GST_5,  "1234", bd("5000"));
        SaleItem item18 = item(GSTType.GST_18, "1234", bd("8000"));
        Sale sale = buildSale("INV-400", customerWithoutGstin(), POS_INTRA,
                              SaleStatus.COMPLETED, null, bd("13000"), List.of(item5, item18));
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(sale));

        Gstr1ExportDto dto = builder.build(2026, 9);

        List<Gstr1ExportDto.HsnEntry> data = dto.getHsn().getData();
        assertThat(data).hasSize(2);
        // Both rows carry the same HSN code but different rates
        assertThat(data).extracting(Gstr1ExportDto.HsnEntry::getHsnSc)
                        .containsOnly("1234");
        // Distinct taxable values confirm they were not merged
        assertThat(data).extracting(e -> e.getTxval().intValue())
                        .containsExactlyInAnyOrder(5000, 8000);
    }

    @Test
    @DisplayName("Cess: cessAmt from SaleItem propagates into TaxItemEntry itm_det.csamt")
    void cessAmount_propagatesToTaxItems() {
        SaleItem lineWithCess = item18WithCess(bd("10000"), bd("10"));
        Sale sale = buildSale("INV-500", customerWithoutGstin(), POS_INTRA,
                              SaleStatus.COMPLETED, null, bd("10000"), List.of(lineWithCess));
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(sale));

        Gstr1ExportDto dto = builder.build(2026, 9);

        assertThat(dto.getB2cs()).hasSize(1);
        assertThat(dto.getB2cs().get(0).getCsamt()).isEqualByComparingTo(bd("10"));
    }

    @Test
    @DisplayName("Cess: cessAmt from SaleItem propagates into HsnEntry csamt")
    void cessAmount_propagatesToHsnTable() {
        SaleItem lineWithCess = item18WithCess(bd("10000"), bd("10"));
        Sale sale = buildSale("INV-501", customerWithoutGstin(), POS_INTRA,
                              SaleStatus.COMPLETED, null, bd("10000"), List.of(lineWithCess));
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(sale));

        Gstr1ExportDto dto = builder.build(2026, 9);

        assertThat(dto.getHsn().getData()).hasSize(1);
        assertThat(dto.getHsn().getData().get(0).getCsamt()).isEqualByComparingTo(bd("10"));
    }

    @Test
    @DisplayName("HELD: sale with HELD status is excluded from all tables")
    void heldSale_excluded() {
        Sale held = buildSale("INV-600", customerWithoutGstin(), POS_INTRA,
                              SaleStatus.HELD, null, bd("5000"), List.of(item18(bd("5000"))));
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(held));

        Gstr1ExportDto dto = builder.build(2026, 9);

        assertThat(dto.getB2b()).isEmpty();
        assertThat(dto.getB2cl()).isEmpty();
        assertThat(dto.getB2cs()).isEmpty();
        assertThat(dto.getHsn().getData()).isEmpty();
    }

    @Test
    @DisplayName("JSON field names: serialized DTO uses GSTN snake_case (inum, itm_det, sply_ty)")
    void jsonFieldNames_gstSnakeCase() throws Exception {
        Sale sale = buildSale("INV-700", customerWithGstin(CUST_GSTIN), POS_INTRA,
                              SaleStatus.COMPLETED, null, bd("10000"), List.of(item18(bd("10000"))));
        Sale b2cSale = buildSale("INV-701", customerWithoutGstin(), POS_INTRA,
                                 SaleStatus.COMPLETED, null, bd("2000"), List.of(item18(bd("2000"))));
        when(saleRepo.findAllByShopIdAndDateBetween(eq(SHOP_ID), any(), any()))
                .thenReturn(List.of(sale, b2cSale));

        Gstr1ExportDto dto = builder.build(2026, 9);
        String json = new ObjectMapper().writeValueAsString(dto);

        // GSTN snake_case keys must be present
        assertThat(json).contains("\"inum\"");
        assertThat(json).contains("\"itm_det\"");
        assertThat(json).contains("\"sply_ty\"");
        assertThat(json).contains("\"gstin\"");
        assertThat(json).contains("\"fp\"");

        // Java camelCase keys must NOT appear
        assertThat(json).doesNotContain("\"invoiceNo\"");
        assertThat(json).doesNotContain("\"taxDetails\"");
        assertThat(json).doesNotContain("\"supplyType\"");
        assertThat(json).doesNotContain("\"invoiceDate\"");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Sale buildSale(String invoiceNo, Customer customer, String placeOfSupply,
                           SaleStatus status, SupplyType supplyType,
                           BigDecimal reportingTotal, List<SaleItem> items) {
        Sale sale = new Sale();
        sale.setInvoiceNo(invoiceNo);
        sale.setCustomer(customer);
        sale.setPlaceOfSupply(placeOfSupply);
        sale.setStatus(status);
        sale.setSupplyType(supplyType);
        sale.setDate(LocalDateTime.of(2026, 9, 10, 12, 0));
        sale.setOriginalTotalAmount(reportingTotal);
        sale.getSaleItems().addAll(items);
        return sale;
    }

    private Customer customerWithGstin(String gstin) {
        Customer c = new Customer();
        c.setName("Test Customer");
        c.setGstNumber(gstin);
        return c;
    }

    private Customer customerWithoutGstin() {
        Customer c = new Customer();
        c.setName("Walk-in Customer");
        return c;
    }

    private SaleItem item18(BigDecimal taxableValue) {
        return item(GSTType.GST_18, "", taxableValue);
    }

    private SaleItem item18WithCess(BigDecimal taxableValue, BigDecimal cessAmt) {
        SaleItem i = item(GSTType.GST_18, "", taxableValue);
        i.setCessAmt(cessAmt);
        return i;
    }

    private SaleItem item(GSTType gstType, String hsn, BigDecimal taxableValue) {
        SaleItem i = new SaleItem();
        i.setGstType(gstType);
        i.setTaxableValue(taxableValue);
        i.setQty(BigDecimal.ONE);
        i.setCustomHsnSac(hsn);
        // Default lineType is GOODS, getGstnUqc() returns uqc (null → "OTH" in HsnKey)
        return i;
    }

    private CreditNote buildNote(NoteType noteType, String customerGstin,
                                 String noteNo, List<CreditNoteItem> items) {
        Customer customer = customerWithGstin(customerGstin);
        CreditNote note = new CreditNote();
        note.setNoteType(noteType);
        note.setCustomer(customer);
        note.setCreditNoteDate(LocalDate.of(2026, 9, 10));
        note.setCreditNoteNo(noteNo);
        note.setTotalAmount(items.stream()
                .map(CreditNoteItem::getTaxableValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        note.setReferenceInvoiceNumber("INV-ORIG-001");
        items.forEach(item -> {
            item.setCreditNote(note);
            note.getItems().add(item);
        });
        return note;
    }

    private CreditNoteItem noteItem18(BigDecimal taxableValue) {
        CreditNoteItem item = new CreditNoteItem();
        item.setGstType(GSTType.GST_18);
        item.setTaxableValue(taxableValue);
        return item;
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
