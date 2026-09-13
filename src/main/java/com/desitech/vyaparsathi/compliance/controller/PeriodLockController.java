package com.desitech.vyaparsathi.compliance.controller;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.compliance.dto.PeriodLockDto;
import com.desitech.vyaparsathi.compliance.dto.PeriodLockRequestDto;
import com.desitech.vyaparsathi.compliance.service.PeriodLockService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/compliance/period-locks")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class PeriodLockController {

    private final PeriodLockService periodLockService;

    public PeriodLockController(PeriodLockService periodLockService) {
        this.periodLockService = periodLockService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PeriodLockDto>>> getPeriodsForYear(@RequestParam int year) {
        Long shopId = TenantContext.getCurrentShopId();
        List<PeriodLockDto> locks = periodLockService.getPeriodsForYear(shopId, year);
        return ResponseEntity.ok(new ApiResponse<>("success", "Period locks retrieved", locks));
    }

    @PostMapping("/lock")
    public ResponseEntity<ApiResponse<PeriodLockDto>> lockPeriod(@RequestBody PeriodLockRequestDto req) {
        Long shopId = TenantContext.getCurrentShopId();
        PeriodLockDto dto = periodLockService.lockPeriod(shopId, req.getYear(), req.getMonth(),
                req.getFormType(), req.getReason());
        return ResponseEntity.ok(new ApiResponse<>("success", "Period locked successfully", dto));
    }

    @PostMapping("/unlock")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<PeriodLockDto>> unlockPeriod(@RequestBody PeriodLockRequestDto req) {
        Long shopId = TenantContext.getCurrentShopId();
        PeriodLockDto dto = periodLockService.unlockPeriod(shopId, req.getYear(), req.getMonth(),
                req.getFormType(), req.getReason());
        return ResponseEntity.ok(new ApiResponse<>("success", "Period unlocked successfully", dto));
    }
}
