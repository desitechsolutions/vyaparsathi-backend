package com.desitech.vyaparsathi.receipt.controller;

import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.repository.PaymentRepository;
import com.desitech.vyaparsathi.receipt.dto.PaymentReceiptDto;
import com.desitech.vyaparsathi.receipt.dto.ReceiptTokenData;
import com.desitech.vyaparsathi.receipt.entity.PaymentReceipt;
import com.desitech.vyaparsathi.receipt.repository.PaymentReceiptRepository;
import com.desitech.vyaparsathi.receipt.service.PaymentReceiptService;
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
 * REST endpoints for payment receipts.
 *
 * <p>Two paths mirror the invoice pattern:
 *   <ul>
 *     <li>{@code GET /api/payments/{id}/receipt-signed-url} — issues a signed JWT
 *         and returns the URL the client should GET to download the PDF.</li>
 *     <li>{@code GET /api/receipts/signed?token=…&download=…} — validates the
 *         token and streams the receipt PDF. Public-with-token; no session auth
 *         needed once the JWT is issued.</li>
 *   </ul>
 */
@RestController
public class PaymentReceiptController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentReceiptController.class);

    private final PaymentReceiptService receiptService;
    private final PaymentReceiptRepository receiptRepository;
    private final PaymentRepository paymentRepository;
    private final JwtUtil jwtUtil;

    public PaymentReceiptController(PaymentReceiptService receiptService,
                                    PaymentReceiptRepository receiptRepository,
                                    PaymentRepository paymentRepository,
                                    JwtUtil jwtUtil) {
        this.receiptService = receiptService;
        this.receiptRepository = receiptRepository;
        this.paymentRepository = paymentRepository;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Issues a signed URL the client can use to download the receipt PDF.
     * If no receipt has been issued for this payment yet (e.g. legacy payments
     * from before V58 or internal allocations from a bulk payment), one is
     * created on the fly.
     */
    @GetMapping("/api/payments/{paymentId}/receipt-signed-url")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
    public ResponseEntity<String> getSignedUrlForPayment(@PathVariable Long paymentId) {
        PaymentReceipt receipt = receiptRepository.findByPaymentId(paymentId)
                .orElseGet(() -> {
                    Payment payment = paymentRepository.findById(paymentId)
                            .orElseThrow(() -> new EntityNotFoundAppException("Payment", paymentId));
                    return receiptService.createFromPayment(payment);
                });
        String token = jwtUtil.generateReceiptToken(receipt.getId(), receipt.getReceiptNumber());
        return ResponseEntity.ok("/api/receipts/signed?token=" + token);
    }

    /** Streams the receipt PDF; {@code download=true} forces an attachment. */
    @GetMapping("/api/receipts/signed")
    public ResponseEntity<?> getSignedReceipt(@RequestParam String token,
                                              @RequestParam(defaultValue = "false") boolean download) {
        try {
            ReceiptTokenData data = jwtUtil.validateReceiptToken(token);
            byte[] pdf = receiptService.generatePdfByReceiptId(data.getReceiptId());

            String filename = "receipt_" + (data.getReceiptNumber() != null
                    ? data.getReceiptNumber().replace('/', '_')
                    : data.getReceiptId()) + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentLength(pdf.length);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            String disposition = download ? "attachment" : "inline";
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Invalid or expired signed receipt token", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired access");
        }
    }

    /** Paginated list of receipts for a customer. */
    @GetMapping("/api/payment-receipts")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
    public ResponseEntity<Page<PaymentReceiptDto>> listByCustomer(
            @RequestParam Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PaymentReceiptDto> result = receiptRepository.findByCustomerId(customerId, pageable)
                .map(receiptService::toDto);
        return ResponseEntity.ok(result);
    }
}
