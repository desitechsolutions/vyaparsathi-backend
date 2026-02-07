package com.desitech.vyaparsathi.purchaseorder.kafka;

import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderProducer {

    private static final String TOPIC = "purchase-order-events";

    @Autowired
    private KafkaTemplate<String, PurchaseOrderEvent> kafkaTemplate;

    public void sendMessage(PurchaseOrderEvent event) {
        System.out.println(String.format("#### -> Producing message -> %s", event));
        this.kafkaTemplate.send(TOPIC, event);
    }
}