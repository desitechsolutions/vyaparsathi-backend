package com.desitech.vyaparsathi.payroll.controller;

import com.desitech.vyaparsathi.payroll.dto.*;
import com.desitech.vyaparsathi.payroll.service.PayrollService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    @Autowired
    private PayrollService payrollService;

    // --- STAFF ENDPOINTS ---

    @PostMapping("/staff")
    public ResponseEntity<StaffDto> addStaff(@Valid @RequestBody StaffDto dto) {
        return new ResponseEntity<>(payrollService.addStaff(dto), HttpStatus.CREATED);
    }

    @GetMapping("/staff")
    public ResponseEntity<Page<StaffResponseDto>> listStaff(
            @RequestParam String month,
            @RequestParam Integer year,
            Pageable pageable) {
        return ResponseEntity.ok(payrollService.listStaff(month, year, pageable));
    }

    @GetMapping("/staff/{id}")
    public ResponseEntity<StaffDto> getStaff(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.getStaff(id));
    }

    @PutMapping("/staff/{id}")
    public ResponseEntity<StaffDto> updateStaff(@PathVariable Long id, @Valid @RequestBody StaffDto dto) {
        return ResponseEntity.ok(payrollService.updateStaff(id, dto));
    }

    @DeleteMapping("/staff/{id}")
    public ResponseEntity<Void> deleteStaff(@PathVariable Long id) {
        payrollService.deleteStaff(id);
        return ResponseEntity.noContent().build();
    }

    // --- ADVANCE ENDPOINTS ---

    @PostMapping("/staff/{id}/advance")
    public ResponseEntity<Void> issueAdvance(
            @PathVariable Long id,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String remarks) {
        payrollService.issueAdvance(id, amount, remarks);
        return ResponseEntity.ok().build();
    }

    // --- SALARY PROCESSING ENDPOINTS ---

    /**
     * Process single salary payment.
     */
    @PostMapping("/process")
    public ResponseEntity<PayrollResponseDto> processSalary(@Valid @RequestBody PayrollRequestDto dto) {
        return ResponseEntity.ok(payrollService.processSalary(dto));
    }

    /**
     * Planned for Bulk Selection: Process multiple salaries at once.
     */
    @PostMapping("/process/bulk")
    public ResponseEntity<List<PayrollResponseDto>> processBulkSalary(@Valid @RequestBody List<PayrollRequestDto> dtos) {
        // You can implement this in your service using a loop over processSalary
        List<PayrollResponseDto> responses = dtos.stream()
                .map(payrollService::processSalary)
                .toList();
        return ResponseEntity.ok(responses);
    }

    // --- HISTORY ENDPOINTS ---

    @GetMapping("/history/staff/{staffId}")
    public ResponseEntity<Page<PayrollResponseDto>> getStaffPaymentHistory(
            @PathVariable Long staffId,
            Pageable pageable) {
        return ResponseEntity.ok(payrollService.listPayments(staffId, pageable));
    }
}