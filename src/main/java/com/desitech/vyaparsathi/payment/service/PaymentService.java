package com.desitech.vyaparsathi.payment.service;

import com.desitech.vyaparsathi.payment.dto.BulkPaymentRequest;
import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.dto.PaymentReceivedRequest;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.refund.dto.RefundDto;
import com.desitech.vyaparsathi.refund.dto.RefundRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface PaymentService {
    PaymentDto createPayment(PaymentDto dto);
    Page<PaymentDto> getPaymentsBySource(PaymentSourceType sourceType, Long sourceId, Pageable pageable);
    Page<PaymentDto> getPaymentsBySupplier(Long supplierId, Pageable pageable);
    Page<PaymentDto> getPaymentsByCustomer(Long customerId, Pageable pageable);
    Optional<PaymentDto> getPayment(Long id);

    BigDecimal calculateDueAmount(Long sourceId, PaymentSourceType sourceType, BigDecimal totalAmount);
    PaymentDto recordDuePayment(PaymentReceivedRequest paymentReceivedRequest);

    Map<Long, BigDecimal> getTotalPaidBySaleIds(Set<Long> saleIds);

    void bulkPayment(BulkPaymentRequest request);
    BigDecimal getCustomerAdvanceBalance(Long customerId);
    BigDecimal applyAdvanceToSale(Long customerId, Long saleId, BigDecimal saleTotal);

    /**
     * Refund a portion (or all) of an existing Payment. Creates a Refund
     * document, posts a reversal ledger entry, and returns the persisted DTO.
     * Throws {@code BusinessValidationException} on over-refund attempts.
     */
    RefundDto refundPayment(Long originalPaymentId, RefundRequest request);
}
