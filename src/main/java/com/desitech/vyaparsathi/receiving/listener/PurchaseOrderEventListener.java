package com.desitech.vyaparsathi.receiving.listener;

import com.desitech.vyaparsathi.purchaseorder.events.PurchaseOrderEvent;
import com.desitech.vyaparsathi.receiving.service.ReceivingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PurchaseOrderEventListener {
    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderEventListener.class);

    @Autowired
    private ReceivingService receivingService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePurchaseOrderEvent(PurchaseOrderEvent event) {
        logger.info("#### -> Locally Consumed Event -> {}", event);
        receivingService.processPurchaseOrderEvent(event);
    }
}