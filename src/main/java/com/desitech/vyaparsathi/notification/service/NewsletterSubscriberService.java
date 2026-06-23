package com.desitech.vyaparsathi.notification.service;

import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.util.TemplateUtil;
import com.desitech.vyaparsathi.notification.dto.NewsletterStatsDto;
import com.desitech.vyaparsathi.notification.dto.NewsletterSubscribeRequest;
import com.desitech.vyaparsathi.notification.dto.NewsletterSubscriberDto;
import com.desitech.vyaparsathi.notification.entity.NewsletterSubscriber;
import com.desitech.vyaparsathi.notification.repository.NewsletterSubscriberRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NewsletterSubscriberService {

    private static final Logger logger = LoggerFactory.getLogger(NewsletterSubscriberService.class);

    private final NewsletterSubscriberRepository repository;
    private final EmailService emailService;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    @Transactional
    public String subscribe(NewsletterSubscribeRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String source = request.getSource() != null ? request.getSource().trim() : "Landing Page";

        Optional<NewsletterSubscriber> existing = repository.findByEmail(email);
        NewsletterSubscriber subscriber;
        boolean isReactivation = false;

        if (existing.isPresent()) {
            subscriber = existing.get();
            if (subscriber.isActive()) {
                logger.info("Email already active in newsletter list: {}", email);
                return "You are already subscribed to our newsletter!";
            } else {
                // Reactivate
                subscriber.setActive(true);
                subscriber.setSubscribedAt(LocalDateTime.now());
                subscriber.setUnsubscribedAt(null);
                subscriber.setSource(source);
                isReactivation = true;
                logger.info("Reactivating newsletter subscription for: {}", email);
            }
        } else {
            // New Subscription
            subscriber = new NewsletterSubscriber();
            subscriber.setEmail(email);
            subscriber.setActive(true);
            subscriber.setSource(source);
            subscriber.setUnsubscribeToken(UUID.randomUUID().toString());
            logger.info("Creating new newsletter subscription for: {}", email);
        }

        subscriber = repository.save(subscriber);

        // Send Welcome Email
        sendWelcomeEmail(subscriber);

        return isReactivation ? "Successfully resubscribed to our newsletter!" : "Successfully subscribed to our newsletter!";
    }

    @Transactional
    public String unsubscribe(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Unsubscribe token is required");
        }

        NewsletterSubscriber subscriber = repository.findByUnsubscribeToken(token)
                .orElseThrow(() -> new ApplicationException("Invalid or expired unsubscribe token"));

        if (subscriber.isActive()) {
            subscriber.setActive(false);
            subscriber.setUnsubscribedAt(LocalDateTime.now());
            repository.save(subscriber);
            logger.info("Subscriber unsubscribed successfully: {}", subscriber.getEmail());
        }

        return subscriber.getEmail();
    }

    public Page<NewsletterSubscriberDto> getSubscribers(String email, Boolean active, String source, Pageable pageable) {
        Page<NewsletterSubscriber> page = repository.findWithFilters(
                (email == null || email.trim().isEmpty()) ? null : email.trim(),
                active,
                (source == null || source.trim().isEmpty()) ? null : source.trim(),
                pageable
        );
        return page.map(this::toDto);
    }

    public NewsletterStatsDto getStats() {
        NewsletterStatsDto stats = new NewsletterStatsDto();

        long total = repository.count();
        long active = repository.countByActive(true);
        long unsubscribed = repository.countByActive(false);

        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long newThisMonth = repository.countActiveSubscribersNewerThan(startOfMonth);

        stats.setTotalSubscribers(total);
        stats.setActiveSubscribers(active);
        stats.setUnsubscribedSubscribers(unsubscribed);
        stats.setNewThisMonth(newThisMonth);

        // Source Breakdown
        Map<String, Long> sourceMap = new LinkedHashMap<>();
        for (Object[] row : repository.countSubscribersBySource()) {
            String src = row[0] != null ? row[0].toString() : "Unknown";
            Long count = (Long) row[1];
            sourceMap.put(src, count);
        }
        stats.setSubscribersBySource(sourceMap);

        // Monthly Growth
        Map<String, Long> monthMap = new LinkedHashMap<>();
        for (Object[] row : repository.countSubscribersByMonth()) {
            String month = row[0] != null ? row[0].toString() : "Unknown";
            Long count = (Long) row[1];
            monthMap.put(month, count);
        }
        stats.setSubscribersByMonth(monthMap);

        return stats;
    }

    public byte[] exportCsv(String email, Boolean active, String source) {
        List<NewsletterSubscriber> list;
        if ((email == null || email.trim().isEmpty()) && active == null && (source == null || source.trim().isEmpty())) {
            list = repository.findAll();
        } else {
            // Unpaginated search using high limit Pageable
            Page<NewsletterSubscriber> page = repository.findWithFilters(
                    (email == null || email.trim().isEmpty()) ? null : email.trim(),
                    active,
                    (source == null || source.trim().isEmpty()) ? null : source.trim(),
                    Pageable.unpaged()
            );
            list = page.getContent();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out)) {
            writer.println("ID,Email,Active,Subscribed At,Unsubscribed At,Source");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for (NewsletterSubscriber s : list) {
                String subDate = s.getSubscribedAt() != null ? s.getSubscribedAt().format(formatter) : "";
                String unsubDate = s.getUnsubscribedAt() != null ? s.getUnsubscribedAt().format(formatter) : "";
                writer.printf("%d,%s,%b,%s,%s,%s\n",
                        s.getId(),
                        s.getEmail(),
                        s.isActive(),
                        subDate,
                        unsubDate,
                        s.getSource() != null ? s.getSource() : ""
                );
            }
            writer.flush();
        }
        return out.toByteArray();
    }

    private void sendWelcomeEmail(NewsletterSubscriber subscriber) {
        try {
            Map<String, String> variables = new HashMap<>();
            variables.put("email", subscriber.getEmail());
            variables.put("unsubscribeLink", backendUrl + "/api/newsletter/unsubscribe?token=" + subscriber.getUnsubscribeToken());
            variables.put("currentYear", String.valueOf(LocalDateTime.now().getYear()));

            String htmlContent = TemplateUtil.loadTemplate("templates/newsletter-welcome.html", variables);
            emailService.sendEmail(subscriber.getEmail(), "Welcome to VyaparSathi", htmlContent);
        } catch (MessagingException e) {
            logger.error("Failed to send welcome email to: {}", subscriber.getEmail(), e);
            // We do not fail the subscription transactional save even if the welcome email fails to deliver.
        }
    }

    private NewsletterSubscriberDto toDto(NewsletterSubscriber entity) {
        NewsletterSubscriberDto dto = new NewsletterSubscriberDto();
        dto.setId(entity.getId());
        dto.setEmail(entity.getEmail());
        dto.setActive(entity.isActive());
        dto.setSubscribedAt(entity.getSubscribedAt());
        dto.setUnsubscribedAt(entity.getUnsubscribedAt());
        dto.setSource(entity.getSource());
        dto.setUnsubscribeToken(entity.getUnsubscribeToken());
        return dto;
    }

    // Future Readiness Methods as requested:
    public List<NewsletterSubscriber> findAllActiveSubscribers() {
        return repository.findAllByActiveTrue();
    }

    public long countActiveSubscribers() {
        return repository.countByActive(true);
    }
}
