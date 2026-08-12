package com.desitech.vyaparsathi.salesorder.controller;

import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.common.exception.EntityNotFoundAppException;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.salesorder.dto.ConvertToSaleRequest;
import com.desitech.vyaparsathi.salesorder.dto.SalesOrderDto;
import com.desitech.vyaparsathi.salesorder.dto.SalesOrderTokenData;
import com.desitech.vyaparsathi.salesorder.entity.SalesOrder;
import com.desitech.vyaparsathi.salesorder.enums.SalesOrderStatus;
import com.desitech.vyaparsathi.salesorder.repository.SalesOrderRepository;
import com.desitech.vyaparsathi.salesorder.service.SalesOrderPdfService;
import com.desitech.vyaparsathi.salesorder.service.SalesOrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales-orders")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
public class SalesOrderController {

    private static final Logger logger = LoggerFactory.getLogger(SalesOrderController.class);

    private final SalesOrderService orderService;
    private final SalesOrderPdfService pdfService;
    private final SalesOrderRepository orderRepo;
    private final JwtUtil jwtUtil;

    public SalesOrderController(SalesOrderService orderService,
                                SalesOrderPdfService pdfService,
                                SalesOrderRepository orderRepo,
                                JwtUtil jwtUtil) {
        this.orderService = orderService;
        this.pdfService = pdfService;
        this.orderRepo = orderRepo;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SalesOrderDto>> create(@Valid @RequestBody SalesOrderDto dto) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Sales Order created", orderService.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SalesOrderDto>> update(@PathVariable Long id,
                                                              @Valid @RequestBody SalesOrderDto dto) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Sales Order updated", orderService.update(id, dto)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SalesOrderDto>> get(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Sales Order fetched", orderService.get(id)));
    }

    @GetMapping
    public ResponseEntity<Page<SalesOrderDto>> list(@PageableDefault(size = 20) Pageable pageable,
                                                    @RequestParam(required = false) SalesOrderStatus status,
                                                    @RequestParam(required = false) Long customerId) {
        return ResponseEntity.ok(orderService.list(pageable, status, customerId));
    }

    // ── State transitions ─────────────────────────────────────────────

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<SalesOrderDto>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Sales Order approved", orderService.approve(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SalesOrderDto>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Sales Order cancelled", orderService.cancel(id)));
    }

    @PostMapping("/{id}/convert-to-sale")
    public ResponseEntity<ApiResponse<SalesOrderDto>> convert(@PathVariable Long id,
                                                              @RequestBody(required = false) ConvertToSaleRequest req) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Sales Order converted", orderService.convertToSale(id, req)));
    }

    @PostMapping("/from-quotation/{quotationId}")
    public ResponseEntity<ApiResponse<SalesOrderDto>> createFromQuotation(@PathVariable Long quotationId) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Sales Order created from quotation",
                orderService.createFromQuotation(quotationId)));
    }

    // ── Signed URL PDF download ──────────────────────────────────────

    @GetMapping("/{id}/signed-url")
    public ResponseEntity<String> getSignedUrl(@PathVariable Long id) {
        SalesOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundAppException("Sales Order", id));
        String token = jwtUtil.generateSalesOrderToken(so.getId(), so.getOrderNo());
        return ResponseEntity.ok("/api/sales-orders/signed?token=" + token);
    }

    @GetMapping("/signed")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getSignedPdf(@RequestParam String token,
                                          @RequestParam(defaultValue = "false") boolean download) {
        try {
            SalesOrderTokenData data = jwtUtil.validateSalesOrderToken(token);
            byte[] pdf = pdfService.generatePdf(data.getSalesOrderId());
            String filename = "sales_order_" + (data.getOrderNo() != null
                    ? data.getOrderNo().replace('/', '_') : data.getSalesOrderId()) + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentLength(pdf.length);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
            String disposition = download ? "attachment" : "inline";
            headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Invalid or expired sales order token", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired access");
        }
    }
}
