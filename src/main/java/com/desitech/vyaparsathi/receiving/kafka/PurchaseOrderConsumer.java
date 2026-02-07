package com.desitech.vyaparsathi.receiving.kafka;

import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderEvent;
import com.desitech.vyaparsathi.receiving.service.ReceivingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderConsumer {

    @Autowired
    private ReceivingService receivingService;

    @KafkaListener(topics = "purchase-order-events", groupId = "receiving-group")
    public void consume(PurchaseOrderEvent event) {
        System.out.println(String.format("#### -> Consumed message -> %s", event));
        receivingService.processPurchaseOrderEvent(event);
    }
}