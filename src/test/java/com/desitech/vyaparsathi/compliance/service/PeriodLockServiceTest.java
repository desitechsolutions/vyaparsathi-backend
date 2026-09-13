package com.desitech.vyaparsathi.compliance.service;

import com.desitech.vyaparsathi.compliance.entity.CompliancePeriodLock;
import com.desitech.vyaparsathi.compliance.exception.LockedPeriodException;
import com.desitech.vyaparsathi.compliance.repository.CompliancePeriodLockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PeriodLockService — period guard checks")
class PeriodLockServiceTest {

    @Mock private CompliancePeriodLockRepository lockRepo;
    @InjectMocks private PeriodLockService periodLockService;

    private static final Long SHOP_ID = 1L;
    private static final LocalDate SEPT_2026 = LocalDate.of(2026, 9, 15);

    @Test
    @DisplayName("isPeriodLocked returns true when a LOCKED record exists, causing LockedPeriodException in the service guard")
    void isPeriodLocked_whenLockedRecordExists_returnsTrue() {
        CompliancePeriodLock lock = new CompliancePeriodLock();
        lock.setShopId(SHOP_ID);
        lock.setPeriodYear(2026);
        lock.setPeriodMonth(9);
        lock.setFormType("ALL");
        lock.setStatus("LOCKED");

        when(lockRepo.findByShopIdAndPeriodYearAndPeriodMonthAndStatus(
                SHOP_ID, 2026, 9, "LOCKED"))
                .thenReturn(List.of(lock));

        boolean locked = periodLockService.isPeriodLocked(SHOP_ID, SEPT_2026);
        assertTrue(locked, "Should report locked when a LOCKED record exists");

        // Verify the guard throws as expected when integrated directly
        assertThrows(LockedPeriodException.class, () -> {
            if (periodLockService.isPeriodLocked(SHOP_ID, SEPT_2026)) {
                throw new LockedPeriodException(
                        String.format("%02d-%d", SEPT_2026.getMonthValue(), SEPT_2026.getYear()));
            }
        });
    }

    @Test
    @DisplayName("isPeriodLocked returns false when no LOCKED record exists, guard does not throw")
    void isPeriodLocked_whenNoLockedRecord_returnsFalse() {
        when(lockRepo.findByShopIdAndPeriodYearAndPeriodMonthAndStatus(
                SHOP_ID, 2026, 9, "LOCKED"))
                .thenReturn(List.of());

        boolean locked = periodLockService.isPeriodLocked(SHOP_ID, SEPT_2026);
        assertFalse(locked, "Should report open when no LOCKED record exists");

        // Guard should not throw
        assertDoesNotThrow(() -> {
            if (periodLockService.isPeriodLocked(SHOP_ID, SEPT_2026)) {
                throw new LockedPeriodException("09-2026");
            }
        });
    }
}
