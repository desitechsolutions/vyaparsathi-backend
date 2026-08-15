package com.desitech.vyaparsathi.receiving.service;

import com.desitech.vyaparsathi.receiving.entity.GrnSequence;
import com.desitech.vyaparsathi.receiving.repository.GrnSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the format contract of {@link GrnNumberService}. Mirrors the
 * approach used for PurchaseOrderNumberService and keeps the DB layer mocked
 * — the sequence table's own pessimistic-lock behavior is covered by the JPA
 * layer, not by this unit test.
 */
class GrnNumberServiceTest {

    @Mock
    private GrnSequenceRepository repo;

    @InjectMocks
    private GrnNumberService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void firstNumberInFiscalYear_isPaddedToFiveDigits() {
        // Empty sequence table → service creates a fresh row starting at 0,
        // then increments to 1 for the first GRN. Result: GRN/26-27/00001.
        when(repo.findByShopIdAndPrefixAndFiscalYearForUpdate(1L, "GRN", (short) 2026))
                .thenReturn(Optional.empty());
        when(repo.saveAndFlush(any(GrnSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        String number = service.nextGrnNumber(1L, LocalDate.of(2026, 5, 10));

        assertThat(number).isEqualTo("GRN/26-27/00001");
    }

    @Test
    void indianFiscalYear_janMarchCountsAsPriorFY() {
        // Jan/Feb/Mar belong to the *previous* fiscal year (Apr–Mar).
        // 2026-02-15 → FY26 label "25-26".
        when(repo.findByShopIdAndPrefixAndFiscalYearForUpdate(1L, "GRN", (short) 2025))
                .thenReturn(Optional.empty());
        when(repo.saveAndFlush(any(GrnSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        String number = service.nextGrnNumber(1L, LocalDate.of(2026, 2, 15));

        assertThat(number).isEqualTo("GRN/25-26/00001");
    }

    @Test
    void reusesExistingSequenceRowAndIncrements() {
        GrnSequence existing = new GrnSequence();
        existing.setShopId(1L);
        existing.setPrefix("GRN");
        existing.setFiscalYear((short) 2026);
        existing.setLastSeq(41L);

        when(repo.findByShopIdAndPrefixAndFiscalYearForUpdate(1L, "GRN", (short) 2026))
                .thenReturn(Optional.of(existing));
        when(repo.saveAndFlush(any(GrnSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        String number = service.nextGrnNumber(1L, LocalDate.of(2026, 5, 10));

        assertThat(number).isEqualTo("GRN/26-27/00042");
        ArgumentCaptor<GrnSequence> saved = ArgumentCaptor.forClass(GrnSequence.class);
        // Once when saving the incremented row (no "create new" save this time).
        verify(repo, times(1)).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getLastSeq()).isEqualTo(42L);
    }

    @Test
    void separatesFiscalYearsForSameShop() {
        // Independent sequences per fiscal year — a new FY resets the counter.
        GrnSequence fy26 = new GrnSequence();
        fy26.setShopId(1L); fy26.setPrefix("GRN"); fy26.setFiscalYear((short) 2026); fy26.setLastSeq(999L);
        when(repo.findByShopIdAndPrefixAndFiscalYearForUpdate(1L, "GRN", (short) 2026))
                .thenReturn(Optional.of(fy26));
        when(repo.findByShopIdAndPrefixAndFiscalYearForUpdate(1L, "GRN", (short) 2027))
                .thenReturn(Optional.empty());
        when(repo.saveAndFlush(any(GrnSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        String lastOfPrev = service.nextGrnNumber(1L, LocalDate.of(2027, 3, 31));   // still FY26
        String firstOfNew = service.nextGrnNumber(1L, LocalDate.of(2027, 4, 1));    // FY27 starts

        assertThat(lastOfPrev).isEqualTo("GRN/26-27/01000");
        assertThat(firstOfNew).isEqualTo("GRN/27-28/00001");
    }
}