package com.desitech.vyaparsathi.supplier.service;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.purchasereturn.entity.PurchaseReturn;
import com.desitech.vyaparsathi.purchasereturn.enums.PurchaseReturnStatus;
import com.desitech.vyaparsathi.purchasereturn.repository.PurchaseReturnRepository;
import com.desitech.vyaparsathi.supplier.dto.SupplierStatementDto;
import com.desitech.vyaparsathi.supplier.entity.Supplier;
import com.desitech.vyaparsathi.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SupplierPaymentServiceTest {

    @InjectMocks
    private SupplierPaymentService supplierPaymentService;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PurchaseReturnRepository purchaseReturnRepository;

    private Supplier supplier;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentShopId(1L);

        supplier = new Supplier();
        supplier.setId(5L);
        supplier.setName("Pharma Wholesaler");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should generate dynamic supplier statement with running balance")
    void getSupplierStatement_Success() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(10);
        LocalDateTime end = now.plusDays(1);

        when(supplierRepository.findById(5L)).thenReturn(Optional.of(supplier));

        // Prior (Opening)
        when(purchaseOrderRepository.findBySupplierIdAndShopIdAndOrderDateBefore(eq(5L), eq(1L), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findBySupplierIdAndShopIdAndPaymentDateBefore(eq(5L), eq(1L), any())).thenReturn(Collections.emptyList());
        when(purchaseReturnRepository.findBySupplierIdAndStatusAndShopIdAndReturnDateBefore(eq(5L), eq(PurchaseReturnStatus.APPROVED), eq(1L), any())).thenReturn(Collections.emptyList());

        // Period PO
        PurchaseOrder po = new PurchaseOrder();
        po.setId(101L);
        po.setPoNumber("PO-1001");
        po.setOrderDate(now.minusDays(5));
        po.setTotalAmount(new BigDecimal("10000.00"));

        when(purchaseOrderRepository.findBySupplierIdAndShopIdAndOrderDateBetween(eq(5L), eq(1L), any(), any()))
                .thenReturn(List.of(po));

        // Period Payment
        Payment payment = new Payment();
        payment.setId(201L);
        payment.setPaymentDate(now.minusDays(3));
        payment.setAmount(new BigDecimal("4000.00"));
        payment.setPaymentMethod(PaymentMethod.UPI);
        payment.setReference("UPI-8812");

        when(paymentRepository.findBySupplierIdAndShopIdAndPaymentDateBetween(eq(5L), eq(1L), any(), any()))
                .thenReturn(List.of(payment));

        // Period Return
        PurchaseReturn pr = new PurchaseReturn();
        pr.setId(301L);
        pr.setReturnNo("PR-991");
        pr.setReturnDate(now.minusDays(1));
        pr.setTotalAmount(new BigDecimal("1000.00"));
        pr.setStatus(PurchaseReturnStatus.APPROVED);

        when(purchaseReturnRepository.findBySupplierIdAndStatusAndShopIdAndReturnDateBetween(eq(5L), eq(PurchaseReturnStatus.APPROVED), eq(1L), any(), any()))
                .thenReturn(List.of(pr));

        SupplierStatementDto statement = supplierPaymentService.getSupplierStatement(5L, start, end);

        assertThat(statement).isNotNull();
        assertThat(statement.getOpeningBalance()).isEqualByComparingTo("0.00");
        assertThat(statement.getTotalBilled()).isEqualByComparingTo("10000.00");
        assertThat(statement.getTotalPaid()).isEqualByComparingTo("4000.00");
        assertThat(statement.getTotalReturned()).isEqualByComparingTo("1000.00");
        // Net liability = 10000 (PO) - 4000 (Paid) - 1000 (Returned) = 5000
        assertThat(statement.getClosingBalance()).isEqualByComparingTo("5000.00");
        assertThat(statement.getStatementEntries()).hasSize(3);
    }
}
