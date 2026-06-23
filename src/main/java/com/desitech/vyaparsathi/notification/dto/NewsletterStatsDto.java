package com.desitech.vyaparsathi.notification.dto;

import lombok.Data;
import java.util.Map;

@Data
public class NewsletterStatsDto {
    private long totalSubscribers;
    private long activeSubscribers;
    private long unsubscribedSubscribers;
    private long newThisMonth;
    private Map<String, Long> subscribersBySource;
    private Map<String, Long> subscribersByMonth;
}
