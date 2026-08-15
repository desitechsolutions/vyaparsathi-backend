package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Periodic stocktake session. Lifecycle:
 * <pre>
 *   PLANNED → IN_PROGRESS → COMPLETED
 *                       └→ CANCELLED
 * </pre>
 * {@code scope} distinguishes FULL takes from targeted counts (single category,
 * a batch, or a single-shelf spot check). Each line has a system-of-record
 * quantity captured at plan time, the actual counted quantity, and a variance
 * that becomes an ADJUST movement when the count is committed.
 */
@Entity
@Table(name = "cycle_count")
@Getter
@Setter
@NoArgsConstructor
public class CycleCount extends ShopAwareEntity {

    @Column(name = "count_number", nullable = false, length = 50)
    private String countNumber;

    @Column(name = "scope", nullable = false, length = 30)
    private String scope = "FULL";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PLANNED";

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "cycleCount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CycleCountLine> lines = new ArrayList<>();
}
