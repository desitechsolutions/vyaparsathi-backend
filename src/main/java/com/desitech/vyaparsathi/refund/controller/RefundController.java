package com.desitech.vyaparsathi.refund.controller;

import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.payment.service.PaymentService;
import com.desitech.vyaparsathi.refund.dto.RefundDto;
import com.desitech.vyaparsathi.refund.dto.RefundRequest;
import com.desitech.vyaparsathi.refund.dto.RefundTokenData;
import com.desitech.vyaparsathi.refund.entity.Refund;
import com.desitech.vyaparsathi.refund.repository.RefundRepository;
import com.desitech.vyaparsathi.refund.service.RefundService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for refunds. The primary write path is
 * {@code POST /api/payments/{id}/refund} (creates a refund against a specific
 * Payment). Read/download endpoints live under {@code /api/refunds}.
 */
@RestController
public class RefundController {

    private static final Logger logger = LoggerFactory.getLogger(RefundController.class);

    private final PaymentService paymentService;
    private final RefundService refundService;
    private final RefundRepository refundRepository;
    private final JwtUtil jwtUtil;

    public RefundController(PaymentService paymentService,
                            RefundService refundService,
                            RefundRepository refundRepository,
                            JwtUtil jwtUtil) {
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.refundRepository = refundRepository;
        this.jwtUtil = jwtUtil;
    }

    /** Create a refund against an existing Payment. */
    @PostMapping("/api/payments/{paymentId}/refund")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<RefundDto>> refundPayment(@PathVariable Long paymentId,
                                                                @Valid @RequestBody RefundRequest request) {
        RefundDto dto = paymentService.refundPayment(paymentId, request);
        return ResponseEntity.ok(new ApiResponse<>("success", "Refund issued", dto));
    }

    /** List refunds for a customer. */
    @GetMapping("/api/refunds")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
    public ResponseEntity<Page<RefundDto>> listByCustomer(@RequestParam(required = false) Long customerId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<RefundDto> result;
        if (customerId != null) {
            result = refundRepository.findAllByCustomerId(customerId, pageable).map(refundService::toDto);
        } else {
            result = refundRepository.findAll(pageable).map(refundService::toDto);
        }
        return ResponseEntity.ok(result);
    }

    /** All refunds issued against a specific Payment. */
    @GetMapping("/api/payments/{paymentId}/refunds")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
    public ResponseEntity<java.util.List<RefundDto>> listByPayment(@PathVariable Long paymentId) {
        java.util.List<RefundDto> refunds = refundRepository.findByOriginalPaymentIdOrderByIdDesc(paymentId)
                .stream().map(refundService::toDto).toList();
        return ResponseEntity.ok(refunds);
    }

    /** Issues a signed URL a client can use to download the refund receipt PDF. */
    @GetMapping("/api/refunds/{id}/signed-url")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
    public ResponseEntity<String> getSignedUrl(@PathVariable Long id) {
        Refund refund = refundRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Refund", id));
        String token = jwtUtil.generateRefundToken(refund.getId(), refund.getRefundNo());
        return ResponseEntity.ok("/api/refunds/signed?token=" + token);
    }

    /** Streams the refund receipt PDF; validates the signed token. */
    @GetMapping("/api/refunds/signed")
    public ResponseEntity<?> getSignedPdf(@RequestParam String token,
                                          @RequestParam(defaultValue = "false") boolean download) {
        try {
            RefundTokenData data = jwtUtil.validateRefundToken(token);
            byte[] pdf = refundService.generatePdfByRefundId(data.getRefundId());
            String filename = "refund_" + (data.getRefundNo() != null
                    ? data.getRefundNo().replace('/', '_') : data.getRefundId()) + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentLength(pdf.length);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            String disposition = download ? "attachment" : "inline";
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Invalid or expired refund token", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired access");
        }
    }
}
