package com.desitech.vyaparsathi.payment.mapper;

import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.enums.PaymentMethod;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMapperTest {
    private final PaymentMapper paymentMapper = new PaymentMapper();

    @Test
    void toDto_NullInput_ReturnsNull() {
        assertNull(paymentMapper.toDto(null));
    }

    @Test
    void toDto_ValidPayment_ReturnsDto() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setSourceId(2L);
        payment.setSourceType(PaymentSourceType.SALE);
        payment.setSupplierId(3L);
        payment.setCustomerId(4L);
        payment.setAmount(BigDecimal.TEN);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setPaymentMethod(PaymentMethod.CASH);
        payment.setReference("REF123");
        payment.setNotes("Test");
        payment.setStatus(null);

        PaymentDto dto = paymentMapper.toDto(payment);
        assertNotNull(dto);
        assertEquals(payment.getId(), dto.getId());
        assertEquals(payment.getSourceId(), dto.getSourceId());
        assertEquals(payment.getSourceType(), dto.getSourceType());
        assertEquals(payment.getSupplierId(), dto.getSupplierId());
        assertEquals(payment.getCustomerId(), dto.getCustomerId());
        assertEquals(payment.getAmount(), dto.getAmount());
        assertEquals(payment.getPaymentDate(), dto.getPaymentDate());
        assertEquals(payment.getPaymentMethod(), dto.getPaymentMethod());
        assertEquals(payment.getReference(), dto.getReference());
        assertEquals(payment.getNotes(), dto.getNotes());
        assertEquals(payment.getStatus(), dto.getStatus());
    }

    @Test
    void toEntity_NullInput_ReturnsNull() {
        assertNull(paymentMapper.toEntity(null));
    }

    @Test
    void toEntity_ValidDto_ReturnsEntity() {
        PaymentDto dto = new PaymentDto();
        dto.setId(1L);
        dto.setSourceId(2L);
        dto.setSourceType(PaymentSourceType.SALE);
        dto.setSupplierId(3L);
        dto.setCustomerId(4L);
        dto.setAmount(BigDecimal.TEN);
        dto.setPaymentDate(LocalDateTime.now());
        dto.setPaymentMethod(PaymentMethod.CASH);
        dto.setReference("REF123");
        dto.setNotes("Test");
        dto.setStatus(null);

        Payment payment = paymentMapper.toEntity(dto);
        assertNotNull(payment);
        assertEquals(dto.getId(), payment.getId());
        assertEquals(dto.getSourceId(), payment.getSourceId());
        assertEquals(dto.getSourceType(), payment.getSourceType());
        assertEquals(dto.getSupplierId(), payment.getSupplierId());
        assertEquals(dto.getCustomerId(), payment.getCustomerId());
        assertEquals(dto.getAmount(), payment.getAmount());
        assertEquals(dto.getPaymentDate(), payment.getPaymentDate());
        assertEquals(dto.getPaymentMethod(), payment.getPaymentMethod());
        assertEquals(dto.getReference(), payment.getReference());
        assertEquals(dto.getNotes(), payment.getNotes());
        assertEquals(dto.getStatus(), payment.getStatus());
    }
}
