package com.desitech.vyaparsathi.purchaseorder.events;

import com.desitech.vyaparsathi.purchaseorder.enums.EventType;
import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderEvent;
import com.desitech.vyaparsathi.purchaseorder.events.dto.PurchaseOrderEventDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderProducer {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public void sendMessage(EventType type, PurchaseOrderEventDto eventDto) {
        PurchaseOrderEvent event = new PurchaseOrderEvent(this, type, eventDto);
        System.out.println(String.format("#### -> Publishing Local Event -> %s", event));

        this.eventPublisher.publishEvent(event);
    }
}