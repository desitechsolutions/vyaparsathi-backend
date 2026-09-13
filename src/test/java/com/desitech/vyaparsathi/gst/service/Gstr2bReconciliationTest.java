package com.desitech.vyaparsathi.gst.service;

import com.desitech.vyaparsathi.gst.entity.Gstr2bEntry;
import com.desitech.vyaparsathi.gst.entity.Gstr2bImport;
import com.desitech.vyaparsathi.gst.repository.Gstr2bEntryRepository;
import com.desitech.vyaparsathi.gst.repository.Gstr2bImportRepository;
import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoice;
import com.desitech.vyaparsathi.purchases.repository.PurchaseInvoiceRepository;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Gstr2bReconciliationTest {

    @Mock private PurchaseInvoiceRepository purchaseRepo;
    @Mock private Gstr2bImportRepository importRepo;
    @Mock private Gstr2bEntryRepository entryRepo;

    private Gstr2bReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new Gstr2bReconciler(new ObjectMapper(), purchaseRepo, importRepo, entryRepo);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    @Test
    void normalizeInvoiceNo_stripsLeadingZerosAndSeparators() {
        assertThat(Gstr2bReconciler.normaliseInvoiceNo("INV/2026/001"))
                .isEqualTo(Gstr2bReconciler.normaliseInvoiceNo("INV-2026-1"))
                .isEqualTo("INV20261");
    }

    @Test
    void levenshtein_emptyStrings_returnsZero() {
        assertThat(Gstr2bReconciler.levenshtein("", "")).isZero();
    }

    @Test
    void levenshtein_oneSubstitution() {
        assertThat(Gstr2bReconciler.levenshtein("INVO001", "INV0001")).isEqualTo(1);
    }

    // ── Reconciliation cases ──────────────────────────────────────────────────

    private static final String GSTIN = "27ABCDE1234F1Z5";

    private PurchaseInvoice fakePurchase(String invoiceNo, double igst, double cgst, double sgst) {
        Supplier s = new Supplier();
        s.setGstin(GSTIN);
        PurchaseInvoice pi = new PurchaseInvoice();
        pi.setSupplier(s);
        pi.setSupplierInvoiceNo(invoiceNo);
        pi.setTotalIgst(BigDecimal.valueOf(igst));
        pi.setTotalCgst(BigDecimal.valueOf(cgst));
        pi.setTotalSgst(BigDecimal.valueOf(sgst));
        pi.setPurchaseDate(LocalDate.of(2026, 9, 15));
        return pi;
    }

    private MockMultipartFile gstr2bJson(String supplierGstin, String invoiceNo,
                                          double igst, double cgst, double sgst) throws Exception {
        String json = "{ \"data\": { \"docDetails\": { \"b2b\": [{ \"ctin\": \"" + supplierGstin + "\","
                + " \"trdnm\": \"Test Supplier\","
                + " \"inv\": [{ \"inum\": \"" + invoiceNo + "\", \"dt\": \"15-Sep-2026\", \"val\": 11800,"
                + " \"itms\": [{ \"num\": 1, \"itm_det\": { \"rt\": 18, \"txval\": 10000,"
                + " \"igst\": " + igst + ", \"cgst\": " + cgst + ", \"sgst\": " + sgst + " }}] }] }] } } }";
        return new MockMultipartFile("file", "gstr2b.json", "application/json", json.getBytes());
    }

    private void stubSave() {
        when(importRepo.save(any(Gstr2bImport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(entryRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void exactMatch_sameGstinSameInvoiceNoSameTax() throws Exception {
        stubSave();
        when(purchaseRepo.findAllByShopIdAndPurchaseDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of(fakePurchase("INV/2026/001", 1800, 0, 0)));

        MockMultipartFile file = gstr2bJson(GSTIN, "INV/2026/001", 1800, 0, 0);
        var summary = reconciler.reconcile(file, 1L, 2026, 9);

        assertThat(summary.getMatchedCount()).isEqualTo(1);
        assertThat(summary.getMismatchedCount()).isZero();
    }

    @Test
    void probableMatch_invoiceNoLevenshtein1_classified() throws Exception {
        stubSave();
        // Books has "INVO001", portal sends "INV0001" — edit distance = 1
        when(purchaseRepo.findAllByShopIdAndPurchaseDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of(fakePurchase("INVO001", 1800, 0, 0)));

        MockMultipartFile file = gstr2bJson(GSTIN, "INV0001", 1800, 0, 0);
        var summary = reconciler.reconcile(file, 1L, 2026, 9);

        assertThat(summary.getMatchedCount()).isEqualTo(1);
    }

    @Test
    void mismatch_sameGstinSameInvoiceNo_taxDiffOver1Rupee() throws Exception {
        stubSave();
        when(purchaseRepo.findAllByShopIdAndPurchaseDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of(fakePurchase("INV001", 1800, 0, 0)));

        // Portal has 1900 igst — diff is 100 > 1 rupee
        MockMultipartFile file = gstr2bJson(GSTIN, "INV001", 1900, 0, 0);
        var summary = reconciler.reconcile(file, 1L, 2026, 9);

        assertThat(summary.getMismatchedCount()).isEqualTo(1);
    }

    @Test
    void missingInBooks_gstinNotInPurchases() throws Exception {
        stubSave();
        // Books has a different supplier
        PurchaseInvoice other = fakePurchase("INV999", 900, 0, 0);
        other.getSupplier().setGstin("29ZZZZZ9999Z1ZZ");
        when(purchaseRepo.findAllByShopIdAndPurchaseDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of(other));

        MockMultipartFile file = gstr2bJson(GSTIN, "INV001", 1800, 0, 0);
        var summary = reconciler.reconcile(file, 1L, 2026, 9);

        assertThat(summary.getMissingInBooksCount()).isEqualTo(1);
    }

    @Test
    void missingInPortal_itcEligiblePurchaseNotInJson() throws Exception {
        stubSave();
        PurchaseInvoice pi = fakePurchase("INV555", 900, 0, 0);
        pi.setIsItcEligible(true);
        when(purchaseRepo.findAllByShopIdAndPurchaseDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of(pi));

        // Portal JSON has no entries for this supplier
        MockMultipartFile file = new MockMultipartFile("file", "gstr2b.json", "application/json",
                "{ \"data\": { \"docDetails\": { \"b2b\": [] } } }".getBytes());
        var summary = reconciler.reconcile(file, 1L, 2026, 9);

        assertThat(summary.getMissingInPortalCount()).isEqualTo(1);
    }
}
