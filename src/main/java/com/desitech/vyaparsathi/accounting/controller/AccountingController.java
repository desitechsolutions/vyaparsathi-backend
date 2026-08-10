package com.desitech.vyaparsathi.accounting.controller;

import com.desitech.vyaparsathi.accounting.dto.*;
import com.desitech.vyaparsathi.accounting.service.AccountingService;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounting")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class AccountingController {

    private final AccountingService accountingService;

    public AccountingController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @GetMapping("/receivables-aging")
    public ResponseEntity<ApiResponse<List<ReceivablesAgingDto>>> getReceivablesAging() {
        List<ReceivablesAgingDto> list = accountingService.getReceivablesAging();
        return ResponseEntity.ok(new ApiResponse<>("success", "Receivables aging fetched", list));
    }

    @GetMapping("/payables-aging")
    public ResponseEntity<ApiResponse<List<PayablesAgingDto>>> getPayablesAging() {
        List<PayablesAgingDto> list = accountingService.getPayablesAging();
        return ResponseEntity.ok(new ApiResponse<>("success", "Payables aging fetched", list));
    }

    @GetMapping("/pnl")
    public ResponseEntity<ApiResponse<ProfitAndLossDto>> getProfitAndLoss(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        ProfitAndLossDto pnl = accountingService.getProfitAndLoss(startDate, endDate);
        return ResponseEntity.ok(new ApiResponse<>("success", "Profit and loss report generated", pnl));
    }
}
