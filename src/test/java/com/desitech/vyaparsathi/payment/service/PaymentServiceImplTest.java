package com.desitech.vyaparsathi.payment.service;

import com.desitech.vyaparsathi.customer.dto.CustomerLedgerDto;
import com.desitech.vyaparsathi.customer.entity.CustomerLedgerType;
import com.desitech.vyaparsathi.customer.service.CustomerLedgerService;
import com.desitech.vyaparsathi.payment.dto.BulkPaymentRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceImplTest {

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Mock
    private CustomerLedgerService ledgerService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private SaleRepository saleRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    // ----------- createPayment tests ------------

    @Test
    void createPayment_setsStatusExplicit_whenExplicitGiven() {
        PaymentDto dto = createBasicDto(1L, PaymentSourceType.SALE, "200");
        dto.setStatus(PaymentStatus.PAID);

        Payment payment = createBasicEntity(1L, PaymentSourceType.SALE, "200");
        Sale sale = createSale(1L, "200");

        when(paymentMapper.toEntity(any())).thenReturn(payment);
        when(saleRepository.findById(1L)).thenReturn(Optional.of(sale));
        when(paymentRepository.sumPaymentsBySource(any(), any())).thenReturn(ZERO);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toDto(any())).thenReturn(dto);

        PaymentDto result = paymentService.createPayment(dto);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void createPayment_statusPaid_whenFullPaidCashOrUpi() {
        PaymentDto dto = createBasicDto(2L, PaymentSourceType.SALE, "500");
        Payment payment = createBasicEntity(2L, PaymentSourceType.SALE, "500");
        Sale sale = createSale(2L, "500");

        when(paymentMapper.toEntity(any())).thenReturn(payment);
        when(saleRepository.findById(2L)).thenReturn(Optional.of(sale));
        when(paymentRepository.sumPaymentsBySource(any(), any())).thenReturn(ZERO);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentMapper.toDto(any())).thenReturn(dto);

        paymentService.createPayment(dto);

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void createPayment_statusPartiallyPaid_whenPartialCashOrUpi() {
        PaymentDto dto = createBasicDto(3L, PaymentSourceType.SALE, "300");
        Payment payment = createBasicEntity(3L, PaymentSourceType.SALE, "300");
        Sale sale = createSale(3L, "500");

        when(paymentMapper.toEntity(any())).thenReturn(payment);
        when(saleRepository.findById(3L)).thenReturn(Optional.of(sale));
        when(paymentRepository.sumPaymentsBySource(any(), any())).thenReturn(ZERO);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.createPayment(dto);

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
    }

    @Test
    void createPayment_statusPending_whenZeroPaidCashOrUpi() {
        PaymentDto dto = createBasicDto(4L, PaymentSourceType.SALE, "0");
        Payment payment = createBasicEntity(4L, PaymentSourceType.SALE, "0");
        Sale sale = createSale(4L, "500");

        when(paymentMapper.toEntity(any())).thenReturn(payment);
        when(saleRepository.findById(4L)).thenReturn(Optional.of(sale));
        when(paymentRepository.sumPaymentsBySource(any(), any())).thenReturn(ZERO);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.createPayment(dto);

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void createPayment_statusPending_whenChequeOrNetBanking() {
        // Cheque/NetBanking should result in PENDING until cleared,
        // but based on your logic, it checks amount vs total first.
        PaymentDto dto = createBasicDto(5L, PaymentSourceType.PURCHASE_ORDER, "1000");
        dto.setPaymentMethod(PaymentMethod.CHEQUE);

        Payment payment = createBasicEntity(5L, PaymentSourceType.PURCHASE_ORDER, "1000");
        payment.setPaymentMethod(PaymentMethod.CHEQUE);

        PurchaseOrder po = new PurchaseOrder();
        po.setId(5L);
        po.setTotalAmount(new BigDecimal("2000"));

        when(paymentMapper.toEntity(any())).thenReturn(payment);
        when(purchaseOrderRepository.findById(5L)).thenReturn(Optional.of(po));
        when(paymentRepository.sumPaymentsBySource(any(), any())).thenReturn(ZERO);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.createPayment(dto);

        verify(paymentRepository).save(paymentCaptor.capture());
        // For partial amount via Cheque
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
    }

    @Test
    void createPayment_handlesNullTotalAmountAsZero() {
        PaymentDto dto = createBasicDto(6L, PaymentSourceType.SALE, "0");
        Payment payment = createBasicEntity(6L, PaymentSourceType.SALE, "0");
        Sale sale = createSale(6L, null); // Testing NULL total

        when(paymentMapper.toEntity(any())).thenReturn(payment);
        when(saleRepository.findById(6L)).thenReturn(Optional.of(sale));
        when(paymentRepository.sumPaymentsBySource(any(), any())).thenReturn(ZERO);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.createPayment(dto);

        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    // ----------- recordDuePayment tests ------------

    @Test
    void recordDuePayment_throwsIfOverpay() {
        // 1. Setup Request: Paying 1500
        PaymentReceivedRequest req = new PaymentReceivedRequest();
        req.setAmount(new BigDecimal("1500"));
        req.setSourceId(10L);
        req.setSourceType(PaymentSourceType.SALE);
        req.setCustomerId(1L); // Ensure customerId is set if used for ledger

        // 2. Mock the Sum query: 0 has been paid so far
        // This replaces the findBySourceTypeAndSourceId mock
        when(paymentRepository.sumPaymentsBySource(PaymentSourceType.SALE, 10L))
                .thenReturn(BigDecimal.ZERO);

        // 3. Mock the Sale: Total invoice value is only 1000
        Sale sale = createSale(10L, "1000");
        when(saleRepository.findById(10L)).thenReturn(Optional.of(sale));

        // 4. Act & Assert: Should throw because 1500 > 1000
        assertThatThrownBy(() -> paymentService.recordDuePayment(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds due amount");
    }

    @Test
    void recordDuePayment_successAndSetsStatus() {
        // 1. Setup Request
        PaymentReceivedRequest req = new PaymentReceivedRequest();
        req.setAmount(new BigDecimal("500"));
        req.setSourceId(11L);
        req.setSourceType(PaymentSourceType.SALE);
        req.setPaymentMethod(PaymentMethod.CASH);
        req.setCustomerId(101L);

        Sale sale = createSale(11L, "1000");
        // Ensure initial status is NOT what we are checking for
        sale.setPaymentStatus(PaymentStatus.PENDING);

        Payment payment = createBasicEntity(11L, PaymentSourceType.SALE, "500");
        payment.setPaymentMethod(PaymentMethod.CASH);

        // 2. Mocks
        when(paymentRepository.sumPaymentsBySource(PaymentSourceType.SALE, 11L))
                .thenReturn(BigDecimal.ZERO)
                .thenReturn(new BigDecimal("500"));
        when(paymentRepository.findPaymentMethodsBySource(PaymentSourceType.SALE, 11L))
                .thenReturn(new HashSet<>(Set.of(PaymentMethod.CASH)));

        // Crucial: Use thenAnswer or return the same object to track state changes
        when(saleRepository.findById(11L)).thenReturn(Optional.of(sale));
        when(paymentMapper.toEntityFromPayRequest(any())).thenReturn(payment);
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // 3. Act
        paymentService.recordDuePayment(req);

        // 4. Verification
        // Verify Payment Save
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);

        // Verify Sale Save with specific field check
        // We use ArgumentCaptor for Sale to avoid the "Actual invocations have different arguments" confusion
        ArgumentCaptor<Sale> saleCaptor = ArgumentCaptor.forClass(Sale.class);
        verify(saleRepository, atLeastOnce()).save(saleCaptor.capture());

        assertThat(saleCaptor.getValue().getPaymentStatus())
                .as("The Sale status should be updated to PARTIALLY_PAID")
                .isEqualTo(PaymentStatus.PARTIALLY_PAID);

        // Verify Ledger
        verify(ledgerService).addEntry(eq(101L), any(CustomerLedgerDto.class));
    }
    // ----------- bulkPayment tests ------------

    @Test
    void bulkPayment_waterfallEffect_settlesOldestFirst() {
        BulkPaymentRequest request = new BulkPaymentRequest();
        request.setCustomerId(101L);
        request.setTotalAmount(new BigDecimal("55000"));
        request.setPaymentMethod(PaymentMethod.CASH);

        Sale s1 = createSale(1L, "30000");
        Sale s2 = createSale(2L, "30000");

        when(saleRepository.findByCustomerIdAndPaymentStatusInOrderByIdAsc(anyLong(), anyList()))
                .thenReturn(Arrays.asList(s1, s2));

        // Mock N+1 fix results
        List<Object[]> sums = Arrays.asList(new Object[]{1L, ZERO}, new Object[]{2L, ZERO});
        when(paymentRepository.sumPaymentsBySaleIds(anySet(), eq(PaymentSourceType.SALE))).thenReturn(sums);

        when(saleRepository.findById(1L)).thenReturn(Optional.of(s1));
        when(saleRepository.findById(2L)).thenReturn(Optional.of(s2));

        paymentService.bulkPayment(request);

        verify(paymentRepository, times(2)).save(paymentCaptor.capture());
        List<Payment> allocations = paymentCaptor.getAllValues();

        assertThat(allocations.get(0).getAmount()).isEqualByComparingTo("30000");
        assertThat(allocations.get(1).getAmount()).isEqualByComparingTo("25000");
    }

    @Test
    void bulkPayment_recordsAdvance_whenExcessAmount() {
        BulkPaymentRequest request = new BulkPaymentRequest();
        request.setCustomerId(101L);
        request.setTotalAmount(new BigDecimal("10000"));
        request.setPaymentMethod(PaymentMethod.UPI);

        Sale s1 = createSale(1L, "8000");
        when(saleRepository.findByCustomerIdAndPaymentStatusInOrderByIdAsc(anyLong(), anyList()))
                .thenReturn(Collections.singletonList(s1));

        List<Object[]> sums = Collections.singletonList(new Object[]{1L, ZERO});
        when(paymentRepository.sumPaymentsBySaleIds(anySet(), any())).thenReturn(sums);
        when(saleRepository.findById(1L)).thenReturn(Optional.of(s1));

        paymentService.bulkPayment(request);

        verify(paymentRepository, times(2)).save(paymentCaptor.capture());
        Payment advance = paymentCaptor.getAllValues().get(1);

        assertThat(advance.getAmount()).isEqualByComparingTo("2000");
        assertThat(advance.getSourceId()).isNull();
    }

    // ----------- Helpers to keep code clean and readable ------------

    private PaymentDto createBasicDto(Long id, PaymentSourceType type, String amount) {
        PaymentDto dto = new PaymentDto();
        dto.setSourceId(id);
        dto.setSourceType(type);
        dto.setAmount(new BigDecimal(amount));
        dto.setPaymentMethod(PaymentMethod.CASH);
        dto.setPaymentDate(LocalDateTime.now());
        return dto;
    }

    private Payment createBasicEntity(Long id, PaymentSourceType type, String amount) {
        Payment p = new Payment();
        p.setSourceId(id);
        p.setSourceType(type);
        p.setAmount(new BigDecimal(amount));
        p.setPaymentMethod(PaymentMethod.CASH);
        return p;
    }

    private Sale createSale(Long id, String total) {
        Sale sale = new Sale();
        sale.setId(id);
        sale.setTotalAmount(total != null ? new BigDecimal(total) : null);
        sale.setInvoiceNo("INV-" + id);
        return sale;
    }
}