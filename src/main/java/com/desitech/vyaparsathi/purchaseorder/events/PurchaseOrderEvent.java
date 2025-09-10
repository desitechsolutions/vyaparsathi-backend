package com.desitech.vyaparsathi.purchaseorder.events;

import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrder;
import com.desitech.vyaparsathi.purchaseorder.enums.EventType;
import com.desitech.vyaparsathi.purchaseorder.events.dto.PurchaseOrderEventDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseOrderEvent {
    private EventType eventType; // CREATED, PLACED, APPROVED, UPDATED, DELETED
    private PurchaseOrderEventDto purchaseOrder;
}