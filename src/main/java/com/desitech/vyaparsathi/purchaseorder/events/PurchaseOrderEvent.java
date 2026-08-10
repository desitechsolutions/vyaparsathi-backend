package com.desitech.vyaparsathi.purchaseorder.events;

import com.desitech.vyaparsathi.purchaseorder.enums.EventType;
import com.desitech.vyaparsathi.purchaseorder.events.dto.PurchaseOrderEventDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PurchaseOrderEvent extends ApplicationEvent {
    private final EventType eventType;
    private final PurchaseOrderEventDto purchaseOrder;

    public PurchaseOrderEvent(Object source, EventType eventType, PurchaseOrderEventDto eventDto) {
        super(source);
        this.eventType = eventType;
        this.purchaseOrder = eventDto;
    }

    public EventType getEventType() { return eventType; }
    public PurchaseOrderEventDto getPurchaseOrder() { return purchaseOrder; }
}