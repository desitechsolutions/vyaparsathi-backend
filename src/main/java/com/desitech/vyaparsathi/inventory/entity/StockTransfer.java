package com.desitech.vyaparsathi.inventory.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.inventory.enums.StockTransferStatus;
import com.desitech.vyaparsathi.shop.entity.Shop;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a request to move stock between two shop locations.
 *
 * NOTE: This entity extends BaseEntity (not ShopAwareEntity) because it spans
 * two shops (from_shop → to_shop) and therefore cannot be scoped to a single
 * shop via the standard ShopAwareEntity/shopFilter mechanism.
 */
@Entity
@Table(name = "stock_transfer")
public class StockTransfer extends BaseEntity {

    /** Human-readable reference number (e.g. "TRF-2025-0001"). */
    @Column(name = "transfer_number", nullable = false, unique = true, length = 50)
    private String transferNumber;

    /** The shop that is sending / losing the stock. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_shop_id", nullable = false)
    private Shop fromShop;

    /** The shop that is receiving / gaining the stock. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_shop_id", nullable = false)
    private Shop toShop;

    @Column(name = "transfer_date", nullable = false)
    private LocalDateTime transferDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StockTransferStatus status = StockTransferStatus.PENDING;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "stockTransfer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockTransferItem> items = new ArrayList<>();

    public StockTransfer() {}

    public String getTransferNumber() { return transferNumber; }
    public void setTransferNumber(String transferNumber) { this.transferNumber = transferNumber; }

    public Shop getFromShop() { return fromShop; }
    public void setFromShop(Shop fromShop) { this.fromShop = fromShop; }

    public Shop getToShop() { return toShop; }
    public void setToShop(Shop toShop) { this.toShop = toShop; }

    public LocalDateTime getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDateTime transferDate) { this.transferDate = transferDate; }

    public StockTransferStatus getStatus() { return status; }
    public void setStatus(StockTransferStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<StockTransferItem> getItems() { return items; }
    public void setItems(List<StockTransferItem> items) { this.items = items; }
}

