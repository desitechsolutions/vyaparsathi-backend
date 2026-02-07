package com.desitech.vyaparsathi.payment.mapper;

import com.desitech.vyaparsathi.payment.dto.PaymentDto;
import com.desitech.vyaparsathi.payment.dto.PaymentReceivedRequest;
import com.desitech.vyaparsathi.payment.entity.Payment;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PaymentMapper {
    private static final Logger logger = LoggerFactory.getLogger(PaymentMapper.class);
    public PaymentDto toDto(Payment payment) {
        if (payment == null) return null;
        PaymentDto dto = new PaymentDto();
        dto.setId(payment.getId());
        dto.setSourceId(payment.getSourceId());
        dto.setSourceType(payment.getSourceType());
        dto.setSupplierId(payment.getSupplierId());
        dto.setCustomerId(payment.getCustomerId());
        dto.setAmount(payment.getAmount());
        dto.setPaymentDate(payment.getPaymentDate());
        dto.setPaymentMethod(payment.getPaymentMethod());
        dto.setReference(payment.getReference());
        dto.setNotes(payment.getNotes());
        dto.setStatus(payment.getStatus());
        return dto;
    }

    public Payment toEntity(PaymentDto dto) {
        if (dto == null) return null;
        Payment payment = new Payment();
        payment.setId(dto.getId());
        payment.setSourceId(dto.getSourceId());
        payment.setTransactionId(dto.getTransactionId() == null || dto.getTransactionId().trim().isEmpty() ? null : dto.getTransactionId());
        payment.setSourceType(dto.getSourceType());
        payment.setSupplierId(dto.getSupplierId());
        payment.setCustomerId(dto.getCustomerId());
        payment.setAmount(dto.getAmount());
        payment.setPaymentDate(dto.getPaymentDate());
        payment.setPaymentMethod(dto.getPaymentMethod());
        payment.setReference(dto.getReference());
        payment.setNotes(dto.getNotes());
        payment.setStatus(dto.getStatus());
        return payment;
    }

    public Payment toEntityFromPayRequest(PaymentReceivedRequest request){
        if (request == null) return null;
        Payment payment = new Payment();
        payment.setSourceId(request.getSourceId());
        payment.setSourceType(request.getSourceType());
        payment.setAmount(request.getAmount());
        payment.setTransactionId(request.getTransactionId() == null || request.getTransactionId().trim().isEmpty() ? null : request.getTransactionId());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPaymentDate(java.time.LocalDateTime.now());
        payment.setCustomerId(request.getCustomerId());
        payment.setSupplierId(request.getSupplierId());
        payment.setReference(request.getReference());
        payment.setNotes(request.getNotes());
        return payment;
    }
}
