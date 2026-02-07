package com.desitech.vyaparsathi.payment.controller;

import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.payment.dto.*;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

        private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

        @Autowired
        private PaymentService paymentService;

        @PostMapping
        @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
        public ResponseEntity<PaymentDto> createPayment(@RequestBody PaymentDto paymentDto) {
                PaymentDto saved = paymentService.createPayment(paymentDto);
                return ResponseEntity.ok(saved);
        }

        @GetMapping
        public ResponseEntity<Page<PaymentDto>> getPayments(
                @RequestParam(required = false) PaymentSourceType sourceType,
                @RequestParam(required = false) Long sourceId,
                @RequestParam(required = false) Long supplierId,
                @RequestParam(required = false) Long customerId,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "20") int size) {

                PageRequest pageable = PageRequest.of(page, size, Sort.by("paymentDate").descending());

                if (sourceType != null && sourceId != null) {
                        return ResponseEntity.ok(paymentService.getPaymentsBySource(sourceType, sourceId, pageable));
                } else if (supplierId != null) {
                        return ResponseEntity.ok(paymentService.getPaymentsBySupplier(supplierId, pageable));
                } else if (customerId != null) {
                        return ResponseEntity.ok(paymentService.getPaymentsByCustomer(customerId, pageable));
                } else {
                        return ResponseEntity.badRequest().build();
                }
        }

        @GetMapping("/{id}")
        public ResponseEntity<PaymentDto> getPayment(@PathVariable Long id) {
                return paymentService.getPayment(id)
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
        }

        @PostMapping("/record")
        @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
        public ResponseEntity<ApiResponse<PaymentResponse>> recordPayment(
                @Valid @RequestBody PaymentReceivedRequest request) {
                try {
                        PaymentDto paymentDto = paymentService.recordDuePayment(request);
                        PaymentResponse response = new PaymentResponse(
                                paymentDto.getId(),
                                paymentDto.getAmount(),
                                paymentDto.getPaymentMethod(),
                                paymentDto.getStatus(),
                                paymentDto.getPaymentDate(),
                                paymentDto.getTransactionId(),
                                paymentDto.getReference(),
                                paymentDto.getNotes()
                        );
                        logger.info("Recorded payment for sourceId={}, amount={}", request.getSourceId(), request.getAmount());
                        return ResponseEntity.ok(
                                new ApiResponse<>(
                                        "Payment recorded successfully",
                                        response,
                                        paymentDto.getTransactionId()
                                )
                        );
                } catch (Exception e) {
                        logger.error("Error recording payment for sourceId={}, amount={}: {}", request.getSourceId(), request.getAmount(), e.getMessage(), e);
                        throw new ApplicationException("Failed to record payment", e);
                }
        }

        @PostMapping("/record-batch")
        @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
        public ResponseEntity<ApiResponse<List<PaymentResponse>>> recordPaymentsBatch(
                @Valid @RequestBody List<PaymentReceivedRequest> requests) {
                List<PaymentResponse> responses = new ArrayList<>();
                try {
                        for (PaymentReceivedRequest request : requests) {
                                PaymentDto paymentDto = paymentService.recordDuePayment(request);
                                PaymentResponse response = new PaymentResponse(
                                        paymentDto.getId(),
                                        paymentDto.getAmount(),
                                        paymentDto.getPaymentMethod(),
                                        paymentDto.getStatus(),
                                        paymentDto.getPaymentDate(),
                                        paymentDto.getTransactionId(),
                                        paymentDto.getReference(),
                                        paymentDto.getNotes()
                                );
                                responses.add(response);
                        }
                        logger.info("Batch payment recorded: count={}", requests.size());
                        return ResponseEntity.ok(new ApiResponse<>("Payments recorded successfully", responses, null));
                } catch (Exception e) {
                        logger.error("Error recording batch payments: {}", e.getMessage(), e);
                        throw new ApplicationException("Failed to record batch payments", e);
                }
        }

        @PostMapping("/bulk")
        @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
        public ResponseEntity<ApiResponse<Void>> recordBulkPayment(
                @Valid @RequestBody BulkPaymentRequest request) {
                try {
                        paymentService.bulkPayment(request);
                        logger.info("Bulk payment processed for customerId={}, amount={}",
                                request.getCustomerId(), request.getTotalAmount());

                        return ResponseEntity.ok(
                                new ApiResponse<>("Bulk payment processed and allocated successfully", null, null)
                        );
                } catch (Exception e) {
                        logger.error("Error processing bulk payment for customerId={}: {}",
                                request.getCustomerId(), e.getMessage(), e);
                        throw new ApplicationException("Failed to process bulk payment", e);
                }
        }

        @GetMapping("/customer/{customerId}/advance-balance")
        public ResponseEntity<ApiResponse<BigDecimal>> getCustomerAdvanceBalance(@PathVariable Long customerId) {
                try {
                        BigDecimal balance = paymentService.getCustomerAdvanceBalance(customerId);
                        return ResponseEntity.ok(
                                new ApiResponse<>("Customer advance balance retrieved", balance, null)
                        );
                } catch (Exception e) {
                        logger.error("Error fetching advance balance for customerId={}: {}", customerId, e.getMessage());
                        throw new ApplicationException("Failed to fetch customer balance", e);
                }
        }
}