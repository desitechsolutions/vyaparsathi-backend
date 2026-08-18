package com.desitech.vyaparsathi.customer.controller;

import com.desitech.vyaparsathi.customer.dto.CustomerSegmentDto;
import com.desitech.vyaparsathi.customer.service.CustomerSegmentService;
import com.desitech.vyaparsathi.rbac.annotation.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Two REST surfaces:
 * <ul>
 *   <li>{@code /api/customer-segments} — CRUD on the shop-wide segment catalogue.</li>
 *   <li>{@code /api/customers/{id}/segments} — per-customer attach / detach / replace.</li>
 * </ul>
 */
@RestController
public class CustomerSegmentController {

    private final CustomerSegmentService service;

    public CustomerSegmentController(CustomerSegmentService service) {
        this.service = service;
    }

    // ─── Segment catalogue (shop-wide) ───────────────────────────────

    @GetMapping("/api/customer-segments")
    @RequirePermission("CUSTOMER_VIEW")
    public List<CustomerSegmentDto> listAll() {
        return service.listAll();
    }

    @PostMapping("/api/customer-segments")
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerSegmentDto create(@Valid @RequestBody CustomerSegmentDto dto) {
        return service.create(dto);
    }

    @PutMapping("/api/customer-segments/{segmentId}")
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerSegmentDto update(@PathVariable Long segmentId, @Valid @RequestBody CustomerSegmentDto dto) {
        return service.update(segmentId, dto);
    }

    @DeleteMapping("/api/customer-segments/{segmentId}")
    @RequirePermission("CUSTOMER_DELETE")
    public void delete(@PathVariable Long segmentId) {
        service.delete(segmentId);
    }

    // ─── Membership (per customer) ───────────────────────────────────

    @GetMapping("/api/customers/{customerId}/segments")
    @RequirePermission("CUSTOMER_VIEW")
    public List<CustomerSegmentDto> segmentsForCustomer(@PathVariable Long customerId) {
        return service.segmentsForCustomer(customerId);
    }

    @PostMapping("/api/customers/{customerId}/segments/{segmentId}")
    @RequirePermission("CUSTOMER_EDIT")
    public void attach(@PathVariable Long customerId, @PathVariable Long segmentId) {
        service.attach(customerId, segmentId);
    }

    @DeleteMapping("/api/customers/{customerId}/segments/{segmentId}")
    @RequirePermission("CUSTOMER_EDIT")
    public void detach(@PathVariable Long customerId, @PathVariable Long segmentId) {
        service.detach(customerId, segmentId);
    }

    /**
     * Replaces the customer's entire segment set in one call — used by
     * the customer-detail Segments picker where the user checks/unchecks
     * segments and hits Save.
     */
    @PutMapping("/api/customers/{customerId}/segments")
    @RequirePermission("CUSTOMER_EDIT")
    public List<CustomerSegmentDto> replace(@PathVariable Long customerId,
                                            @RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body != null ? body.get("segmentIds") : List.of();
        return service.replaceMembership(customerId, ids);
    }
}
