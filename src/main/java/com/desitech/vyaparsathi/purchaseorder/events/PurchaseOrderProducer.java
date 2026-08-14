package com.desitech.vyaparsathi.purchaseorder.events;

import com.desitech.vyaparsathi.purchaseorder.enums.EventType;
import com.desitech.vyaparsathi.purchaseorder.events.dto.PurchaseOrderEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderProducer {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderProducer.class);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public void sendMessage(EventType type, PurchaseOrderEventDto eventDto) {
        PurchaseOrderEvent event = new PurchaseOrderEvent(this, type, eventDto);
        logger.debug("Publishing local PO event: type={} poId={}", type, eventDto != null ? eventDto.getId() : null);
        this.eventPublisher.publishEvent(event);
    }
}