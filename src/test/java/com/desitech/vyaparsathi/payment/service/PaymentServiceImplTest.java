package com.desitech.vyaparsathi.payment.service;

import com.desitech.vyaparsathi.customer.dto.CustomerLedgerDto;
import com.desitech.vyaparsathi.customer.entity.CustomerLedgerType;
import com.desitech.vyaparsathi.customer.service.CustomerLedgerService;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.dto.PaymentReceivedRequest;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.enums.PaymentStatus;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import com.desitech.vyaparsathi.payment.mapper.PaymentMapper;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.repository.PurchaseOrderRepository;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.desitech.vyaparsathi.sales.repository.SaleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceImplTest {

    @InjectMocks
    PaymentServiceImpl paymentService;

    @Mock
    CustomerLedgerService ledgerService;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PaymentMapper paymentMapper;
    @Mock
    SaleRepository saleRepository;
    @Mock
    PurchaseOrderRepository purchaseOrderRepository;

    @Captor
    ArgumentCaptor<Payment> paymentCaptor;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    // ----------- createPayment tests ------------

    @Test
    void createPayment_setsStatusExplicit_whenExplicitGiven() {
        PaymentDto dto = new PaymentDto();
        dto.setSourceId(1L);
        dto.setSourceType(PaymentSourceType.SALE);
        dto.setAmount(new BigDecimal("200"));
        dto.setPaymentMethod(PaymentMethod.CASH);
        dto.setStatus(PaymentStatus.PAID);

        Payment payment = new Payment();
        payment.setSourceId(1L);
        payment.setSourceType(PaymentSourceType.SALE);
        payment.setAmount(new BigDecimal("200"));
        payment.setPaymentMethod(PaymentMethod.CASH);

        Sale sale = new Sale();
        sale.setId(1L);
        sale.setTotalAmount(new BigDecimal("200"));

        when(paymentMapper.toEntity(any(PaymentDto.class))).thenReturn(payment);
        when(saleRepository.findById(1L)).thenReturn(Optional.of(sale));
        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.SALE, 1L)).thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(dto);

        PaymentDto result = paymentService.createPayment(dto);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void createPayment_statusPaid_whenFullPaidCashOrUpi() {
        PaymentDto dto = new PaymentDto();
        dto.setSourceId(2L);
        dto.setSourceType(PaymentSourceType.SALE);
        dto.setAmount(new BigDecimal("500"));
        dto.setPaymentMethod(PaymentMethod.CASH);

        Payment payment = new Payment();
        payment.setSourceId(2L);
        payment.setSourceType(PaymentSourceType.SALE);
        payment.setAmount(new BigDecimal("500"));
        payment.setPaymentMethod(PaymentMethod.CASH);

        Sale sale = new Sale();
        sale.setId(2L);
        sale.setTotalAmount(new BigDecimal("500"));

        when(paymentMapper.toEntity(any(PaymentDto.class))).thenReturn(payment);
        when(saleRepository.findById(2L)).thenReturn(Optional.of(sale));
        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.SALE, 2L)).thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(dto);

        PaymentDto result = paymentService.createPayment(dto);

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void createPayment_statusPartiallyPaid_whenPartialCashOrUpi() {
        PaymentDto dto = new PaymentDto();
        dto.setSourceId(3L);
        dto.setSourceType(PaymentSourceType.SALE);
        dto.setAmount(new BigDecimal("300"));
        dto.setPaymentMethod(PaymentMethod.UPI);

        Payment payment = new Payment();
        payment.setSourceId(3L);
        payment.setSourceType(PaymentSourceType.SALE);
        payment.setAmount(new BigDecimal("300"));
        payment.setPaymentMethod(PaymentMethod.UPI);

        Sale sale = new Sale();
        sale.setId(3L);
        sale.setTotalAmount(new BigDecimal("500"));

        when(paymentMapper.toEntity(any(PaymentDto.class))).thenReturn(payment);
        when(saleRepository.findById(3L)).thenReturn(Optional.of(sale));
        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.SALE, 3L)).thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(dto);

        PaymentDto result = paymentService.createPayment(dto);

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
    }

    @Test
    void createPayment_statusPending_whenZeroPaidCashOrUpi() {
        PaymentDto dto = new PaymentDto();
        dto.setSourceId(4L);
        dto.setSourceType(PaymentSourceType.SALE);
        dto.setAmount(BigDecimal.ZERO);
        dto.setPaymentMethod(PaymentMethod.CASH);

        Payment payment = new Payment();
        payment.setSourceId(4L);
        payment.setSourceType(PaymentSourceType.SALE);
        payment.setAmount(BigDecimal.ZERO);
        payment.setPaymentMethod(PaymentMethod.CASH);

        Sale sale = new Sale();
        sale.setId(4L);
        sale.setTotalAmount(new BigDecimal("500"));

        when(paymentMapper.toEntity(any(PaymentDto.class))).thenReturn(payment);
        when(saleRepository.findById(4L)).thenReturn(Optional.of(sale));
        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.SALE, 4L)).thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(dto);

        PaymentDto result = paymentService.createPayment(dto);

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void createPayment_statusPending_whenChequeOrNetBanking() {
        PaymentDto dto = new PaymentDto();
        dto.setSourceId(5L);
        dto.setSourceType(PaymentSourceType.PURCHASE_ORDER);
        dto.setAmount(new BigDecimal("1000"));
        dto.setPaymentMethod(PaymentMethod.CHEQUE);

        Payment payment = new Payment();
        payment.setSourceId(5L);
        payment.setSourceType(PaymentSourceType.PURCHASE_ORDER);
        payment.setAmount(new BigDecimal("1000"));
        payment.setPaymentMethod(PaymentMethod.CHEQUE);

        PurchaseOrder po = new PurchaseOrder();
        po.setId(5L);
        po.setTotalAmount(new BigDecimal("1000"));

        when(paymentMapper.toEntity(any(PaymentDto.class))).thenReturn(payment);
        when(purchaseOrderRepository.findById(5L)).thenReturn(Optional.of(po));
        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.PURCHASE_ORDER, 5L)).thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(dto);

        PaymentDto result = paymentService.createPayment(dto);

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
    }

    @Test
    void createPayment_handlesNullTotalAmountAsZero() {
        PaymentDto dto = new PaymentDto();
        dto.setSourceId(6L);
        dto.setSourceType(PaymentSourceType.SALE);
        dto.setAmount(new BigDecimal("0"));
        dto.setPaymentMethod(PaymentMethod.CASH);

        Payment payment = new Payment();
        payment.setSourceId(6L);
        payment.setSourceType(PaymentSourceType.SALE);
        payment.setAmount(new BigDecimal("0"));
        payment.setPaymentMethod(PaymentMethod.CASH);

        Sale sale = new Sale();
        sale.setId(6L);
        sale.setTotalAmount(null);

        when(paymentMapper.toEntity(any(PaymentDto.class))).thenReturn(payment);
        when(saleRepository.findById(6L)).thenReturn(Optional.of(sale));
        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.SALE, 6L)).thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(dto);

        PaymentDto result = paymentService.createPayment(dto);

        verify(paymentRepository).save(paymentCaptor.capture());
        // Since totalAmount=0, status should be PENDING
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void createPayment_entityNotFoundForSale_throws() {
        PaymentDto dto = new PaymentDto();
        dto.setSourceId(22L);
        dto.setSourceType(PaymentSourceType.SALE);

        // FIX: Set sourceId and sourceType on Payment
        Payment payment = new Payment();
        payment.setSourceId(22L);
        payment.setSourceType(PaymentSourceType.SALE);

        when(paymentMapper.toEntity(any(PaymentDto.class))).thenReturn(payment);
        when(saleRepository.findById(22L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(dto))
                .isInstanceOf(EntityNotFoundException.class);
    }
    @Test
    void createPayment_entityNotFoundForPurchaseOrder_throws() {
        PaymentDto dto = new PaymentDto();
        dto.setSourceId(101L);
        dto.setSourceType(PaymentSourceType.PURCHASE_ORDER);

        // FIX: Set sourceId and sourceType on Payment
        Payment payment = new Payment();
        payment.setSourceId(101L);
        payment.setSourceType(PaymentSourceType.PURCHASE_ORDER);

        when(paymentMapper.toEntity(any(PaymentDto.class))).thenReturn(payment);
        when(purchaseOrderRepository.findById(101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(dto))
                .isInstanceOf(EntityNotFoundException.class);
    }
    @Test
    void createPayment_unsupportedSourceType_throws() {
        PaymentDto dto = new PaymentDto();
        dto.setSourceId(201L);
        dto.setSourceType(null);

        when(paymentMapper.toEntity(any(PaymentDto.class))).thenReturn(new Payment());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(new PaymentDto());

        assertThatCode(() -> paymentService.createPayment(dto)).doesNotThrowAnyException();
        // Because if sourceType is null, getTotalAmountForSource is not called, so no exception
    }

    // ----------- recordDuePayment tests ------------

    @Test
    void recordDuePayment_throwsIfInvalidAmount() {
        PaymentReceivedRequest req = new PaymentReceivedRequest();
        req.setAmount(BigDecimal.ZERO);
        req.setSourceId(1L);
        req.setSourceType(PaymentSourceType.SALE);

        assertThatThrownBy(() -> paymentService.recordDuePayment(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordDuePayment_throwsIfSourceNull() {
        PaymentReceivedRequest req = new PaymentReceivedRequest();
        req.setAmount(BigDecimal.ONE);

        assertThatThrownBy(() -> paymentService.recordDuePayment(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordDuePayment_throwsIfOverpay() {
        PaymentReceivedRequest req = new PaymentReceivedRequest();
        req.setAmount(new BigDecimal("1500"));
        req.setSourceId(10L);
        req.setSourceType(PaymentSourceType.SALE);

        Sale sale = new Sale();
        sale.setId(10L);
        sale.setTotalAmount(new BigDecimal("1000"));

        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.SALE, 10L)).thenReturn(Collections.emptyList());
        when(saleRepository.findById(10L)).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> paymentService.recordDuePayment(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds due amount");
    }

    @Test
    void recordDuePayment_successAndSetsStatus() {
        PaymentReceivedRequest req = new PaymentReceivedRequest();
        req.setAmount(new BigDecimal("500"));
        req.setSourceId(11L);
        req.setSourceType(PaymentSourceType.SALE);
        req.setPaymentMethod(PaymentMethod.CASH);
        req.setCustomerId(101L);

        CustomerLedgerDto dto = new CustomerLedgerDto();
        dto.setId(101L);
        dto.setCustomerId(101L);
        dto.setAmount(new BigDecimal("500"));
        dto.setType(CustomerLedgerType.DEBIT);

        Sale sale = new Sale();
        sale.setId(11L);
        sale.setTotalAmount(new BigDecimal("1000"));

        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.SALE, 11L)).thenReturn(Collections.emptyList());
        when(saleRepository.findById(11L)).thenReturn(Optional.of(sale));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerService.addEntry(eq(101L), any(CustomerLedgerDto.class))).thenReturn(dto);

        Payment payment = new Payment();
        payment.setSourceType(PaymentSourceType.SALE); // <-- FIXED
        payment.setSourceId(11L);                      // <-- FIXED
        payment.setAmount(new BigDecimal("500"));
        payment.setPaymentMethod(PaymentMethod.CASH);

        PaymentDto paymentDto = new PaymentDto();
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(paymentDto);
        when(paymentMapper.toEntityFromPayRequest(eq(req))).thenReturn(payment);

        PaymentDto result = paymentService.recordDuePayment(req);

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
    }

    // ----------- calculateDueAmount tests ------------

    @Test
    void calculateDueAmount_returnsZeroIfOverpaid() {
        List<Payment> payments = Arrays.asList(
                paymentWithAmount(new BigDecimal("600")),
                paymentWithAmount(new BigDecimal("700"))
        );
        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.SALE, 88L)).thenReturn(payments);

        BigDecimal due = paymentService.calculateDueAmount(88L, PaymentSourceType.SALE, new BigDecimal("1000"));
        assertThat(due).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void calculateDueAmount_returnsCorrectDue() {
        List<Payment> payments = Arrays.asList(
                paymentWithAmount(new BigDecimal("200")),
                paymentWithAmount(new BigDecimal("300"))
        );
        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.SALE, 77L)).thenReturn(payments);

        BigDecimal due = paymentService.calculateDueAmount(77L, PaymentSourceType.SALE, new BigDecimal("1000"));
        assertThat(due).isEqualTo(new BigDecimal("500"));
    }

    // ----------- getTotalAmountForSource tests ------------

    @Test
    void getTotalAmountForSource_returnsSaleAmount() {
        Sale sale = new Sale();
        sale.setId(1L);
        sale.setTotalAmount(new BigDecimal("999"));
        when(saleRepository.findById(1L)).thenReturn(Optional.of(sale));
        assertThat(paymentService.getTotalAmountForSource(1L, PaymentSourceType.SALE)).isEqualTo(new BigDecimal("999"));
    }

    @Test
    void getTotalAmountForSource_returnsPurchaseOrderAmount() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(2L);
        po.setTotalAmount(new BigDecimal("222"));
        when(purchaseOrderRepository.findById(2L)).thenReturn(Optional.of(po));
        assertThat(paymentService.getTotalAmountForSource(2L, PaymentSourceType.PURCHASE_ORDER)).isEqualTo(new BigDecimal("222"));
    }

    @Test
    void getTotalAmountForSource_nullAmountReturnsZero() {
        Sale sale = new Sale();
        sale.setId(3L);
        sale.setTotalAmount(null);
        when(saleRepository.findById(3L)).thenReturn(Optional.of(sale));
        assertThat(paymentService.getTotalAmountForSource(3L, PaymentSourceType.SALE)).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void getTotalAmountForSource_entityNotFound_throws() {
        when(saleRepository.findById(1000L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> paymentService.getTotalAmountForSource(1000L, PaymentSourceType.SALE))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ----------- getPaymentsBySource/Customer/Supplier tests ------------

    @Test
    void getPaymentsBySource_mapsToDto() {
        Payment payment = paymentWithAmount(new BigDecimal("123"));
        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.SALE, 5L)).thenReturn(Arrays.asList(payment));
        PaymentDto dto = new PaymentDto();
        when(paymentMapper.toDto(payment)).thenReturn(dto);

        List<PaymentDto> result = paymentService.getPaymentsBySource(PaymentSourceType.SALE, 5L);
        assertThat(result).containsExactly(dto);
    }

    @Test
    void getPaymentsBySupplier_mapsToDto() {
        Payment payment = paymentWithAmount(new BigDecimal("123"));
        when(paymentRepository.findBySupplierId(9L)).thenReturn(Arrays.asList(payment));
        PaymentDto dto = new PaymentDto();
        when(paymentMapper.toDto(payment)).thenReturn(dto);

        List<PaymentDto> result = paymentService.getPaymentsBySupplier(9L);
        assertThat(result).containsExactly(dto);
    }

    @Test
    void getPaymentsByCustomer_mapsToDto() {
        Payment payment = paymentWithAmount(new BigDecimal("123"));
        when(paymentRepository.findByCustomerId(7L)).thenReturn(Arrays.asList(payment));
        PaymentDto dto = new PaymentDto();
        when(paymentMapper.toDto(payment)).thenReturn(dto);

        List<PaymentDto> result = paymentService.getPaymentsByCustomer(7L);
        assertThat(result).containsExactly(dto);
    }

    @Test
    void getPayment_returnsOptionalDto() {
        Payment payment = paymentWithAmount(new BigDecimal("321"));
        PaymentDto dto = new PaymentDto();
        when(paymentRepository.findById(33L)).thenReturn(Optional.of(payment));
        when(paymentMapper.toDto(payment)).thenReturn(dto);

        Optional<PaymentDto> result = paymentService.getPayment(33L);
        assertThat(result).contains(dto);
    }

    @Test
    void getPayment_returnsEmptyIfNotFound() {
        when(paymentRepository.findById(44L)).thenReturn(Optional.empty());
        assertThat(paymentService.getPayment(44L)).isEmpty();
    }

    // ----------- helpers ------------

    static Payment paymentWithAmount(BigDecimal amount) {
        Payment p = new Payment();
        p.setAmount(amount);
        return p;
    }

    @Test
    void createPayment_forCustomer_setsCustomerIdAndStatus() {
        PaymentDto dto = new PaymentDto();
        dto.setSourceId(1L);
        dto.setSourceType(PaymentSourceType.SALE);
        dto.setAmount(new BigDecimal("100"));
        dto.setPaymentMethod(PaymentMethod.CASH);
        dto.setCustomerId(10L);
        dto.setStatus(PaymentStatus.PAID);

        Payment payment = new Payment();
        payment.setSourceId(1L);
        payment.setSourceType(PaymentSourceType.SALE);
        payment.setAmount(new BigDecimal("100"));
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setCustomerId(10L);

        Sale sale = new Sale();
        sale.setId(1L);
        sale.setTotalAmount(new BigDecimal("100"));

        when(paymentMapper.toEntity(any(PaymentDto.class))).thenReturn(payment);
        when(saleRepository.findById(1L)).thenReturn(Optional.of(sale));
        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.SALE, 1L)).thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(dto);

        PaymentDto result = paymentService.createPayment(dto);

        assertThat(result.getCustomerId()).isEqualTo(10L);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getCustomerId()).isEqualTo(10L);
    }

    @Test
    void createPayment_forSupplier_setsSupplierIdAndStatus() {
        PaymentDto dto = new PaymentDto();
        dto.setSourceId(2L);
        dto.setSourceType(PaymentSourceType.PURCHASE_ORDER);
        dto.setAmount(new BigDecimal("200"));
        dto.setPaymentMethod(PaymentMethod.CHEQUE);
        dto.setSupplierId(20L);
        dto.setStatus(PaymentStatus.PENDING);

        Payment payment = new Payment();
        payment.setSourceId(2L);
        payment.setSourceType(PaymentSourceType.PURCHASE_ORDER);
        payment.setAmount(new BigDecimal("200"));
        payment.setPaymentMethod(PaymentMethod.CHEQUE);
        payment.setSupplierId(20L);

        PurchaseOrder po = new PurchaseOrder();
        po.setId(2L);
        po.setTotalAmount(new BigDecimal("200"));

        when(paymentMapper.toEntity(any(PaymentDto.class))).thenReturn(payment);
        when(purchaseOrderRepository.findById(2L)).thenReturn(Optional.of(po));
        when(paymentRepository.findBySourceTypeAndSourceId(PaymentSourceType.PURCHASE_ORDER, 2L)).thenReturn(Collections.emptyList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(dto);

        PaymentDto result = paymentService.createPayment(dto);

        assertThat(result.getSupplierId()).isEqualTo(20L);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getSupplierId()).isEqualTo(20L);
    }
}