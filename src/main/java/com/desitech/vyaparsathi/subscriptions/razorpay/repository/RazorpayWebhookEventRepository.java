package com.desitech.vyaparsathi.subscriptions.razorpay.repository;

import com.desitech.vyaparsathi.subscriptions.razorpay.entity.RazorpayWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RazorpayWebhookEventRepository extends JpaRepository<RazorpayWebhookEvent, Long> {

    /**
     * Primary idempotency lookup — called before processing any incoming webhook.
     * If an event with this ID is already {@code PROCESSED}, the service skips
     * all business logic and responds 200 immediately.
     */
    Optional<RazorpayWebhookEvent> findByEventId(String eventId);
}
