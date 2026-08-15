package com.desitech.vyaparsathi.receiving.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Warehouse bin/shelf destination for a received line. Multiple assignments
 * per receiving item are allowed — supports split-putaway when a single line
 * lands in more than one bin.
 */
@Entity
@Table(name = "receiving_bin_assignment")
@Getter
@Setter
@NoArgsConstructor
public class ReceivingBinAssignment extends ShopAwareEntity {

    @Column(name = "receiving_item_id", nullable = false)
    private Long receivingItemId;

    @Column(name = "bin_code", nullable = false, length = 50)
    private String binCode;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;

    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();
}
