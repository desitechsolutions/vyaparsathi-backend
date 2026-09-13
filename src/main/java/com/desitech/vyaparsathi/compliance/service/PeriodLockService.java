package com.desitech.vyaparsathi.compliance.service;

import com.desitech.vyaparsathi.compliance.dto.PeriodLockDto;
import com.desitech.vyaparsathi.compliance.entity.CompliancePeriodLock;
import com.desitech.vyaparsathi.compliance.repository.CompliancePeriodLockRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PeriodLockService {

    private static final String LOCKED   = "LOCKED";
    private static final String UNLOCKED = "UNLOCKED";
    private static final String OPEN     = "OPEN";
    private static final String FORM_ALL = "ALL";

    private final CompliancePeriodLockRepository lockRepo;

    public PeriodLockService(CompliancePeriodLockRepository lockRepo) {
        this.lockRepo = lockRepo;
    }

    /**
     * Returns a 12-element list (Jan → Dec) for the given year.
     * Months with no lock record appear as status=OPEN with null audit fields.
     */
    @Transactional(readOnly = true)
    public List<PeriodLockDto> getPeriodsForYear(Long shopId, int year) {
        List<CompliancePeriodLock> locks = lockRepo.findByShopIdAndPeriodYearOrderByPeriodMonth(shopId, year);
        Map<Integer, CompliancePeriodLock> byMonth = locks.stream()
                .collect(Collectors.toMap(CompliancePeriodLock::getPeriodMonth, Function.identity(), (a, b) -> a));
        List<PeriodLockDto> result = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            CompliancePeriodLock lock = byMonth.get(m);
            result.add(lock != null ? toDto(lock) : PeriodLockDto.open(year, m));
        }
        return result;
    }

    /**
     * Returns true if the period (year + month of {@code date}) is locked for
     * this shop under formType "ALL" — the broadest lock scope.
     */
    @Transactional(readOnly = true)
    public boolean isPeriodLocked(Long shopId, LocalDate date) {
        if (shopId == null || date == null) return false;
        List<CompliancePeriodLock> locked = lockRepo.findByShopIdAndPeriodYearAndPeriodMonthAndStatus(
                shopId, date.getYear(), date.getMonthValue(), LOCKED);
        return !locked.isEmpty();
    }

    /**
     * Creates or updates a period-lock record to LOCKED status.
     */
    @Transactional
    public PeriodLockDto lockPeriod(Long shopId, int year, int month, String formType, String reason) {
        String ft = formType != null ? formType : FORM_ALL;
        CompliancePeriodLock lock = lockRepo
                .findByShopIdAndPeriodYearAndPeriodMonthAndFormType(shopId, year, month, ft)
                .orElseGet(() -> {
                    CompliancePeriodLock l = new CompliancePeriodLock();
                    l.setShopId(shopId);
                    l.setPeriodYear(year);
                    l.setPeriodMonth(month);
                    l.setFormType(ft);
                    return l;
                });
        lock.setStatus(LOCKED);
        lock.setReason(reason);
        lock.setLockedAt(Instant.now());
        lock.setLockedBy(currentUsername());
        return toDto(lockRepo.save(lock));
    }

    /**
     * Updates an existing lock record to UNLOCKED status. Throws if no record found.
     */
    @Transactional
    public PeriodLockDto unlockPeriod(Long shopId, int year, int month, String formType, String reason) {
        String ft = formType != null ? formType : FORM_ALL;
        CompliancePeriodLock lock = lockRepo
                .findByShopIdAndPeriodYearAndPeriodMonthAndFormType(shopId, year, month, ft)
                .orElseThrow(() -> new IllegalStateException(
                        "No lock record found for " + String.format("%02d-%d", month, year)));
        lock.setStatus(UNLOCKED);
        lock.setReason(reason);
        return toDto(lockRepo.save(lock));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PeriodLockDto toDto(CompliancePeriodLock lock) {
        PeriodLockDto dto = new PeriodLockDto();
        dto.setYear(lock.getPeriodYear());
        dto.setMonth(lock.getPeriodMonth());
        dto.setFormType(lock.getFormType());
        dto.setStatus(lock.getStatus());
        dto.setLockedAt(lock.getLockedAt());
        dto.setLockedBy(lock.getLockedBy());
        dto.setReason(lock.getReason());
        return dto;
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
